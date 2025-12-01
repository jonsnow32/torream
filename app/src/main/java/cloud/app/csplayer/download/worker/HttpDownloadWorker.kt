package cloud.app.csplayer.download.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.media.dataSource.MediaStoreDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import android.app.NotificationManager

/**
 * Simplified HTTP Download Worker
 * Core responsibilities:
 * 1. Download file from URL
 * 2. Save to target directory
 * 3. Update progress to database
 * 4. Handle pause/resume/cancel
 */
@HiltWorker
class HttpDownloadWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val repo: DownloadRepository,
  private val coordinator: DownloadCoordinator,
  private val mediaStore: MediaStoreDataSource
) : CoroutineWorker(context, params) {

  companion object {
    const val KEY_TASK_ID = "task_id"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR = "error"

    private const val BUFFER_SIZE = 8192
    private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

    Timber.i("HttpDownloadWorker started: $taskId")
    if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      setForeground(createForegroundInfo(taskId, 0))
    } else {
      // Show regular notification if in background
      showProgressNotification(taskId, 0)
    }

    // Load task from database with retry
    var state = repo.observeState(taskId).first()
    var retries = 0
    while (state == null && retries < 20) {
      delay(100L)
      state = repo.observeState(taskId).first()
      retries++
    }

    if (state == null) {
      return@withContext Result.failure(workDataOf(KEY_ERROR to "Task not found"))
    }

    val task = state.task
    Timber.d("Downloading from: ${task.source}")
    Timber.d("Save to: ${task.targetPath}")

    try {
      // Step 1: Prepare download file
      val targetDir = File(task.targetPath)
      if (!targetDir.exists()) {
        targetDir.mkdirs()
      }

      val fileName = extractFileName(task.source)
      val finalFile = File(targetDir, fileName)
      val tempFile = File(targetDir, "$fileName.part")

      Timber.i("Final file: ${finalFile.absolutePath}")
      Timber.i("Temp file: ${tempFile.absolutePath}")

      // Step 2: Open HTTP connection
      val url = URL(task.source)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 15000
      connection.readTimeout = 15000

      // Resume support
      val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
      if (existingBytes > 0) {
        connection.setRequestProperty("Range", "bytes=$existingBytes-")
        Timber.i("Resuming from byte: $existingBytes")
      }

      connection.connect()

      val responseCode = connection.responseCode
      Timber.d("HTTP Response: $responseCode")

      // Handle errors
      if (responseCode in 400..599) {
        val error = "HTTP error: $responseCode"
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      // Step 3: Get file size
      val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
      val totalBytes = if (contentLength > 0) {
        existingBytes + contentLength
      } else {
        -1L
      }

      Timber.i("Total size: ${totalBytes / (1024 * 1024)} MB")

      // Check if already complete
      if (responseCode == 416 && existingBytes > 0) {
        Timber.i("File already complete (HTTP 416)")
        finalizeDownload(tempFile, finalFile, state)
        return@withContext Result.success()
      }

      // Step 4: Download data
      connection.inputStream.use { input ->
        java.io.FileOutputStream(tempFile, existingBytes > 0).use { output ->
          downloadData(
            input = input,
            output = output,
            taskId = taskId,
            state = state,
            existingBytes = existingBytes,
            totalBytes = totalBytes
          )
        }
      }

      // Step 5: Finalize - rename temp to final
      if (!isActive) {
        repo.updateState(state.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      finalizeDownload(tempFile, finalFile, state)

      coordinator.processQueuedDownloads()
      return@withContext Result.success()

    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException || !isActive) {
        Timber.d("Download cancelled: $taskId")
        repo.updateState(state.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      Timber.e(e, "Download failed: $taskId")
      repo.updateState(state.copy(
        status = DownloadStatus.FAILED,
        error = e.message ?: "Unknown error"
      ))

      coordinator.processQueuedDownloads()
      return@withContext Result.failure(workDataOf(KEY_ERROR to e.message))
    }
  }

  /**
   * Download data with progress tracking
   */
  private suspend fun downloadData(
    input: InputStream,
    output: OutputStream,
    taskId: String,
    state: cloud.app.csplayer.download.DownloadState,
    existingBytes: Long,
    totalBytes: Long
  ) {
    val buffer = ByteArray(BUFFER_SIZE)
    var downloadedBytes = 0L
    var totalDownloaded = existingBytes
    var lastProgressTime = System.currentTimeMillis()
    var lastReportBytes = 0L

    while (kotlinx.coroutines.coroutineScope { isActive }) {
      val bytesRead = input.read(buffer)
      if (bytesRead == -1) break

      output.write(buffer, 0, bytesRead)
      downloadedBytes += bytesRead
      totalDownloaded += bytesRead

      // Update progress every second
      val now = System.currentTimeMillis()
      if (now - lastProgressTime >= PROGRESS_UPDATE_INTERVAL_MS) {
        val progress = if (totalBytes > 0) {
          ((totalDownloaded * 100) / totalBytes).toInt()
        } else 0

        val speed = ((downloadedBytes - lastReportBytes) * 1000) / (now - lastProgressTime)

        // Update state
        repo.updateState(state.copy(
          status = DownloadStatus.DOWNLOADING,
          downloadedBytes = totalDownloaded,
          totalBytes = if (totalBytes > 0) totalBytes else totalDownloaded,
          progress = progress,
          speed = speed
        ))

        // Update notification
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
          setForeground(createForegroundInfo(taskId, progress))
        } else {
          showProgressNotification(taskId, progress)
        }
        setProgress(workDataOf(KEY_PROGRESS to progress))

        lastProgressTime = now
        lastReportBytes = downloadedBytes
      }
    }

    output.flush()
  }

  private fun showProgressNotification(taskId: String, progress: Int) {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context,
      taskId,
      progress,
      isHttp = true
    )
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(taskId.hashCode(), notification)
  }

  /**
   * Finalize download - rename temp to final file
   */
  private suspend fun finalizeDownload(
    tempFile: File,
    finalFile: File,
    state: cloud.app.csplayer.download.DownloadState
  ) {
    if (!tempFile.exists()) {
      Timber.e("Temp file missing: ${tempFile.absolutePath}")
      throw IllegalStateException("Temp file not found")
    }

    // Rename temp to final
    if (finalFile.exists()) {
      finalFile.delete()
    }

    val renamed = tempFile.renameTo(finalFile)
    if (!renamed) {
      Timber.e("Failed to rename: ${tempFile.name} -> ${finalFile.name}")
      throw IllegalStateException("Failed to rename file")
    }

    Timber.i("✓ Download complete: ${finalFile.absolutePath}")
    Timber.i("File size: ${finalFile.length() / (1024 * 1024)} MB")

    // Update database
    val updatedTask = state.task.copy(
      fileName = finalFile.absolutePath
    )

    repo.updateState(state.copy(
      task = updatedTask,
      status = DownloadStatus.COMPLETED,
      downloadedBytes = finalFile.length(),
      totalBytes = finalFile.length(),
      progress = 100,
      speed = 0L,
      completedAt = System.currentTimeMillis()
    ))

    // Scan to MediaStore
    try {
      mediaStore.scanMedia(finalFile.absolutePath)
      Timber.d("Scanned to MediaStore: ${finalFile.name}")
    } catch (e: Exception) {
      Timber.w(e, "Failed to scan to MediaStore")
    }
  }

  /**
   * Extract filename from URL
   */
  private fun extractFileName(url: String): String {
    return try {
      val path = URL(url).path
      val fileName = path.substringAfterLast('/')
        .substringBefore('?')
        .takeIf { it.isNotBlank() }
        ?: "download_${System.currentTimeMillis()}"

      // Ensure valid filename
      fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    } catch (e: Exception) {
      "download_${System.currentTimeMillis()}"
    }
  }

  /**
   * Create foreground notification
   */
  private fun createForegroundInfo(taskId: String, progress: Int): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context,
      taskId,
      progress,
      isHttp = true
    )
    // For Android 14+ (API 34+), specify foreground service type
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ForegroundInfo(
        taskId.hashCode(),
        notification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      ForegroundInfo(taskId.hashCode(), notification)
    }
  }
}
