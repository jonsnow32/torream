package cloud.app.csplayer.download.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class HttpDownloadWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val repo: DownloadRepository,
  private val mediaStore: cloud.app.csplayer.media.dataSource.MediaStoreDataSource
) : CoroutineWorker(context, params) {

  companion object {
    const val KEY_TASK_ID = "task_id"
    const val KEY_PROGRESS = "progress"
    const val KEY_ERROR = "error"

    // Notification ID for foreground service
    private const val NOTIFICATION_ID = 1001
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

    Timber.d("HttpDownloadWorker started for taskId=$taskId")

    // Set as foreground to ensure long-running download
    setForeground(createForegroundInfo(taskId, 0))

    val state = repo.observeState(taskId).first() ?: return@withContext Result.failure(
      workDataOf(KEY_ERROR to "Task not found")
    )
    val task = state.task

    var connection: HttpURLConnection? = null
    var input: InputStream? = null
    var out: FileOutputStream? = null
    var currentState = state // Declare here so it's accessible in catch block

    try {
      val targetFile = File(task.targetPath)
      val tempFile = File(task.targetPath + ".part")
      tempFile.parentFile?.mkdirs()

      val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

      val url = URL(task.source)
      connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        if (existingBytes > 0L) {
          setRequestProperty("Range", "bytes=$existingBytes-")
        }
        connect()
      }

      val responseCode = connection.responseCode
      if (responseCode in 400..599) {
        val err = "HTTP error $responseCode"
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

      // Get filename for UI display from HTTP response or URL
      val fileName = extractFileName(connection, task.source)

      // Update task with filename and use this updated state going forward
      val updatedTask = task.copy(title = fileName)
      currentState = state.copy(task = updatedTask)

      val contentLengthHeaderStr = connection.getHeaderField("Content-Length")
      val contentLengthHeader = contentLengthHeaderStr?.toLongOrNull() ?: -1L

      val totalBytes = when {
        currentState.task.totalBytes > 0 -> currentState.task.totalBytes
        contentLengthHeader > 0 -> existingBytes + contentLengthHeader
        else -> -1L
      }

      // Update with DOWNLOADING status, keeping the updated task with title
      currentState = currentState.copy(status = DownloadStatus.DOWNLOADING)
      repo.updateState(currentState)

      input = connection.inputStream
      out = FileOutputStream(tempFile, true)

      val buffer = ByteArray(8 * 1024)
      var downloaded = 0L
      var lastReportTime = System.currentTimeMillis()
      var lastReportBytes = 0L

      while (isActive) {
        val read = input.read(buffer)
        if (read == -1) break
        out.write(buffer, 0, read)
        downloaded += read

        val now = System.currentTimeMillis()
        val elapsed = now - lastReportTime
        if (elapsed >= 1000) {
          val currentTotal = existingBytes + downloaded
          val progress = if (totalBytes > 0) {
            ((currentTotal * 100) / totalBytes).toInt()
          } else 0

          val speed = ((downloaded - lastReportBytes) * 1000 / elapsed)

          // Update current state (already has title) with new progress
          currentState = currentState.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = currentTotal,
            totalBytes = if (totalBytes > 0) totalBytes else currentTotal,
            progress = progress,
            speed = speed,
            downloadSpeedBytesPerSec = speed
          )

          // Update repository
          repo.updateState(currentState)

          // Update notification
          setForeground(createForegroundInfo(taskId, progress))

          // Update work progress
          setProgress(workDataOf(KEY_PROGRESS to progress))

          lastReportTime = now
          lastReportBytes = downloaded
        }
      }

      // Check if download was complete before closing
      if (!isActive) {
        // Use currentState which already has title
        repo.updateState(currentState.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      // Flush and close streams
      try {
        out.flush()
        out.close()
        input.close()
      } catch (e: Exception) {
        Timber.w("Error closing streams: ${e.message}")
      }

      connection.disconnect()

      Timber.d("Download completed, moving temp file to target: ${tempFile.absolutePath} -> ${targetFile.absolutePath}")

      // Ensure target file path is valid (not a directory)
      val finalTargetFile = if (targetFile.isDirectory || task.targetPath.endsWith("/")) {
        // Extract filename from URL
        val filename = task.source.substringAfterLast("/").substringBefore("?")
        File(targetFile, filename.ifBlank { taskId })
      } else {
        targetFile
      }

      // Ensure parent directory exists
      finalTargetFile.parentFile?.mkdirs()

      // Delete existing target file if it exists
      if (finalTargetFile.exists()) {
        Timber.d("Target file already exists, deleting: ${finalTargetFile.absolutePath}")
        finalTargetFile.delete()
      }

      // Try to move temp file to final location
      var success = false
      try {
        // First try rename (fast)
        success = tempFile.renameTo(finalTargetFile)
        if (success) {
          Timber.d("Successfully renamed temp file")
        } else {
          // Rename failed, try copy + delete (slower but more reliable)
          Timber.w("Rename failed, falling back to copy+delete")
          tempFile.copyTo(finalTargetFile, overwrite = true)
          tempFile.delete()
          success = finalTargetFile.exists()
          if (success) {
            Timber.d("Successfully copied temp file and deleted original")
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "Exception during file move")
        success = false
      }

      if (success && finalTargetFile.exists()) {
        val finalSize = finalTargetFile.length()
        Timber.d("Final file size: $finalSize bytes")

        // Update task with downloaded file path (currentState already has title)
        val updatedTask = currentState.task.copy(downloadedFilePath = finalTargetFile.absolutePath)

        repo.updateState(
          currentState.copy(
            task = updatedTask,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = finalSize,
            totalBytes = finalSize,
            progress = 100,
            speed = 0L,
            downloadSpeedBytesPerSec = 0L,
            completedAt = System.currentTimeMillis()
          )
        )

        // Scan file into MediaStore so it becomes visible in media library
        try {
          mediaStore.scanMedia(finalTargetFile.absolutePath)
          Timber.d("HTTP download: Scanned file into MediaStore: ${finalTargetFile.absolutePath}")
        } catch (e: Exception) {
          Timber.w(e, "HTTP download: Failed to scan file into MediaStore")
        }

        Timber.i("HTTP download completed: $taskId, saved to ${finalTargetFile.absolutePath}")
        return@withContext Result.success()
      } else {
        val err = "Failed to move temp file to final location. Temp: ${tempFile.absolutePath} (exists: ${tempFile.exists()}), Target: ${finalTargetFile.absolutePath} (exists: ${finalTargetFile.exists()})"
        Timber.e(err)
        // Use currentState which already has title
        repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

    } catch (e: Exception) {
      Timber.e(e, "HTTP download failed for taskId=$taskId")
      val err = e.message ?: "Unknown error"
      // Use currentState which may have title if error happened after filename extraction
      repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
      return@withContext Result.failure(workDataOf(KEY_ERROR to err))
    } finally {
      // Cleanup - only if not already closed
      try {
        out?.close()
      } catch (_: Exception) {}
      try {
        input?.close()
      } catch (_: Exception) {}
      try {
        connection?.disconnect()
      } catch (_: Exception) {}
    }
  }

  private fun createForegroundInfo(taskId: String, progress: Int): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context = context,
      taskId = taskId,
      progress = progress,
      isHttp = true
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

  /**
   * Extract filename from HTTP response Content-Disposition header or URL.
   * Priority: Content-Disposition > URL path > fallback
   */
  private fun extractFileName(connection: HttpURLConnection, sourceUrl: String): String {
    // Try to get filename from Content-Disposition header
    val contentDisposition = connection.getHeaderField("Content-Disposition")
    if (contentDisposition != null) {
      // Parse Content-Disposition header
      // Examples:
      // - attachment; filename="video.mp4"
      // - inline; filename*=UTF-8''video%20name.mp4
      val filenamePattern = """filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""".toRegex()
      val match = filenamePattern.find(contentDisposition)
      if (match != null) {
        val filename = match.groupValues[1]
        // Decode URL encoding if present
        return try {
          java.net.URLDecoder.decode(filename, "UTF-8")
        } catch (e: Exception) {
          filename
        }
      }
    }

    // Fallback: extract from URL
    val urlFilename = sourceUrl
      .substringAfterLast("/")
      .substringBefore("?")
      .substringBefore("#")

    // Return filename if valid, otherwise use generic name
    return if (urlFilename.isNotBlank() && urlFilename.contains(".")) {
      urlFilename
    } else {
      "download_${System.currentTimeMillis()}.mp4"
    }
  }
}

