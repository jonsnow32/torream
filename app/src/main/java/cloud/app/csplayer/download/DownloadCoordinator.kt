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
import cloud.app.csplayer.utils.AppUtils.getDownloadPath
import cloud.app.csplayer.utils.UnifiedFileFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
   *
   * Note: Updates task.targetPath to current user-configured download path before starting.
   */
  suspend fun startDownload(task: DownloadTask) {
    Timber.d("startDownload: taskId=${task.id}, type=${task.type}, source=${task.source}")

    // Check if this source has already been downloaded successfully
    val allStates = repo.observeAllStates().firstOrNull() ?: emptyList()
    val alreadyDownloaded = allStates.any { it.status == DownloadStatus.COMPLETED && it.task.source == task.source }

    if (alreadyDownloaded) {
      // Show Toast on main/UI thread and skip starting download
      withContext(Dispatchers.Main) {
        android.widget.Toast.makeText(
          context,
          context.getString(R.string.file_already_downloaded),
          android.widget.Toast.LENGTH_SHORT
        ).show()
      }
      Timber.d("Source already downloaded, skipping start for taskId=${task.id}, source=${task.source}")
      return
    }

    // Update task with current download path from preferences
    // This ensures downloads go to the user's current download directory setting
    val currentDownloadPath = withContext(Dispatchers.IO) {
      try {
        val downloadDir = context.getDownloadPath()
        downloadDir?.uri?.toString()
      } catch (e: Exception) {
        Timber.w(e, "Failed to get current download path, using task's targetPath")
        null
      }
    }

    // Use current path if available, otherwise fallback to task's path
    val updatedTask = if (currentDownloadPath != null) {
      task.copy(targetPath = currentDownloadPath, createdAt = System.currentTimeMillis()).also {
        Timber.d("Updated task targetPath to current setting: $currentDownloadPath")
      }
    } else {
      task
    }

    // ...existing code...
    // Always cancel any existing work first to ensure clean slate
    // This handles case where work is left in ENQUEUED/CANCELLED state from previous delete
    try {
      workManager.cancelUniqueWork("download_${updatedTask.id}")
      Timber.d("Cancelled any existing work for taskId=${updatedTask.id}")
      // Small delay to ensure WorkManager processes cancellation
      kotlinx.coroutines.delay(50L)
    } catch (e: Exception) {
      Timber.w(e, "Failed to cancel existing work")
    }

    // Check if download is actually running (RUNNING state only, not ENQUEUED)
    val existingWork = try {
      workManager.getWorkInfosForUniqueWork("download_${updatedTask.id}").get()
    } catch (e: Exception) {
      emptyList()
    }

    val isCurrentlyRunning = existingWork.any { workInfo ->
      workInfo.state == WorkInfo.State.RUNNING
    }

    if (isCurrentlyRunning) {
      Timber.w("Download currently RUNNING for taskId=${updatedTask.id}, skipping")
      return
    }

    // Get concurrent download limit from settings
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val concurrentLimit = prefs.getInt(context.getString(R.string.download_concurrent_limit_key), 3)

    // Check current active downloads if limit is set
    if (concurrentLimit > 0) {
      val activeDownloads = getActiveDownloadCount()
      if (activeDownloads >= concurrentLimit) {
        Timber.d("Concurrent download limit ($concurrentLimit) reached. Active: $activeDownloads. Queuing task ${updatedTask.id}")
        // Keep in QUEUED status so it will be picked up when a download finishes
        val existingState = repo.observeState(updatedTask.id).firstOrNull()
        if (existingState != null) {
          repo.updateState(existingState.copy(status = DownloadStatus.QUEUED, error = null, task = updatedTask))
        } else {
          repo.insertTask(updatedTask, DownloadStatus.QUEUED)
        }
        return
      }
    }

    // Insert or update task in repository
    val existingState = repo.observeState(updatedTask.id).firstOrNull()
    if (existingState != null) {
      // Update existing task with new path
      repo.updateState(existingState.copy(status = DownloadStatus.QUEUED, error = null, task = updatedTask))
      Timber.d("Updated existing task in repository with id=${updatedTask.id}")
    } else {
      // Insert new task with updated path
      repo.insertTask(updatedTask, DownloadStatus.QUEUED)
      Timber.d("Inserted new task into repository with id=${updatedTask.id}")
    }

    // Ensure database write is committed before starting worker
    // Verify task exists in database
    var retries = 0
    while (retries < 10) {
      val verifyState = repo.observeState(updatedTask.id).firstOrNull()
      if (verifyState != null) {
        Timber.d("✓ Task verified in database: ${updatedTask.id}")
        break
      }
      Timber.w("Task not yet in database, retry ${retries + 1}/10")
      kotlinx.coroutines.delay(50L)
      retries++
    }

    if (retries >= 10) {
      Timber.e("Failed to verify task in database after 10 retries: ${updatedTask.id}")
      // Try to insert again
      try {
        repo.insertTask(updatedTask, DownloadStatus.QUEUED)
        kotlinx.coroutines.delay(100L)
      } catch (e: Exception) {
        Timber.e(e, "Failed to insert task on retry")
      }
    }

    // Create work request based on type
    val workRequest = when (updatedTask.type) {
      DownloadType.HTTP -> {
        OneTimeWorkRequestBuilder<HttpDownloadWorker>()
          .setInputData(workDataOf(HttpDownloadWorker.KEY_TASK_ID to updatedTask.id))
          .setConstraints(
            Constraints.Builder()
              .setRequiredNetworkType(getRequiredNetworkType(context))
              .build()
          )
          .addTag(DOWNLOAD_WORK_TAG)
          .addTag(updatedTask.id)
          .build()
      }

      DownloadType.TORRENT -> {
        OneTimeWorkRequestBuilder<TorrentDownloadWorker>()
          .setInputData(workDataOf(TorrentDownloadWorker.KEY_TASK_ID to updatedTask.id))
          .setConstraints(
            Constraints.Builder()
              .setRequiredNetworkType(getRequiredNetworkType(context))
              .build()
          )
          .addTag(DOWNLOAD_WORK_TAG)
          .addTag(updatedTask.id)
          .build()
      }
    }

    // Enqueue work with unique work name
    // Use REPLACE to allow re-downloading after delete/failure
    workManager.enqueueUniqueWork(
      "download_${updatedTask.id}",
      ExistingWorkPolicy.REPLACE,
      workRequest
    )

    Timber.i("Download work enqueued for taskId=${updatedTask.id}")
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
    if (state != null) {
      try {
        val downloadState = state
        val taskId = taskId
        val fileName = downloadState.task.fileName
        val targetPath = downloadState.task.targetPath
        val downloadType = downloadState.task.type

        Timber.d("Cleanup: fileName=$fileName, targetPath=$targetPath, type=$downloadType")

        if (fileName.isNullOrBlank()) {
          Timber.w("No fileName in task, skipping file cleanup")
        } else {
          // Use UnifiedFile to handle both regular files and SAF URIs transparently
          val unifiedFile = if (targetPath.startsWith("content://")) {
            // SAF URI - use fromUri
            UnifiedFileFactory.fromUri(context, android.net.Uri.parse(targetPath))
          } else {
            // Regular file path - use fromFile
            UnifiedFileFactory.fromFile(context, java.io.File(targetPath))
          }

          if (unifiedFile != null) {
            when (downloadType) {
              DownloadType.TORRENT -> {
                // For torrents: look for the subdirectory with fileName
                if (unifiedFile.isDirectory) {
                  val torrentDir = unifiedFile.findFile(fileName)
                  if (torrentDir != null) {
                    if (torrentDir.delete()) {
                      Timber.d("✓ Deleted torrent directory: $fileName")
                    } else {
                      Timber.w("✗ Failed to delete torrent directory: $fileName")
                    }
                  } else {
                    Timber.d("Torrent directory not found: $fileName")
                  }
                }

                // Also cleanup magnet temp directory if exists
                val magnetTempDirName = "magnet_tmp_$taskId"
                val magnetTempDir = unifiedFile.findFile(magnetTempDirName)
                if (magnetTempDir != null && magnetTempDir.delete()) {
                  Timber.d("✓ Deleted magnet temp directory: $magnetTempDirName")
                }
              }

              DownloadType.HTTP -> {
                // For HTTP: directly delete the file
                if (unifiedFile.delete()) {
                  Timber.d("✓ Deleted HTTP file: $fileName")
                } else {
                  Timber.w("✗ Failed to delete HTTP file: $fileName")
                }

                // Try to delete .part temp file if it exists
                val partFileName = "$fileName.part"
                val partFile = unifiedFile.findFile(partFileName)
                if (partFile?.delete() == true) {
                  Timber.d("✓ Deleted .part file: $partFileName")
                }
              }
            }
          } else {
            Timber.w("Failed to create UnifiedFile for path: $targetPath")
          }
        }

      } catch (e: Exception) {
        Timber.e(e, "Failed to cleanup files for taskId=$taskId")
      }
    } else {
      Timber.w("Download state is null, cannot cleanup files for taskId=$taskId")
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

