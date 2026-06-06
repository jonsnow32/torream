package cloud.streamless.torream.download.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * TorrentDownloadEngine - Core torrent download logic based on LibreTorrent
 *
 * Key features:
 * - Magnet link support with metadata fetching
 * - Torrent file support
 * - Sequential download for streaming
 * - Progress monitoring
 * - Proper error handling
 */
class TorrentDownloadEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val sessionManager: TorrentSessionManager
) {

  /**
   * Download info for monitoring
   */
  data class DownloadInfo(
    val infoHash: String,
    val name: String,
    val totalSize: Long,
    val downloadedBytes: Long,
    val progress: Int,
    val downloadRate: Long,
    val uploadRate: Long,
    val numSeeds: Int,
    val numPeers: Int,
    val state: TorrentStatus.State,
    val isFinished: Boolean,
    val isSeeding: Boolean,
    val error: String? = null,
    val largestVideoFileIndex: Int = 0  // Index of the largest video file in the torrent
  )

  /**
   * Start downloading a magnet link
   */
  fun downloadMagnet(
    magnetUri: String,
    saveDir: File,
    sequential: Boolean = false
  ): Flow<DownloadInfo> = flow {
    Timber.i("📥 Starting magnet download: $magnetUri")

    // Ensure session is started
    sessionManager.start()
    val session = sessionManager.getSession()
      ?: throw Exception("Failed to start torrent session")

    // Fetch magnet metadata
    Timber.i("🧲 Fetching magnet metadata...")
    val tempDir = File(context.cacheDir, "magnet_tmp_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    val metadata = try {
      session.fetchMagnet(magnetUri, 60, tempDir)
    } catch (e: Exception) {
      tempDir.deleteRecursively()
      throw Exception("Failed to fetch magnet metadata: ${e.message}")
    } finally {
      try {
        tempDir.deleteRecursively()
      } catch (_: Exception) {}
    }

    if (metadata == null || metadata.isEmpty()) {
      throw Exception("Failed to fetch magnet metadata - no data received")
    }

    Timber.i("✅ Metadata fetched: ${metadata.size} bytes")

    // Fix: For large metadata, save to file first to avoid StackOverflowError
    val torrentInfo = try {
      if (metadata.size > 500_000) { // 500KB threshold
        Timber.d("Large metadata detected (${metadata.size} bytes), using file-based approach")
        val metadataFile = File(context.cacheDir, "metadata_${System.currentTimeMillis()}.torrent")
        try {
          metadataFile.writeBytes(metadata)
          TorrentInfo(metadataFile)
        } finally {
          try {
            metadataFile.delete()
          } catch (_: Exception) {}
        }
      } else {
        TorrentInfo.bdecode(metadata)
      }
    } catch (e: StackOverflowError) {
      Timber.e(e, "StackOverflowError while decoding metadata, trying file-based approach")
      val metadataFile = File(context.cacheDir, "metadata_${System.currentTimeMillis()}.torrent")
      try {
        metadataFile.writeBytes(metadata)
        TorrentInfo(metadataFile)
      } finally {
        try {
          metadataFile.delete()
        } catch (_: Exception) {}
      }
    }

    Timber.i("📦 Torrent: ${torrentInfo.name()}, size: ${torrentInfo.totalSize()}")

    // Start download
    val handle = startDownload(session, torrentInfo, saveDir, sequential)
      ?: throw Exception("Failed to start torrent download")

    // Add public trackers
    addPublicTrackers(handle)


    // Force DHT announce
    handle.forceReannounce()

    // Monitor download
    monitorDownload(handle, torrentInfo).collect { info ->
      emit(info)
    }
  }

  /**
   * Start downloading a torrent file
   */
  fun downloadTorrentFile(
    torrentFilePath: String,
    saveDir: File,
    sequential: Boolean = false
  ): Flow<DownloadInfo> = flow {
    Timber.i("📥 Starting torrent file download: $torrentFilePath")

    // Ensure session is started
    sessionManager.start()
    val session = sessionManager.getSession()
      ?: throw Exception("Failed to start torrent session")

    // Load torrent file
    val torrentFile = File(torrentFilePath)
    if (!torrentFile.exists()) {
      throw Exception("Torrent file not found: $torrentFilePath")
    }

    val torrentInfo = TorrentInfo(torrentFile)
    Timber.i("📦 Torrent info: name=${torrentInfo.name()}, size=${torrentInfo.totalSize()}")

    // Start download
    val handle = startDownload(session, torrentInfo, saveDir, sequential)
      ?: throw Exception("Failed to start torrent download")

    // Add public trackers
    addPublicTrackers(handle)

    // Force announce
    handle.forceReannounce()

    // Monitor download
    monitorDownload(handle, torrentInfo).collect { info ->
      emit(info)
    }
  }

  /**
   * Pause a torrent
   */
  @Suppress("unused")
  suspend fun pauseTorrent(infoHash: String) = withContext(Dispatchers.IO) {
    try {
      val hash = org.libtorrent4j.Sha1Hash.parseHex(infoHash)
      val handle = sessionManager.findTorrent(hash)
      if (handle != null && handle.isValid) {
        handle.pause()
        Timber.i("⏸️ Torrent paused: $infoHash")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to pause torrent: $infoHash")
      throw e
    }
  }

  /**
   * Resume a torrent
   */
  @Suppress("unused")
  suspend fun resumeTorrent(infoHash: String) = withContext(Dispatchers.IO) {
    try {
      val hash = org.libtorrent4j.Sha1Hash.parseHex(infoHash)
      val handle = sessionManager.findTorrent(hash)
      if (handle != null && handle.isValid) {
        handle.resume()
        Timber.i("▶️ Torrent resumed: $infoHash")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to resume torrent: $infoHash")
      throw e
    }
  }

  /**
   * Remove/delete a torrent
   */
  @Suppress("unused")
  suspend fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) = withContext(Dispatchers.IO) {
    try {
      val session = sessionManager.getSession() ?: return@withContext
      val hash = org.libtorrent4j.Sha1Hash.parseHex(infoHash)
      val handle = sessionManager.findTorrent(hash)

      if (handle != null && handle.isValid) {
        if (deleteFiles) {
          session.remove(handle, org.libtorrent4j.SessionHandle.DELETE_FILES)
        } else {
          session.remove(handle)
        }
        Timber.i("🗑️ Torrent removed: $infoHash, deleteFiles=$deleteFiles")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to remove torrent: $infoHash")
      throw e
    }
  }

  /**
   * Fetch magnet metadata with timeout
   */
  private suspend fun fetchMagnetMetadata(
    session: org.libtorrent4j.SessionManager,
    magnetUri: String,
    timeoutSeconds: Int = 60
  ): TorrentInfo? = withContext(Dispatchers.IO) {
    Timber.d("🧲 Fetching magnet metadata (timeout: ${timeoutSeconds}s)...")

    // Create temp directory for magnet fetch
    val tempDir = File(context.cacheDir, "magnet_tmp_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    try {
      // Wait for DHT to be ready
      var dhtReady = false
      repeat(30) { // 30 seconds max for DHT
        try {
          val stats = session.stats()
          val dhtNodes = stats.dhtNodes()
          if (dhtNodes >= 10) {
            Timber.d("✅ DHT ready: $dhtNodes nodes")
            dhtReady = true
            return@repeat
          }
        } catch (_: Exception) {
          // Ignore DHT stats errors
        }
        delay(1000)
      }

      if (!dhtReady) {
        Timber.w("DHT not ready, but continuing with magnet fetch...")
      }

      // Fetch metadata
      val metadata = session.fetchMagnet(magnetUri, timeoutSeconds, tempDir)

      if (metadata != null && metadata.isNotEmpty()) {
        Timber.i("✅ Metadata fetched: ${metadata.size} bytes")

        // Fix: For large metadata, save to file first to avoid StackOverflowError
        val torrentInfo = try {
          if (metadata.size > 500_000) { // 500KB threshold
            Timber.d("Large metadata detected (${metadata.size} bytes), using file-based approach")
            val metadataFile = File(context.cacheDir, "metadata_${System.currentTimeMillis()}.torrent")
            try {
              metadataFile.writeBytes(metadata)
              TorrentInfo(metadataFile)
            } finally {
              try {
                metadataFile.delete()
              } catch (_: Exception) {}
            }
          } else {
            TorrentInfo.bdecode(metadata)
          }
        } catch (e: StackOverflowError) {
          Timber.e(e, "StackOverflowError while decoding metadata, trying file-based approach")
          val metadataFile = File(context.cacheDir, "metadata_${System.currentTimeMillis()}.torrent")
          try {
            metadataFile.writeBytes(metadata)
            TorrentInfo(metadataFile)
          } finally {
            try {
              metadataFile.delete()
            } catch (_: Exception) {}
          }
        }

        return@withContext torrentInfo
      } else {
        Timber.e("❌ Metadata fetch returned null or empty")
        return@withContext null
      }
    } catch (_: Exception) {
      Timber.e("❌ Failed to fetch magnet metadata")
      return@withContext null
    } finally {
      // Cleanup temp directory
      try {
        tempDir.deleteRecursively()
      } catch (e: Exception) {
        Timber.w(e, "Failed to cleanup temp directory")
      }
    }
  }

  /**
   * Start torrent download
   */
  private suspend fun startDownload(
    session: org.libtorrent4j.SessionManager,
    torrentInfo: TorrentInfo,
    saveDir: File,
    sequential: Boolean
  ): TorrentHandle? = withContext(Dispatchers.IO) {
    try {
      // Ensure save directory exists
      if (!saveDir.exists()) {
        saveDir.mkdirs()
      }

      // Download torrent
      session.download(torrentInfo, saveDir)

      // Wait for handle to be added
      delay(500)

      // Find handle
      val infoHash = torrentInfo.infoHash()
      val handle = session.find(infoHash)

      if (handle == null || !handle.isValid) {
        Timber.e("Failed to find torrent handle after download")
        return@withContext null
      }

      // Configure handle for sequential download
      if (sequential) {
        // Enable sequential download flag
        handle.setFlags(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD)
        Timber.d("✅ Sequential download enabled")

        // Set file priorities (prioritize first and last files for streaming)
        val fileStorage = torrentInfo.files()
        val numFiles = fileStorage.numFiles()
        val priorities = Array(numFiles) { i ->
          when (i) {
            0 -> Priority.TOP_PRIORITY // First file
            numFiles - 1 -> Priority.TOP_PRIORITY // Last file
            else -> Priority.DEFAULT
          }
        }

        handle.prioritizeFiles(priorities)
        Timber.d("✅ File priorities set for streaming")
      }

      // Resume torrent
      handle.resume()

      Timber.i("✅ Torrent download started: ${torrentInfo.name()}")
      return@withContext handle
    } catch (e: Exception) {
      Timber.e(e, "Failed to start download")
      return@withContext null
    }
  }

  /**
   * Add public trackers to torrent
   */
  private fun addPublicTrackers(handle: TorrentHandle) {
    var added = 0
    TorrentSessionManager.PUBLIC_TRACKERS.forEach { url ->
      try {
        handle.addTracker(AnnounceEntry(url))
        added++
      } catch (_: Exception) {
        Timber.w("Failed to add tracker: $url")
      }
    }
    Timber.d("📡 Added $added public trackers")
  }

  /**
   * Find the index of the largest video file in the torrent
   */
  private fun findLargestVideoFileIndex(torrentInfo: TorrentInfo): Int {
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
                                "mpg", "mpeg", "3gp", "ts", "m2ts", "vob", "ogv", "rmvb")

    val fileStorage = torrentInfo.files()
    val numFiles = fileStorage.numFiles()

    var largestVideoIndex = 0
    var largestVideoSize = 0L

    for (i in 0 until numFiles) {
      val filePath = fileStorage.filePath(i)
      val fileSize = fileStorage.fileSize(i)
      val extension = filePath.substringAfterLast('.', "").lowercase()

      if (extension in videoExtensions && fileSize > largestVideoSize) {
        largestVideoSize = fileSize
        largestVideoIndex = i
      }
    }

    Timber.d("Largest video file: index=$largestVideoIndex, size=${largestVideoSize / (1024 * 1024)}MB")
    return largestVideoIndex
  }

  /**
   * Monitor torrent download progress
   */
  private fun monitorDownload(
    handle: TorrentHandle,
    torrentInfo: TorrentInfo,
    pollIntervalMs: Long = 500L
  ): Flow<DownloadInfo> = flow {
    val infoHash = torrentInfo.infoHash().toString()
    val totalSize = torrentInfo.totalSize()
    val largestVideoFileIndex = findLargestVideoFileIndex(torrentInfo)

    while (true) {
      if (!handle.isValid) {
        emit(DownloadInfo(
          infoHash = infoHash,
          name = torrentInfo.name(),
          totalSize = totalSize,
          downloadedBytes = 0,
          progress = 0,
          downloadRate = 0,
          uploadRate = 0,
          numSeeds = 0,
          numPeers = 0,
          state = TorrentStatus.State.UNKNOWN,
          isFinished = false,
          isSeeding = false,
          error = "Handle became invalid",
          largestVideoFileIndex = largestVideoFileIndex
        ))
        break
      }

      val status = handle.status()
      val state = status.state()

      val downloadedBytes = status.totalDone()
      val progress = if (totalSize > 0) {
        ((downloadedBytes.toFloat() / totalSize) * 100).toInt().coerceIn(0, 100)
      } else {
        (status.progress() * 100).toInt().coerceIn(0, 100)
      }

      // Check for errors
      val errorMsg = if (state == TorrentStatus.State.CHECKING_RESUME_DATA) {
        null
      } else if (downloadedBytes == 0L && progress == 0 && status.numSeeds() == 0 && status.numPeers() == 0) {
        // Likely stuck or error
        null  // Don't report error, just slow
      } else {
        null
      }

      val info = DownloadInfo(
        infoHash = infoHash,
        name = torrentInfo.name(),
        totalSize = totalSize,
        downloadedBytes = downloadedBytes,
        progress = progress,
        downloadRate = status.downloadRate().toLong(),
        uploadRate = status.uploadRate().toLong(),
        numSeeds = status.numSeeds(),
        numPeers = status.numPeers(),
        state = state,
        isFinished = state == TorrentStatus.State.FINISHED || state == TorrentStatus.State.SEEDING,
        isSeeding = state == TorrentStatus.State.SEEDING,
        error = errorMsg,
        largestVideoFileIndex = largestVideoFileIndex
      )

      emit(info)

      // Break if finished or has error
      if (info.isFinished || info.error != null) {
        break
      }

      delay(pollIntervalMs)
    }
  }
}
