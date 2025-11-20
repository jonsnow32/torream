package cloud.app.csplayer.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import cloud.app.csplayer.download.worker.HttpDownloadWorker
import cloud.app.csplayer.download.worker.TorrentDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinator for managing downloads using WorkManager.
 * This ensures downloads run in the background even if the app is killed.
 */
@Singleton
class DownloadCoordinator @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repo: DownloadRepository
) {

  private val workManager = WorkManager.getInstance(context)

  /**
   * Enqueue and start a download task.
   * This will persist the task and schedule a Worker to handle it.
   */
  suspend fun startDownload(task: DownloadTask) {
    Timber.d("startDownload: taskId=${task.id}, type=${task.type}, source=${task.source}")

    // Check if download is already running
    val existingWork = try {
      workManager.getWorkInfosForUniqueWork("download_${task.id}").get()
    } catch (e: Exception) {
      emptyList()
    }

    val isRunning = existingWork.any { workInfo ->
      workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
    }

    if (isRunning) {
      Timber.w("Download already running for taskId=${task.id}, skipping")
      return
    }

    // Insert or update task in repository
    val existingState = repo.observeState(task.id).firstOrNull()
    if (existingState != null) {
      // Update existing task
      repo.updateState(existingState.copy(status = DownloadStatus.QUEUED, error = null))
      Timber.d("Updated existing task in repository with id=${task.id}")
    } else {
      // Insert new task
      repo.insertTask(task, DownloadStatus.QUEUED)
      Timber.d("Inserted new task into repository with id=${task.id}")
    }

    // Create work request based on type
    val workRequest = when (task.type) {
      DownloadType.HTTP -> {
        OneTimeWorkRequestBuilder<HttpDownloadWorker>()
          .setInputData(workDataOf(HttpDownloadWorker.KEY_TASK_ID to task.id))
          .setConstraints(
            Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .build()
          )
          .addTag(DOWNLOAD_WORK_TAG)
          .addTag(task.id)
          .build()
      }

      DownloadType.TORRENT -> {
        OneTimeWorkRequestBuilder<TorrentDownloadWorker>()
          .setInputData(workDataOf(TorrentDownloadWorker.KEY_TASK_ID to task.id))
          .setConstraints(
            Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .build()
          )
          .addTag(DOWNLOAD_WORK_TAG)
          .addTag(task.id)
          .build()
      }
    }

    // Enqueue work with unique work name
    // Use REPLACE to allow re-downloading after delete/failure
    workManager.enqueueUniqueWork(
      "download_${task.id}",
      ExistingWorkPolicy.REPLACE,
      workRequest
    )

    Timber.i("Download work enqueued for taskId=${task.id}")
  }

  /**
   * Pause/cancel a download task.
   */
  suspend fun pauseDownload(taskId: String) {
    Timber.d("pauseDownload: taskId=$taskId")

    // Cancel the worker
    workManager.cancelUniqueWork("download_$taskId")

    // Update status in repository
    repo.observeState(taskId).map { it }.collect { currentState ->
      currentState?.let {
        repo.updateState(it.copy(status = DownloadStatus.PAUSED))
      }
      return@collect
    }
  }

  /**
   * Resume a paused download.
   */
  suspend fun resumeDownload(taskId: String) {
    Timber.d("resumeDownload: taskId=$taskId")

    // Get task from repository
    repo.observeState(taskId).map { it }.collect { currentState ->
      currentState?.let { state ->
        if (state.status == DownloadStatus.PAUSED) {
          // Re-enqueue the download
          startDownload(state.task)
        }
      }
      return@collect
    }
  }

  /**
   * Delete a download task and cancel its worker.
   * This will completely remove the task and allow re-downloading the same URL.
   */
  suspend fun deleteDownload(taskId: String) {
    Timber.d("deleteDownload: taskId=$taskId")

    // Get task info before deletion for cleanup
    val state = repo.observeState(taskId).map { it }.firstOrNull()

    // Cancel worker first
    workManager.cancelUniqueWork("download_$taskId")

    // Also cancel by tag to ensure complete cleanup
    workManager.cancelAllWorkByTag(taskId)

    // Cleanup downloaded files if exists
    state?.let { downloadState ->
      try {
        val targetPath = java.io.File(downloadState.task.targetPath)

        if (downloadState.task.type == DownloadType.TORRENT) {
          // For torrents: targetPath is the subdirectory, delete entire directory
          if (targetPath.exists() && targetPath.isDirectory) {
            targetPath.deleteRecursively()
            Timber.d("Deleted torrent directory: ${targetPath.absolutePath}")
          }

          // Also cleanup magnet temp directory if exists
          val magnetTempDir = java.io.File(targetPath.parentFile, "magnet_tmp_$taskId")
          if (magnetTempDir.exists()) {
            magnetTempDir.deleteRecursively()
            Timber.d("Deleted magnet temp dir: ${magnetTempDir.absolutePath}")
          }
        } else {
          // For HTTP: targetPath might be file or directory
          if (targetPath.exists()) {
            if (targetPath.isFile) {
              targetPath.delete()
              Timber.d("Deleted target file: ${targetPath.absolutePath}")
            } else if (targetPath.isDirectory) {
              // Delete directory and contents
              targetPath.deleteRecursively()
              Timber.d("Deleted target directory: ${targetPath.absolutePath}")
            }
          }

          // Delete .part file if exists
          val tempFile = java.io.File(downloadState.task.targetPath + ".part")
          if (tempFile.exists()) {
            tempFile.delete()
            Timber.d("Deleted temp file: ${tempFile.absolutePath}")
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Failed to cleanup files for taskId=$taskId")
      }
    }

    // Delete from repository (this removes DB entry)
    repo.deleteTask(taskId)

    Timber.i("Download task deleted: taskId=$taskId")
  }

  /**
   * Observe download state from repository.
   */
  fun observeDownload(taskId: String): Flow<DownloadState?> {
    return repo.observeState(taskId)
  }

  /**
   * Observe all downloads.
   */
  fun observeAllDownloads(): Flow<List<DownloadState>> {
    return repo.observeAllStates()
  }

  /**
   * Get WorkInfo for a download to check worker status.
   */
  fun getWorkInfo(taskId: String): Flow<WorkInfo?> {
    return workManager.getWorkInfosForUniqueWorkFlow("download_$taskId")
      .map { list -> list.firstOrNull() }
  }

  companion object {
    private const val DOWNLOAD_WORK_TAG = "download_work"
  }
}

