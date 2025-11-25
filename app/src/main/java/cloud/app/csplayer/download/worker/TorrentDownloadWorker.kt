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

    val state = repo.observeState(taskId).first()
    if (state == null) {
      val error = "Task not found in repository for taskId=$taskId"
      Timber.e("TorrentDownloadWorker: $error")
      return@withContext Result.failure(workDataOf(KEY_ERROR to error))
    }
    val task = state.task
    Timber.d("TorrentDownloadWorker: Found task - id=${task.id}, type=${task.type}")
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

      // Prepare save directory - convert content URI to real path if needed
      val baseDirPath = when {
        task.targetPath.startsWith("content://") -> {
          // Convert content URI to real filesystem path using KUniFile
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

      val baseDir = File(baseDirPath).let { if (it.isDirectory) it else it.parentFile ?: it }

      // Use baseDir directly as saveDir without taskId subdirectory
      val saveDir = baseDir
      if (!saveDir.exists()) {
        saveDir.mkdirs()
        Timber.d("Created torrent save directory: ${saveDir.absolutePath}")
      }

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
          val torrentFile = File(task.source)
          if (!torrentFile.exists()) {
            val err = "Torrent file not found: ${task.source}"
            repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
            return@withContext Result.failure(workDataOf(KEY_ERROR to err))
          }
          TorrentInfo(torrentFile)
        }

        else -> {
          val err = "Bare info-hash not directly supported, use magnet link"
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
        title = torrentName
      )
      currentState = currentState.copy(task = updatedTask)

      repo.updateState(currentState.copy(status = DownloadStatus.DOWNLOADING))

      // Poll torrent status until complete or cancelled
      val result = pollDownloadProgress(h, ti, taskId, torrentName, saveDir, currentState)
      if (result != Result.success()) {
        return@withContext result
      }

      // Worker was cancelled - fetch current state
      currentState = repo.observeState(taskId).first() ?: currentState
      repo.updateState(currentState.copy(status = DownloadStatus.PAUSED))
      return@withContext Result.retry()

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

    val tmp = File(saveDir, "magnet_tmp_${taskId}").apply {
      if (exists()) {
        deleteRecursively()
      }
      mkdirs()
    }
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

      repo.updateState(
        currentState.copy(
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
    val downloadedFilePath = File(saveDir, torrentRootName).absolutePath

    Timber.i("Torrent completed, saved to directory: $downloadedFilePath")

    val updatedTaskWithFile = currentState.task.copy(downloadedFilePath = downloadedFilePath)

    repo.updateState(
      currentState.copy(
        task = updatedTaskWithFile,
        status = DownloadStatus.COMPLETED,
        downloadedBytes = torrentInfo.totalSize(),
        totalBytes = torrentInfo.totalSize(),
        progress = 100,
        speed = 0L,
        uploadSpeed = uploadRate,
        completedAt = System.currentTimeMillis()
      )
    )

    scanVideoFilesIntoMediaStore(downloadedFilePath)

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
      val torrentDir = File(downloadedFilePath)
      val videoFiles = if (torrentDir.exists() && torrentDir.isDirectory) {
        torrentDir.walkTopDown()
          .filter { it.isFile }
          .filter { file ->
            val extension = file.extension.lowercase()
            extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
          }
          .toList()
      } else {
        Timber.w("Torrent directory does not exist: $downloadedFilePath")
        emptyList()
      }

      Timber.d("Torrent download: Found ${videoFiles.size} video files to scan")
      videoFiles.forEach { videoFile ->
        try {
          mediaStore.scanMedia(videoFile.absolutePath)
          Timber.d("Torrent download: Scanned file into MediaStore: ${videoFile.absolutePath}")
        } catch (e: Exception) {
          Timber.w(e, "Torrent download: Failed to scan file: ${videoFile.absolutePath}")
        }
      }
    } catch (e: Exception) {
      Timber.w(e, "Torrent download: Failed to scan files into MediaStore")
    }
  }

}

