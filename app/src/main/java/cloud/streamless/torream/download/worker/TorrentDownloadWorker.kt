package cloud.streamless.torream.download.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.streamless.torream.download.DownloadCoordinator
import cloud.streamless.torream.download.DownloadRepository
import cloud.streamless.torream.download.DownloadStatus
import cloud.streamless.torream.download.torrent.TorrentDownloadEngine
import cloud.streamless.torream.download.torrent.TorrentSessionManager
import cloud.streamless.torream.utils.UnifiedFileFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * TorrentDownloadWorker - Rewritten using TorrentDownloadEngine
 * Based on LibreTorrent architecture with proper session management
 */
@HiltWorker
class TorrentDownloadWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val repo: DownloadRepository,
  private val mediaStore: cloud.streamless.torream.media.dataSource.MediaStoreDataSource,
  private val coordinator: DownloadCoordinator,
  private val sessionManager: TorrentSessionManager,
  private val downloadEngine: TorrentDownloadEngine
) : CoroutineWorker(context, params) {

  companion object {
    const val KEY_TASK_ID = "task_id"
    const val KEY_ERROR = "error"
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure(
      workDataOf(KEY_ERROR to "No taskId")
    )

    Timber.i("⚡ TorrentDownloadWorker started: $taskId")
    setForeground(createForegroundInfo(taskId, 0))

    try {
      // Wait for task in DB
      val state = waitForTask(taskId) ?: return@withContext Result.failure(
        workDataOf(KEY_ERROR to "Task not found")
      )

      val task = state.task
      Timber.d("📦 Task: source=${task.source}")

      // Prepare save directory using UnifiedFile (same as HttpDownloadWorker)
      val targetDir = UnifiedFileFactory.fromUri(context, task.targetPath.toUri()) ?: run {
        val error = "Invalid target directory"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      // Get actual file path for libtorrent
      val saveDir = File(targetDir.filePath ?: run {
        val error = "Cannot get file path from URI"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      })

      Timber.d("✅ Save directory: ${saveDir.absolutePath}")

      repo.updateState(state.copy(status = DownloadStatus.REPAIRING))
      // Start session
      sessionManager.start()

      // Start download using engine
      val downloadFlow = when {
        task.source.startsWith("magnet:", ignoreCase = true) ->
          downloadEngine.downloadMagnet(task.source, saveDir, sequential = false)
        task.source.endsWith(".torrent", ignoreCase = true) ->
          downloadEngine.downloadTorrentFile(task.source, saveDir, sequential = false)
        else -> return@withContext Result.failure(workDataOf(KEY_ERROR to "Invalid torrent source"))
      }

      // Monitor download progress
      var currentState = state
      downloadFlow.collect { info ->
        if (!isActive) {
          return@collect
        }

        // Libtorrent creates a folder named after the torrent inside saveDir
        // So the actual torrent folder is: saveDir/torrentName
        val actualTorrentFolder = File(saveDir, info.name)

        // Update state in database
        currentState = repo.observeState(taskId).first() ?: currentState
        repo.updateState(
          currentState.copy(
            task = currentState.task.copy(
              targetPath = actualTorrentFolder.absolutePath, // Save the actual torrent folder, not just download dir
              fileName = info.name
            ),
            status = when {
              info.error != null -> DownloadStatus.FAILED
              info.isFinished -> DownloadStatus.COMPLETED
              else -> DownloadStatus.DOWNLOADING
            },
            downloadedBytes = info.downloadedBytes,
            totalBytes = info.totalSize,
            progress = info.progress,
            speed = info.downloadRate,
            uploadSpeed = info.uploadRate,
            numSeeds = info.numSeeds,
            numPeers = info.numPeers,
            completedAt = if (info.isFinished) System.currentTimeMillis() else null
          )
        )

        // Update notification
        setForeground(createForegroundInfo(taskId, info.progress, info.name))

        // Log progress
        if (info.progress % 10 == 0) {
          Timber.d("📈 ${info.name}: ${info.progress}% | ↓${info.downloadRate}B/s | Seeds: ${info.numSeeds} | Peers: ${info.numPeers}")
        }

        // Handle completion
        if (info.isFinished) {
          Timber.i("✅ Download completed: ${info.name}")

          // Scan files in the actual torrent folder
          scanVideoFiles(actualTorrentFolder.absolutePath)

          // Process queue
          try {
            coordinator.processQueuedDownloads()
          } catch (e: Exception) {
            Timber.w(e, "Failed to process queue")
          }

          return@collect
        }

        // Handle error
        if (info.error != null) {
          Timber.e("❌ Download error: ${info.error}")
          throw Exception(info.error)
        }
      }

      return@withContext Result.success()

    } catch (e: Exception) {
      Timber.e(e, "❌ Download error: $taskId")
      val fallbackPath = context.getExternalFilesDir(null)?.path ?: context.filesDir.path
      val errorMessage = if (cloud.streamless.torream.utils.StorageUtils.isNoSpaceError(e)) {
        cloud.streamless.torream.utils.StorageUtils.noSpaceMessage(fallbackPath)
      } else {
        e.message ?: "Unknown error"
      }

      // Mark the task as failed in DB
      val failedState = repo.observeState(taskId).first()
      if (failedState != null) {
        repo.updateState(failedState.copy(status = DownloadStatus.FAILED, error = errorMessage))
      }

      // Show a visible error notification so the user knows the download failed
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
      val notification = DownloadNotificationHelper.createErrorNotification(
        context, taskId, failedState?.task?.fileName, errorMessage
      )
      notificationManager.notify(taskId.hashCode(), notification)
      DownloadNotificationHelper.postSummary(context)

      return@withContext Result.failure(workDataOf(KEY_ERROR to errorMessage))
    }
  }



  private suspend fun waitForTask(taskId: String) = withContext(Dispatchers.IO) {
    repeat(20) {
      val state = repo.observeState(taskId).first()
      if (state != null) return@withContext state
      delay(100)
    }
    null
  }


  private suspend fun scanVideoFiles(directory: String) {
    try {
      val dirFile = UnifiedFileFactory.fromFile(context, File(directory))
      if (dirFile != null && dirFile.isDirectory) {
        scanRecursive(dirFile)
      }
    } catch (e: Exception) {
      Timber.w(e, "Failed to scan files")
    }
  }

  private suspend fun scanRecursive(dir: cloud.streamless.torream.utils.UnifiedFile) {
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")

    try {
      dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
          scanRecursive(file)
        } else {
          val ext = file.name.substringAfterLast('.', "").lowercase()
          if (ext in videoExtensions) {
            file.filePath?.let { path ->
              try {
                mediaStore.scanMedia(path)
              } catch (_: Exception) {}
            }
          }
        }
      }
    } catch (_: Exception) {}
  }

  private fun createForegroundInfo(
    taskId: String,
    progress: Int,
    fileName: String? = null
  ): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context, taskId, progress, false, fileName
    )
    DownloadNotificationHelper.postSummary(context)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(taskId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(taskId.hashCode(), notification)
    }
  }
}

