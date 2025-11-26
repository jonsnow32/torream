package cloud.app.csplayer.download.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

// Wrapper for TorrentInfo result to avoid generic Result type issues
private data class TorrentMetadataResult(
  val isSuccess: Boolean = false,
  val torrentInfo: TorrentInfo? = null,
  val errorResult: androidx.work.ListenableWorker.Result? = null
)

@HiltWorker
class TorrentDownloadWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val repo: DownloadRepository,
  private val mediaStore: cloud.app.csplayer.media.dataSource.MediaStoreDataSource,
  private val coordinator: DownloadCoordinator
) : CoroutineWorker(context, params) {

  companion object {
    const val KEY_TASK_ID = "task_id"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR = "error"

    private const val NOTIFICATION_ID = 1002
    private const val POLL_INTERVAL_MS = 1000L
  }

  private var session: SessionManager? = null
  private var handle: TorrentHandle? = null

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID)
    if (taskId == null) {
      Timber.e("TorrentDownloadWorker: No taskId provided in input data")
      return@withContext Result.failure(workDataOf(KEY_ERROR to "No taskId provided"))
    }

    Timber.d("TorrentDownloadWorker started for taskId=$taskId")

    setForeground(createForegroundInfo(taskId, 0))

    // Retry logic to wait for task to be available in database
    // This handles timing issues where worker starts before database write is committed
    var state: cloud.app.csplayer.download.DownloadState? = null
    var retries = 0
    while (state == null && retries < 20) {
      state = repo.observeState(taskId).first()
      if (state == null) {
        Timber.w("Task not yet in database, retry ${retries + 1}/20 for taskId=$taskId")
        delay(100L)
        retries++
      }
    }

    if (state == null) {
      val error = "Task not found in database after ${retries} retries: $taskId"
      Timber.e("TorrentDownloadWorker: $error")
      return@withContext Result.failure(workDataOf(KEY_ERROR to error))
    }

    val task = state.task
    Timber.d("TorrentDownloadWorker: ✓ Task loaded from database - id=${task.id}, type=${task.type}")
    Timber.d("  source=${task.source}")
    Timber.d("  targetPath=${task.targetPath}")

    try {
      // Initialize libtorrent session
      session = SessionManager().apply {
        start()
        try {
          // Start DHT for peer discovery
          // libtorrent4j internally connects to bootstrap DHT nodes
          startDht()
          Timber.i("SessionManager started and DHT initialized")
        } catch (e: Throwable) {
          Timber.w("Failed to start DHT (non-fatal): ${e.message}")
        }
      }

      val s = session ?: return@withContext Result.failure(
        workDataOf(KEY_ERROR to "Failed to initialize session")
      )

      // Prepare save directory
      // targetPath should be an absolute file path, but handle content URIs for backward compatibility
      val baseDirPath = when {
        task.targetPath.startsWith("content://") -> {
          // Convert content URI to real filesystem path using KUniFile (for old downloads)
          Timber.w("Converting content URI to file path (backward compatibility): ${task.targetPath}")
          val uniFile = cloud.app.csplayer.utils.KUniFile.fromUri(context, task.targetPath.toUri())
          if (uniFile != null && uniFile.exists()) {
            uniFile.filePath ?: task.targetPath
          } else {
            Timber.e("Cannot convert content URI to path: ${task.targetPath}")
            val err = "Invalid download path"
            repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
            return@withContext Result.failure(workDataOf(KEY_ERROR to err))
          }
        }

        else -> task.targetPath
      }

      // Use KUniFile for directory operations
      val baseDirFile = File(baseDirPath)
      val baseDir = if (baseDirFile.isDirectory) baseDirFile else (baseDirFile.parentFile ?: baseDirFile)

      // Create KUniFile for directory checks and creation
      val baseDirKuni = cloud.app.csplayer.utils.KUniFile.fromFile(context, baseDir)
      if (baseDirKuni != null && !baseDirKuni.exists()) {
        baseDirKuni.createDirectory(baseDir.name)
        Timber.d("Created torrent save directory via KUniFile: ${baseDir.absolutePath}")
      } else if (!baseDir.exists()) {
        // Fallback to File if KUniFile fails
        baseDir.mkdirs()
        Timber.d("Created torrent save directory via File: ${baseDir.absolutePath}")
      }

      // Use baseDir directly as saveDir (libtorrent requires File object)
      val saveDir = baseDir

      // Add torrent to session
      val ti = when {
        task.source.startsWith("magnet:", ignoreCase = true) -> {
          val result = fetchMagnetMetadata(s, taskId, task.source, saveDir, state)
          if (!result.isSuccess) {
            return@withContext result.errorResult ?: Result.failure(workDataOf(KEY_ERROR to "Unknown error"))
          }
          result.torrentInfo ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Failed to decode metadata"))
        }

        task.source.endsWith(".torrent", ignoreCase = true) -> {
          // Handle both file paths and content:// URIs
          val torrentFile = if (task.source.startsWith("content://")) {
            // Content URI - need to copy to app storage first
            try {
              val sourceUri = task.source.toUri()
              val tempTorrentFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.torrent")

              // Copy content to temp file
              context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempTorrentFile.outputStream().use { output ->
                  input.copyTo(output)
                }
              }

              if (!tempTorrentFile.exists() || tempTorrentFile.length() == 0L) {
                val err = "Failed to copy torrent file from URI: ${task.source}"
                Timber.e(err)
                repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
                return@withContext Result.failure(workDataOf(KEY_ERROR to err))
              }

              Timber.d("Copied torrent file to: ${tempTorrentFile.absolutePath}")
              tempTorrentFile
            } catch (e: Exception) {
              val err = "Failed to read torrent file from URI: ${task.source} - ${e.message}"
              Timber.e(e, err)
              repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
              return@withContext Result.failure(workDataOf(KEY_ERROR to err))
            }
          } else {
            // Regular file path
            val file = File(task.source)
            val torrentKuniFile = cloud.app.csplayer.utils.KUniFile.fromFile(context, file)
            if (torrentKuniFile == null || !torrentKuniFile.exists()) {
              val err = "Torrent file not found: ${task.source}"
              Timber.e(err)
              repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
              return@withContext Result.failure(workDataOf(KEY_ERROR to err))
            }
            file
          }

          // TorrentInfo requires File object (libtorrent API)
          TorrentInfo(torrentFile)
        }

        else -> {
          val err = "Bare info-hash not directly supported, use magnet link"
          Timber.e(err)
          repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
          return@withContext Result.failure(workDataOf(KEY_ERROR to err))
        }
      }

      // Add torrent and start download
      s.download(ti, saveDir)

      // Find handle by info hash - retry a few times as it may not be immediately available
      val infoHash = ti.infoHash()
      var h: TorrentHandle? = null
      val maxFindAttempts = 10
      val findDelayMs = 300L

      for (attempt in 1..maxFindAttempts) {
        h = s.find(infoHash)
        if (h != null) {
          Timber.d("Found torrent handle for task=$taskId after $attempt attempts")
          break
        }
        if (!isActive) {
          return@withContext Result.retry()
        }
        delay(findDelayMs)
      }

      if (h == null) {
        val err = "Failed to find torrent handle after download"
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

      handle = h

      // Get fresh state before download loop
      var currentState = repo.observeState(taskId).first() ?: state

      // Update task with actual torrent name and targetPath
      // This is important for UI display and play functionality
      val torrentName = ti.name() // Get actual torrent name from TorrentInfo
      val updatedTask = currentState.task.copy(
        targetPath = saveDir.absolutePath,
        fileName = torrentName // Use fileName for display name
      )
      currentState = currentState.copy(task = updatedTask)

      repo.updateState(currentState.copy(status = DownloadStatus.DOWNLOADING))

      // Poll torrent status until complete or cancelled
      val result = pollDownloadProgress(h, ti, taskId, torrentName, saveDir, currentState)

      // Return result directly - don't override status after successful completion
      return@withContext result

    } catch (e: Exception) {
      // Check if this is a cancellation (expected when user pauses)
      if (e is kotlinx.coroutines.CancellationException || !isActive) {
        Timber.d("Download cancelled for taskId=$taskId")
        // Keep the status as PAUSED, don't mark as FAILED
        try {
          val currentState = repo.observeState(taskId).first() ?: state
          if (currentState.status != DownloadStatus.PAUSED) {
            repo.updateState(currentState.copy(status = DownloadStatus.PAUSED, error = null))
          }
        } catch (repoError: Exception) {
          Timber.e(repoError, "Failed to update state for cancelled download")
        }
        return@withContext Result.retry()
      }

      // Real error - not a cancellation
      val err = e.message ?: "Unknown error"
      Timber.e(e, "Torrent download failed for taskId=$taskId - Error: $err")
      try {
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
      } catch (repoError: Exception) {
        Timber.e(repoError, "Failed to update state for failed download")
      }

      // Process any queued downloads even on failure
      try {
        coordinator.processQueuedDownloads()
      } catch (procErr: Exception) {
        Timber.w(procErr, "Failed to process queued downloads after failure")
      }

      return@withContext Result.failure(workDataOf(KEY_ERROR to err))
    } finally {
      // Clean up
      handle?.let { h ->
        try {
          session?.remove(h)
        } catch (e: Throwable) {
          Timber.w("Error removing torrent handle: ${e.message}")
        }
      }
      session?.stop()
      session = null
      handle = null
    }
  }

  private fun createForegroundInfo(
    taskId: String,
    progress: Int,
    fileName: String? = null
  ): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context = context,
      taskId = taskId,
      progress = progress,
      isHttp = false,
      fileName
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      ForegroundInfo(NOTIFICATION_ID, notification)
    }
  }

  private suspend fun fetchMagnetMetadata(
    session: SessionManager,
    taskId: String,
    magnetUri: String,
    saveDir: File,
    state: cloud.app.csplayer.download.DownloadState
  ): TorrentMetadataResult = withContext(Dispatchers.IO) {
    Timber.d("Starting magnet metadata fetch for taskId=$taskId")
    repo.updateState(state.copy(status = DownloadStatus.QUEUED, error = null))

    // Create temp directory for magnet metadata
    val tmpFile = File(saveDir, "magnet_tmp_${taskId}")
    val tmpKuni = cloud.app.csplayer.utils.KUniFile.fromFile(context, tmpFile)

    // Clean up existing temp directory if it exists
    if (tmpKuni != null && tmpKuni.exists()) {
      // Use KUniFile to delete recursively
      tmpKuni.delete()
      Timber.d("Deleted existing temp directory via KUniFile")
    } else if (tmpFile.exists()) {
      // Fallback to File if KUniFile fails
      tmpFile.deleteRecursively()
      Timber.d("Deleted existing temp directory via File")
    }

    // Create new temp directory
    if (tmpKuni != null) {
      tmpKuni.createDirectory(tmpFile.name) ?: tmpFile.mkdirs()
    } else {
      tmpFile.mkdirs()
    }

    val tmp = tmpFile // Keep File reference for libtorrent API
    Timber.d("Temp directory created: ${tmp.absolutePath}")

    var meta: ByteArray? = null
    val maxAttempts = 6
    val perAttemptTimeoutSeconds = 10

    repo.updateState(
      state.copy(
        status = DownloadStatus.QUEUED,
        error = "Connecting to DHT network and finding peers..."
      )
    )

    // Optimized DHT bootstrap
    var dhtNodes = 0L
    var bootstrapWaitTime = 0L
    val maxBootstrapWait = 10000L

    while (dhtNodes == 0L && bootstrapWaitTime < maxBootstrapWait && isActive) {
      delay(1000L)
      bootstrapWaitTime += 1000L

      try {
        dhtNodes = session.stats().dhtNodes()
        if (dhtNodes > 0L) {
          Timber.i("DHT connected: $dhtNodes nodes (waited ${bootstrapWaitTime}ms)")
          break
        }
      } catch (e: Exception) {
        Timber.w("DHT stats error: ${e.message}")
      }
    }

    if (!isActive) {
      Timber.d("Worker cancelled during DHT bootstrap")
      return@withContext TorrentMetadataResult(
        errorResult = Result.retry()
      )
    }

    // Final DHT check
    try {
      val stats = session.stats()
      val dhtNodeCount = stats.dhtNodes()
      Timber.d("DHT Status before metadata fetch: $dhtNodeCount nodes connected")

      if (dhtNodeCount == 0L) {
        Timber.w("DHT failed to connect to any nodes - may have network issues")
        repo.updateState(
          state.copy(
            status = DownloadStatus.QUEUED,
            error = "Warning: DHT not connected. Trying trackers..."
          )
        )
      }
    } catch (e: Exception) {
      Timber.w("Could not get DHT stats: ${e.message}")
    }

    for (attempt in 1..maxAttempts) {
      if (!isActive) {
        Timber.d("Worker cancelled during magnet fetch attempt $attempt")
        return@withContext TorrentMetadataResult(
          errorResult = Result.retry()
        )
      }

      try {
        Timber.d("Magnet fetch attempt $attempt/$maxAttempts (timeout: ${perAttemptTimeoutSeconds}s)")

        repo.updateState(
          state.copy(
            status = DownloadStatus.QUEUED,
            error = "Fetching metadata from peers (attempt $attempt/$maxAttempts)..."
          )
        )

        meta = session.fetchMagnet(magnetUri, perAttemptTimeoutSeconds, tmp)

        if (meta != null) {
          Timber.i("Magnet metadata fetched successfully on attempt $attempt (${meta.size} bytes)")
          break
        }
        Timber.w("Magnet fetch attempt $attempt returned null metadata (no peers responded)")
      } catch (t: Throwable) {
        Timber.w("Magnet fetch attempt $attempt failed: ${t.javaClass.simpleName} - ${t.message}")
      }

      if (attempt < maxAttempts) {
        val delayMs = 2000L
        Timber.d("Waiting ${delayMs}ms before retry")

        if (!isActive) {
          Timber.d("Worker cancelled during retry wait")
          return@withContext TorrentMetadataResult(
            errorResult = Result.retry()
          )
        }

        delay(delayMs)
      }
    }

    if (meta == null) {
      val err =
        "Unable to download torrent metadata. This magnet link may be dead (no peers found) or your network may be blocking torrent connections. Try a different magnet link or check your network settings."
      Timber.e("Magnet fetch failed for taskId=$taskId after $maxAttempts attempts")
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
      return@withContext TorrentMetadataResult(
        errorResult = Result.failure(workDataOf(KEY_ERROR to err))
      )
    }

    try {
      tmp.deleteRecursively()
    } catch (e: Exception) {
      Timber.w("Failed to clean up temp directory: ${e.message}")
    }

    Timber.d("Decoding magnet metadata (${meta.size} bytes)")
    try {
      val torrentInfo = TorrentInfo.bdecode(meta)
      TorrentMetadataResult(
        isSuccess = true,
        torrentInfo = torrentInfo
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to decode magnet metadata")
      TorrentMetadataResult(
        errorResult = Result.failure(workDataOf(KEY_ERROR to "Failed to decode metadata"))
      )
    }
  }

  private suspend fun pollDownloadProgress(
    handle: TorrentHandle,
    torrentInfo: TorrentInfo,
    taskId: String,
    torrentName: String,
    saveDir: File,
    initialState: cloud.app.csplayer.download.DownloadState
  ): Result = withContext(Dispatchers.IO) {
    var lastProgress = 0
    var stuckCounter = 0
    val stuckThresholdCount = 30
    var currentState = initialState

    while (isActive) {
      val status = handle.status()
      var progress = (status.progress() * 100).roundToInt()
      val totalBytes = status.totalDone()
      val downloadRate = status.downloadRate().toLong()
      val uploadRate = status.uploadRate().toLong()
      val torrentState = status.state()
      val percentageOfTotalDownloaded = if (torrentInfo.totalSize() > 0) {
        (totalBytes * 100) / torrentInfo.totalSize()
      } else 0

      if (progress > 94 && progress < 100 && percentageOfTotalDownloaded >= 95) {
        if (progress == lastProgress) {
          stuckCounter++
          Timber.d("Progress stuck at $progress% (actual: $percentageOfTotalDownloaded%, count: $stuckCounter/$stuckThresholdCount), state: $torrentState, rate: ${downloadRate}B/s")

          if (stuckCounter >= stuckThresholdCount) {
            Timber.w(
              "Download appears stuck at $progress% (actual data: $percentageOfTotalDownloaded%) for 30+ seconds. " +
                "Forcing completion as verification phase is complete."
            )
            progress = 100
          }
        } else {
          stuckCounter = 0
          lastProgress = progress
        }
      } else {
        stuckCounter = 0
        lastProgress = progress
      }

      currentState = repo.observeState(taskId).first() ?: currentState

      val numSeeds = try {
        status.numSeeds()
      } catch (e: Exception) {
        Timber.d("Could not get numSeeds: ${e.message}")
        0
      }

      val numPeers = try {
        status.numPeers()
      } catch (e: Exception) {
        Timber.d("Could not get numPeers: ${e.message}")
        0
      }
      val task = currentState.task.copy(targetPath = saveDir.absolutePath,
        fileName = torrentName
        )
      repo.updateState(
        currentState.copy(
          task = task,
          status = DownloadStatus.DOWNLOADING,
          downloadedBytes = totalBytes,
          totalBytes = torrentInfo.totalSize(),
          progress = progress,
          speed = downloadRate,
          uploadSpeed = uploadRate,
          numSeeds = numSeeds,
          numPeers = numPeers
        )
      )

      setForeground(createForegroundInfo(taskId, progress, torrentName))
      setProgress(workDataOf(KEY_PROGRESS to progress))

      val isComplete = progress >= 100 ||
        torrentState == TorrentStatus.State.SEEDING ||
        torrentState == TorrentStatus.State.FINISHED ||
        status.isFinished

      if (isComplete) {
        val result = handleDownloadCompletion(
          taskId,
          saveDir,
          torrentInfo,
          uploadRate,
          currentState
        )
        return@withContext if (result) Result.success() else Result.retry()
      }

      delay(POLL_INTERVAL_MS)
    }

    currentState = repo.observeState(taskId).first() ?: currentState
    repo.updateState(currentState.copy(status = DownloadStatus.PAUSED))
    Result.retry()
  }

  private suspend fun handleDownloadCompletion(
    taskId: String,
    saveDir: File,
    torrentInfo: TorrentInfo,
    uploadRate: Long,
    currentState: cloud.app.csplayer.download.DownloadState
  ): Boolean = withContext(Dispatchers.IO) {
    val torrentRootName = torrentInfo.name()
    val torrentDirectory = File(saveDir, torrentRootName).absolutePath

    Timber.i("Torrent completed, saved to directory: $torrentDirectory")
    Timber.d("Save dir: ${saveDir.absolutePath}")
    Timber.d("Torrent root name: $torrentRootName")

    // fileName is the downloaded folder path (torrentDirectory)
    // This allows UI to find video files within the folder
    Timber.i("✓ Setting fileName to torrent folder: $torrentDirectory")

    val updatedTaskWithFile = currentState.task.copy(
      targetPath = saveDir.absolutePath,
      fileName = torrentDirectory  // Path to torrent download folder
    )

    val completedState = currentState.copy(
      task = updatedTaskWithFile,
      status = DownloadStatus.COMPLETED,
      downloadedBytes = torrentInfo.totalSize(),
      totalBytes = torrentInfo.totalSize(),
      progress = 100,
      speed = 0L,
      uploadSpeed = uploadRate,
      completedAt = System.currentTimeMillis()
    )

    Timber.d("Updating state - targetPath (save dir): ${updatedTaskWithFile.targetPath}")
    Timber.d("Updating state - fileName (torrent folder): ${updatedTaskWithFile.fileName}")
    repo.updateState(completedState)

    // Verify the update was persisted
    delay(100L)
    val verifyState = repo.observeState(taskId).first()
    Timber.d("✓ Verified - targetPath: ${verifyState?.task?.targetPath}")
    Timber.d("✓ Verified - fileName (torrent folder): ${verifyState?.task?.fileName}")

    scanVideoFilesIntoMediaStore(torrentDirectory)

    Timber.i("Torrent download completed: $taskId")

    try {
      coordinator.processQueuedDownloads()
    } catch (e: Exception) {
      Timber.w(e, "Failed to process queued downloads after completion")
    }
    true
  }

  private suspend fun scanVideoFilesIntoMediaStore(downloadedFilePath: String) {
    try {
      // Use KUniFile for directory operations
      val torrentDirKuni = cloud.app.csplayer.utils.KUniFile.fromFile(context, File(downloadedFilePath))

      val videoFiles = if (torrentDirKuni != null && torrentDirKuni.exists() && torrentDirKuni.isDirectory) {
        // Use KUniFile to list files
        findVideoFilesRecursively(torrentDirKuni)
      } else {
        Timber.w("Torrent directory does not exist or is not accessible: $downloadedFilePath")
        emptyList()
      }

      Timber.d("Torrent download: Found ${videoFiles.size} video files to scan")
      videoFiles.forEach { videoFilePath ->
        try {
          mediaStore.scanMedia(videoFilePath)
          Timber.d("Torrent download: Scanned file into MediaStore: $videoFilePath")
        } catch (e: Exception) {
          Timber.w(e, "Torrent download: Failed to scan file: $videoFilePath")
        }
      }
    } catch (e: Exception) {
      Timber.w(e, "Torrent download: Failed to scan files into MediaStore")
    }
  }

  /**
   * Recursively find video files in a directory using KUniFile
   */
  private fun findVideoFilesRecursively(directory: cloud.app.csplayer.utils.KUniFile): List<String> {
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
    val result = mutableListOf<String>()

    fun scan(dir: cloud.app.csplayer.utils.KUniFile) {
      try {
        dir.listFiles()?.forEach { file ->
          if (file.isDirectory) {
            // Recursively scan subdirectories
            scan(file)
          } else {
            // Check if it's a video file
            val fileName = file.name ?: ""
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension in videoExtensions) {
              // Get file path for MediaStore scanning
              val filePath = file.filePath
              if (filePath != null) {
                result.add(filePath)
              } else {
                Timber.w("Cannot get file path for: ${file.uri}")
              }
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Error scanning directory: ${dir.uri}")
      }
    }

    scan(directory)
    return result
  }

}

