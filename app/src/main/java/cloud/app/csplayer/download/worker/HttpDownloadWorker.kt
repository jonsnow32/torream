package cloud.app.csplayer.download.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.utils.KUniFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class HttpDownloadWorker @AssistedInject constructor(
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

    // Notification ID for foreground service
    private const val NOTIFICATION_ID = 1001
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

    Timber.d("HttpDownloadWorker started for taskId=$taskId")

    // Set as foreground to ensure long-running download
    setForeground(createForegroundInfo(taskId, 0))

    // Retry logic to wait for task to be available in database
    // This handles timing issues where worker starts before database write is committed
    var initialState: cloud.app.csplayer.download.DownloadState? = null
    var retries = 0
    while (initialState == null && retries < 20) {
      initialState = repo.observeState(taskId).first()
      if (initialState == null) {
        Timber.w("Task not yet in database, retry ${retries + 1}/20 for taskId=$taskId")
        delay(100L)
        retries++
      }
    }

    if (initialState == null) {
      val err = "Task not found in database after ${retries} retries: $taskId"
      Timber.e(err)
      return@withContext Result.failure(workDataOf(KEY_ERROR to err))
    }

    Timber.d("✓ Task loaded from database: $taskId")
    val task = initialState.task

    var connection: HttpURLConnection? = null
    var input: InputStream? = null
    var out: OutputStream? = null
    // Use non-null assertion since we've verified initialState is not null above
    var currentState: cloud.app.csplayer.download.DownloadState = initialState

    try {
      // targetPath should be an absolute file path (e.g., /storage/emulated/0/Movies)
      // but handle content URIs for backward compatibility
      val targetPath = task.targetPath
      val isSafUri = targetPath.startsWith("content://")

      // Use appropriate KUniFile constructor based on path type
      val targetFile = if (isSafUri) {
        KUniFile.fromUri(context, targetPath.toUri())
      } else {
        KUniFile.fromFile(context, File(targetPath))
      }

      if (targetFile == null) {
        val err = "Failed to create KUniFile from: $targetPath"
        Timber.e(err)
        repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

      // Determine if we need to create a file in a directory, or if target is already the file
      // Check if targetFile exists and is a directory, OR if it doesn't exist but looks like a directory path
      val isTargetDirectory = if (isSafUri) {
        targetFile.isDirectory
      } else {
        // For file paths, check if it exists as directory, or if it's a directory path (no extension)
        val file = File(targetPath)
        file.isDirectory || (!file.exists() && !targetPath.substringAfterLast("/").contains("."))
      }

      // Create the actual target file
      val actualTargetFile = if (isTargetDirectory) {
        val filename = task.source.substringAfterLast("/").substringBefore("?")
          .takeIf { it.isNotBlank() } ?: "download_${System.currentTimeMillis()}.mp4"
        val finalFileName = filename.ifBlank { taskId }

        // For SAF URIs, check if file already exists first
        if (isSafUri) {
          // Try to find existing file with the same name
          val existingFile = targetFile.listFiles()?.firstOrNull { it.name == finalFileName }
          if (existingFile != null) {
            Timber.d("File already exists, reusing: $finalFileName")
            existingFile
          } else {
            // Create new file
            targetFile.createFile(finalFileName, "application/octet-stream")
              ?: kotlin.run {
                val err = "Failed to create file in directory: $targetPath"
                Timber.e(err)
                repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
                return@withContext Result.failure(workDataOf(KEY_ERROR to err))
              }
          }
        } else {
          // For file paths, createFile will handle existing files
          targetFile.createFile(finalFileName, "application/octet-stream")
            ?: kotlin.run {
              val err = "Failed to create file in directory: $targetPath"
              Timber.e(err)
              repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
              return@withContext Result.failure(workDataOf(KEY_ERROR to err))
            }
        }
      } else {
        targetFile
      }

      // For SAF URIs, write directly to the file without temp file
      // For traditional paths, use temp file for safety
      val (downloadFile, tempPath) = if (isSafUri) {
        Pair(actualTargetFile, actualTargetFile.uri.toString())
      } else {
        // For file paths, check if we can write to the target location
        val filePath = actualTargetFile.filePath ?: actualTargetFile.uri.toString()
        val file = File(filePath)
        val parentDir = file.parentFile

        if (parentDir == null) {
          val err = "Parent directory is null for: ${file.absolutePath}"
          Timber.e(err)
          repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
          return@withContext Result.failure(workDataOf(KEY_ERROR to err))
        }

        // Check if parent directory exists and is writable
        // If not, we're probably trying to write to shared storage without permission
        if (!parentDir.exists() || !parentDir.canWrite()) {
          val err = "No write permission for directory: ${parentDir.absolutePath}\n" +
            "Please select a different download location in Settings or use default app storage."
          Timber.e(err)
          repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
          return@withContext Result.failure(workDataOf(KEY_ERROR to err))
        }

        // Create temp file in the parent directory with .part extension
        val tempFileName = "${file.name}.part"
        val tempFile = File(parentDir, tempFileName)
        val tempFilePath = tempFile.absolutePath

        // Create the temp file if it doesn't exist
        if (!tempFile.exists()) {
          try {
            tempFile.createNewFile()
            Timber.d("Created temp file: $tempFilePath")
          } catch (e: Exception) {
            val err = "Failed to create temp file: $tempFilePath - ${e.message}\n" +
              "This usually means the app doesn't have write permission to this location.\n" +
              "Please select a different download location in Settings."
            Timber.e(err)
            repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
            return@withContext Result.failure(workDataOf(KEY_ERROR to err))
          }
        }

        val tempKuniFile = KUniFile.fromFile(context, tempFile)
        if (tempKuniFile == null) {
          val err = "Failed to create KUniFile for temp file: $tempFilePath"
          Timber.e(err)
          repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
          return@withContext Result.failure(workDataOf(KEY_ERROR to err))
        }

        Pair(tempKuniFile, tempFilePath)
      }

      val existingBytes = if (downloadFile?.exists() == true) downloadFile.length() else 0L

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

      // Check if file is already complete (416 = Range Not Satisfiable)
      if (responseCode == 416 && existingBytes > 0) {
        Timber.i("File already fully downloaded (HTTP 416), marking as completed: ${actualTargetFile.name}")

        // Get filename for UI display
        val fileName = extractFileName(connection, task.source)
        val finalTargetPath = actualTargetFile.filePath ?: actualTargetFile.uri.toString()
        val updatedTask = task.copy(
          title = fileName,
          downloadedFilePath = finalTargetPath
        )

        // Mark as completed
        repo.updateState(
          currentState.copy(
            task = updatedTask,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = existingBytes,
            totalBytes = existingBytes,
            progress = 100,
            speed = 0L,
            completedAt = System.currentTimeMillis()
          )
        )

        // Scan to MediaStore if it's a file path
        try {
          if (finalTargetPath.startsWith("/")) {
            mediaStore.scanMedia(finalTargetPath)
            Timber.d("Scanned existing file into MediaStore: $finalTargetPath")
          }
        } catch (e: Exception) {
          Timber.w(e, "Failed to scan existing file into MediaStore")
        }

        // Process queued downloads
        try {
          coordinator.processQueuedDownloads()
        } catch (e: Exception) {
          Timber.w(e, "Failed to process queued downloads")
        }

        return@withContext Result.success()
      }

      if (responseCode in 400..599) {
        val err = "HTTP error $responseCode"
        repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

      // Get filename for UI display from HTTP response or URL
      val fileName = extractFileName(connection, task.source)

      // Update task with filename and use this updated state going forward
      val updatedTask = task.copy(title = fileName)
      currentState = currentState.copy(task = updatedTask)

      val contentLengthHeaderStr = connection.getHeaderField("Content-Length")
      val contentLengthHeader = contentLengthHeaderStr?.toLongOrNull() ?: -1L

      val totalBytes = when {
        currentState.task.totalBytes > 0 -> currentState.task.totalBytes
        contentLengthHeader > 0 -> existingBytes + contentLengthHeader
        else -> -1L
      }

      // Additional check: if existing bytes equals or exceeds total, file is complete
      if (existingBytes > 0 && totalBytes > 0 && existingBytes >= totalBytes) {
        Timber.i("File already fully downloaded (size check), marking as completed: ${actualTargetFile.name}")

        val finalTargetPath = actualTargetFile.filePath ?: actualTargetFile.uri.toString()
        val updatedTaskWithPath = updatedTask.copy(downloadedFilePath = finalTargetPath)

        // Mark as completed
        repo.updateState(
          currentState.copy(
            task = updatedTaskWithPath,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = existingBytes,
            totalBytes = totalBytes,
            progress = 100,
            speed = 0L,
            completedAt = System.currentTimeMillis()
          )
        )

        // Scan to MediaStore if it's a file path
        try {
          if (finalTargetPath.startsWith("/")) {
            mediaStore.scanMedia(finalTargetPath)
            Timber.d("Scanned existing file into MediaStore: $finalTargetPath")
          }
        } catch (e: Exception) {
          Timber.w(e, "Failed to scan existing file into MediaStore")
        }

        // Process queued downloads
        try {
          coordinator.processQueuedDownloads()
        } catch (e: Exception) {
          Timber.w(e, "Failed to process queued downloads")
        }

        return@withContext Result.success()
      }

      // Update with DOWNLOADING status, keeping the updated task with title
      currentState = currentState.copy(status = DownloadStatus.DOWNLOADING)
      repo.updateState(currentState)

      input = connection.inputStream
      out = try {
        // Ensure download file actually exists before opening output stream
        if (downloadFile == null || !downloadFile.exists()) {
          Timber.e("Download file does not exist: $tempPath, exists=${downloadFile?.exists()}")
          null
        } else {
          downloadFile.openOutputStream(append = existingBytes > 0)
        }
      } catch (e: Exception) {
        Timber.e(e, "Failed to open output stream for download file")
        null
      }

      if (out == null) {
        val err = "Failed to open output stream for: $tempPath"
        Timber.e(err)
        repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))
        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

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
            speed = speed
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

      Timber.d("Download completed for: $targetPath")

      // For SAF URIs, the file was written directly; for traditional paths, move temp to final
      var success = false

      try {
        if (!isSafUri) {
          // Traditional file path - move temp file to final location
          Timber.d("Moving temp file to target: $tempPath -> $targetPath")

          val tempKuniFile = KUniFile.fromFile(context, File(tempPath))

          if (tempKuniFile != null && tempKuniFile.exists()) {
            // Delete target if it exists
            if (actualTargetFile.exists()) {
              actualTargetFile.delete()
            }

            // Copy content from temp to final
            val inputStream = tempKuniFile.openInputStream()
            val outputStream = actualTargetFile.openOutputStream(append = false)

            inputStream.use { inStream ->
              outputStream.use { outStream ->
                inStream.copyTo(outStream)
              }
            }

            // Delete temp file
            tempKuniFile.delete()
            success = actualTargetFile.exists()
            if (success) {
              Timber.d("Successfully moved file to $targetPath")
            }
          } else {
            Timber.w("Temp file does not exist: $tempPath")
          }
        } else {
          // For SAF URIs, file was written directly
          success = actualTargetFile.exists()
          if (success) {
            Timber.d("File successfully written to SAF URI")
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "Exception during file finalization")
        success = false
      }

      if (success && actualTargetFile.exists()) {
        val finalSize = actualTargetFile.length()
        // Use absolute file path instead of URI for consistency with MediaStore
        val finalTargetPath = actualTargetFile.filePath ?: actualTargetFile.uri.toString()
        Timber.d("Final file size: $finalSize bytes at path: $finalTargetPath")

        // Update task with downloaded file path (currentState already has title)
        val updatedTask = currentState.task.copy(downloadedFilePath = finalTargetPath)

        repo.updateState(
          currentState.copy(
            task = updatedTask,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = finalSize,
            totalBytes = finalSize,
            progress = 100,
            speed = 0L,
            completedAt = System.currentTimeMillis()
          )
        )

        // Scan file into MediaStore (finalTargetPath is now always a file path)
        try {
          if (finalTargetPath.startsWith("/")) {
            mediaStore.scanMedia(finalTargetPath)
            Timber.d("HTTP download: Scanned file into MediaStore: $finalTargetPath")
          } else {
            Timber.d("HTTP download: Completed on SAF URI (skip MediaStore scan): $finalTargetPath")
          }
        } catch (e: Exception) {
          Timber.w(e, "HTTP download: Failed to scan file into MediaStore")
        }

        Timber.i("HTTP download completed: $taskId, saved to ${actualTargetFile.uri}")

        // Process any queued downloads now that this one is complete
        try {
          coordinator.processQueuedDownloads()
        } catch (e: Exception) {
          Timber.w(e, "Failed to process queued downloads after completion")
        }

        return@withContext Result.success()
      } else {
        val err = "Failed to finalize download. Target exists: ${actualTargetFile.exists()}"
        Timber.e(err)
        // Use currentState which already has title
        repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))

        // Process any queued downloads even on failure
        try {
          coordinator.processQueuedDownloads()
        } catch (_: Exception) {
          Timber.w("Failed to process queued downloads after failure")
        }

        return@withContext Result.failure(workDataOf(KEY_ERROR to err))
      }

    } catch (e: Exception) {
      // Check if this is a cancellation (expected when user pauses)
      if (e is kotlinx.coroutines.CancellationException || !isActive) {
        Timber.d("HTTP download cancelled for taskId=$taskId")
        // Keep the status as PAUSED, don't mark as FAILED
        try {
          if (currentState.status != DownloadStatus.PAUSED) {
            repo.updateState(currentState.copy(status = DownloadStatus.PAUSED, error = null))
          }
        } catch (repoError: Exception) {
          Timber.e(repoError, "Failed to update state for cancelled download")
        }
        return@withContext Result.retry()
      }

      // Real error - not a cancellation
      Timber.e(e, "HTTP download failed for taskId=$taskId")
      val err = e.message ?: "Unknown error"
      // Use currentState which may have title if error happened after filename extraction
      repo.updateState(currentState.copy(status = DownloadStatus.FAILED, error = err))

      // Process any queued downloads even on failure
      try {
        coordinator.processQueuedDownloads()
      } catch (_: Exception) {
        Timber.w("Failed to process queued downloads after failure")
      }

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
        } catch (_: Exception) {
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

