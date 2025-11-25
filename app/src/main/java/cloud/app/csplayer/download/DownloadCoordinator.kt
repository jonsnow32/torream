package cloud.app.csplayer.download

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import cloud.app.csplayer.R
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
   * Respects the concurrent download limit setting.
   */
  suspend fun startDownload(task: DownloadTask) {
    Timber.d("startDownload: taskId=${task.id}, type=${task.type}, source=${task.source}")

    // Always cancel any existing work first to ensure clean slate
    // This handles case where work is left in ENQUEUED/CANCELLED state from previous delete
    try {
      workManager.cancelUniqueWork("download_${task.id}")
      Timber.d("Cancelled any existing work for taskId=${task.id}")
      // Small delay to ensure WorkManager processes cancellation
      kotlinx.coroutines.delay(50L)
    } catch (e: Exception) {
      Timber.w(e, "Failed to cancel existing work")
    }

    // Check if download is actually running (RUNNING state only, not ENQUEUED)
    val existingWork = try {
      workManager.getWorkInfosForUniqueWork("download_${task.id}").get()
    } catch (e: Exception) {
      emptyList()
    }

    val isCurrentlyRunning = existingWork.any { workInfo ->
      workInfo.state == WorkInfo.State.RUNNING
    }

    if (isCurrentlyRunning) {
      Timber.w("Download currently RUNNING for taskId=${task.id}, skipping")
      return
    }

    // Get concurrent download limit from settings
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val concurrentLimit = prefs.getInt(context.getString(R.string.download_concurrent_limit_key), 3)

    // Check current active downloads if limit is set
    if (concurrentLimit > 0) {
      val activeDownloads = getActiveDownloadCount()
      if (activeDownloads >= concurrentLimit) {
        Timber.d("Concurrent download limit ($concurrentLimit) reached. Active: $activeDownloads. Queuing task ${task.id}")
        // Keep in QUEUED status so it will be picked up when a download finishes
        val existingState = repo.observeState(task.id).firstOrNull()
        if (existingState != null) {
          repo.updateState(existingState.copy(status = DownloadStatus.QUEUED, error = null))
        } else {
          repo.insertTask(task, DownloadStatus.QUEUED)
        }
        return
      }
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
              .setRequiredNetworkType(getRequiredNetworkType(context))
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
              .setRequiredNetworkType(getRequiredNetworkType(context))
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
    workManager.cancelAllWorkByTag(taskId)

    // Update status in repository
    val currentState = repo.observeState(taskId).firstOrNull()
    currentState?.let {
      repo.updateState(it.copy(status = DownloadStatus.PAUSED))
    }
  }

  /**
   * Resume a paused download.
   */
  suspend fun resumeDownload(taskId: String) {
    Timber.d("resumeDownload: taskId=$taskId")

    // Get task from repository
    val currentState = repo.observeState(taskId).firstOrNull()
    currentState?.let { state ->
      if (state.status == DownloadStatus.PAUSED) {
        // Clear any existing work that was cancelled
        try {
          workManager.cancelUniqueWork("download_$taskId")
          workManager.cancelAllWorkByTag(taskId)
          Timber.d("Cleared previous work for taskId=$taskId before resume")
        } catch (e: Exception) {
          Timber.w(e, "Failed to clear previous work")
        }

        // Small delay to ensure WorkManager has time to process cancellation
        kotlinx.coroutines.delay(100L)

        // Re-enqueue the download
        startDownload(state.task)
      }
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

    // Cancel worker first - use multiple strategies to ensure complete cleanup
    try {
      workManager.cancelUniqueWork("download_$taskId")
      Timber.d("Cancelled unique work for taskId=$taskId")
    } catch (e: Exception) {
      Timber.w(e, "Failed to cancel unique work")
    }

    try {
      workManager.cancelAllWorkByTag(taskId)
      Timber.d("Cancelled all work by tag for taskId=$taskId")
    } catch (e: Exception) {
      Timber.w(e, "Failed to cancel work by tag")
    }

    // Small delay to ensure WorkManager processes cancellation
    kotlinx.coroutines.delay(100L)

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

  /**
   * Get count of currently active (running or enqueued) downloads.
   */
  private fun getActiveDownloadCount(): Int {
    return try {
      val allWork = workManager.getWorkInfosByTag(DOWNLOAD_WORK_TAG).get()
      val activeCount = allWork.count { workInfo ->
        workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
      }
      Timber.d("Active downloads: $activeCount")
      activeCount
    } catch (e: Exception) {
      Timber.w("Failed to get active download count: ${e.message}")
      0
    }
  }

  /**
   * Process queued downloads when a download completes.
   * This is called after a download finishes to start the next queued download if below limit.
   */
  suspend fun processQueuedDownloads() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val concurrentLimit = prefs.getInt(context.getString(R.string.download_concurrent_limit_key), 3)

    // Only process if limit is enabled
    if (concurrentLimit <= 0) return

    try {
      val activeDownloads = getActiveDownloadCount()
      if (activeDownloads >= concurrentLimit) {
        Timber.d("Concurrent limit still reached. Active: $activeDownloads, Limit: $concurrentLimit")
        return
      }

      // Get all queued downloads from repository
      val allStates = repo.observeAllStates().firstOrNull() ?: emptyList()
      val queuedDownloads = allStates.filter { it.status == DownloadStatus.QUEUED }

      if (queuedDownloads.isEmpty()) {
        Timber.d("No queued downloads to process")
        return
      }

      // Start the first queued download (FIFO)
      val nextDownload = queuedDownloads.first()
      Timber.d("Starting next queued download: ${nextDownload.task.id}")
      startDownload(nextDownload.task)
    } catch (e: Exception) {
      Timber.e(e, "Failed to process queued downloads")
    }
  }

  /**
   * Get the required network type based on user preferences.
   * Returns UNMETERED (WiFi only) if download_over_wifi_key is enabled,
   * otherwise returns CONNECTED (any network).
   */
  private fun getRequiredNetworkType(context: Context): NetworkType {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val isWifiOnly = prefs.getBoolean(context.getString(R.string.download_over_wifi_key), false)
    //val isWifiOnly = prefs.getBoolean(context.getString(R.string.download_over_metered_key), false)

    return if (isWifiOnly) {
      Timber.d("Download network constraint: WiFi only")
      NetworkType.UNMETERED  // WiFi (unmetered) networks only
    } else {
      Timber.d("Download network constraint: Any network")
      NetworkType.CONNECTED  // Any network (WiFi or metered)
    }
  }

  companion object {
    private const val DOWNLOAD_WORK_TAG = "download_work"
  }
}

