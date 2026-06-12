package cloud.streamless.torream.download.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.streamless.torream.download.DownloadCoordinator
import cloud.streamless.torream.download.DownloadRepository
import cloud.streamless.torream.download.DownloadStatus
import cloud.streamless.torream.media.dataSource.MediaStoreDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.app.NotificationManager
import androidx.core.net.toUri
import cloud.streamless.torream.download.DownloadState
import cloud.streamless.torream.utils.UnifiedFile
import cloud.streamless.torream.utils.UnifiedFileFactory

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

  lateinit var targetDir: UnifiedFile

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

    Timber.i("HttpDownloadWorker started: $taskId")
    setForeground(createForegroundInfo(taskId, 0))

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

    var task = state.task
    Timber.d("Downloading from: ${task.source}")
    Timber.d("Save to: ${task.targetPath}")

    try {

      Timber.i("targetPath file: ${task.targetPath}")
      // Step 1: Prepare download file
      targetDir = UnifiedFileFactory.fromUri(context, task.targetPath.toUri()) ?: run {
        val error = "Invalid target directory"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      val tempFileName = "${extractFileName(task.fileName ?: task.source)}.part"
      val tempFile = targetDir.createFile(tempFileName)

      Timber.i("Temp file: ${tempFile?.uri}")
      Timber.i("targetDir file: ${targetDir.uri}")

      // Keep targetPath pointing to the directory, don't change it to the temp file
      // The fileName will be updated after download completes
      task = task.copy(fileName = tempFileName)
      state = state.copy(task = task)
      repo.updateState(state)

      // Step 2: Open HTTP connection
      val url = URL(task.source)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 15000
      connection.readTimeout = 15000

      // Apply custom headers from task if provided
      task.headers?.forEach { (key, value) ->
        connection.setRequestProperty(key, value)
        Timber.d("Added header: $key = $value")
      }

      if (tempFile == null) {
        val error = "Failed to access download files"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      // Resume support
      val existingBytes = try {
        if (tempFile.exists()) {
          tempFile.length()
        } else {
          0L
        }
      } catch (e: Exception) {
        Timber.w(e, "Failed to check temp file size, starting fresh download")
        0L
      }

      if (existingBytes > 0) {
        connection.setRequestProperty("Range", "bytes=$existingBytes-")
        Timber.i("Resuming from byte: $existingBytes")
      }

      connection.connect()

      val responseCode = connection.responseCode
      Timber.d("HTTP Response: $responseCode")

      // Check if already complete (416 Range Not Satisfiable)
      if (responseCode == 416 && existingBytes > 0) {
        Timber.i("File already complete (HTTP 416)")
        // Need to get finalFileName first, so we'll handle this after extracting filename
        // For now, treat this as complete with temp file name
        val tempName = extractFinalFileName(connection, task.source)
        finalizeDownload(tempFile, tempName, state)
        return@withContext Result.success()
      }

      // Handle other errors
      if (responseCode in 400..599) {
        val error = "HTTP error: $responseCode"
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      // Step 3: Get file size and fileName
      val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
      val totalBytes = if (contentLength > 0) {
        existingBytes + contentLength
      } else {
        -1L
      }

      // Extract final filename from Content-Disposition header or URL
      val tempName = extractFinalFileName(connection, task.source)


      Timber.i("Temp filename: $tempName")
      Timber.i("Total size: ${totalBytes / (1024 * 1024)} MB")


      // Update notification with resolved filename before download starts
      setForeground(createForegroundInfo(taskId, 0, tempName))

      // Step 4: Download data
      connection.inputStream.use { input ->
        tempFile.openOutputStream(existingBytes > 0).use { output ->
          downloadData(
            input = input,
            output = output,
            taskId = taskId,
            state = state,
            existingBytes = existingBytes,
            totalBytes = totalBytes,
            fileName = tempName
          )
        }
      }

      // Step 5: Finalize - rename temp to final
      if (!isActive) {
        repo.updateState(state.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      finalizeDownload(tempFile, tempName, state)

      coordinator.processQueuedDownloads()
      return@withContext Result.success()

    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException || !isActive) {
        Timber.d("Download cancelled: $taskId")
        repo.updateState(state.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      Timber.e(e, "Download failed: $taskId")
      repo.updateState(
        state.copy(
          status = DownloadStatus.FAILED,
          error = e.message ?: "Unknown error"
        )
      )

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
    state: DownloadState,
    existingBytes: Long,
    totalBytes: Long,
    fileName: String? = null
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
        repo.updateState(
          state.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = totalDownloaded,
            totalBytes = if (totalBytes > 0) totalBytes else totalDownloaded,
            progress = progress,
            speed = speed
          )
        )

        // Update notification without re-promoting the foreground service
        showProgressNotification(taskId, progress, fileName)
        setProgress(workDataOf(KEY_PROGRESS to progress))

        lastProgressTime = now
        lastReportBytes = downloadedBytes
      }
    }

    output.flush()
  }

  private fun showProgressNotification(taskId: String, progress: Int, fileName: String? = null) {
    // Check if permission is granted before showing notification
    if (!DownloadNotificationHelper.hasNotificationPermission(context)) {
      Timber.d("Notification permission not granted, skipping notification for taskId=$taskId")
      // Try to request permission if we can access MainActivity
      try {
        val activity = android.app.ActivityManager::class.java
        // We're in a background worker, can't directly request permissions
        // The permission will be requested when user opens the app
      } catch (e: Exception) {
        Timber.w(e, "Could not request notification permission from worker")
      }
      return
    }

    val notification = DownloadNotificationHelper.createDownloadNotification(
      context,
      taskId,
      progress,
      isHttp = true,
      fileName = fileName
    )
    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(taskId.hashCode(), notification)
    DownloadNotificationHelper.postSummary(context)
  }

  /**
   * Finalize download - rename temp to final file
   */
  private suspend fun finalizeDownload(
    tempFile: UnifiedFile,
    tempName: String,
    state: DownloadState
  ) {
    if (!tempFile.exists()) {
      Timber.e("Temp file missing: ${tempFile.uri}")
      throw IllegalStateException("Temp file not found")
    }

    val renamed = tempFile.renameTo(tempName)
    if (!renamed) {
      Timber.e("Failed to rename: ${tempFile.name} -> $tempName")
      throw IllegalStateException("Failed to rename file")
    }

    Timber.i("✓ File renamed: ${tempFile.name} -> $tempName")
    Timber.i("✓ Download complete: ${tempFile.uri}")

    val fileSize = try {
      tempFile.length()
    } catch (e: Exception) {
      Timber.w(e, "Failed to get file size after rename")
      0L
    }
    Timber.i("File size: ${fileSize / (1024 * 1024)} MB")

    val finalUri = tempFile.uri.toString().trim()
    Timber.d("DEBUG: finalUri = '$finalUri'")

    // Don't validate URI format here - it may vary based on storage backend
    // Just keep targetPath as the directory and store fileName with final name
    val updatedTask = state.task.copy(
      fileName = tempFile.name,
      targetPath = tempFile.uri.toString()
    )


    repo.updateState(
      state.copy(
        task = updatedTask,
        status = DownloadStatus.COMPLETED,
        downloadedBytes = fileSize,
        totalBytes = fileSize,
        progress = 100,
        speed = 0L,
        completedAt = System.currentTimeMillis()
      )
    )

    Timber.d("Saving to database - fileName=${updatedTask.fileName}, targetPath=${updatedTask.targetPath} totalBytes=$fileSize")

    try {
      mediaStore.scanMedia(finalUri)
      Timber.d("Scanned to MediaStore: $tempName")
    } catch (exception: Exception) {
      Timber.w(exception, "Failed to scan to MediaStore")
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
    } catch (_: Exception) {
      "download_${System.currentTimeMillis()}"
    }
  }

  /**
   * Extract final filename from HTTP headers or URL
   * Checks Content-Disposition header first, then falls back to URL path
   */
  private fun extractFinalFileName(connection: HttpURLConnection, url: String): String {
    return try {
      // Try to get filename from Content-Disposition header
      val contentDisposition = connection.getHeaderField("Content-Disposition")
      if (contentDisposition != null) {
        // Parse: attachment; filename="example.mp4"
        val fileNameMatch = Regex("""filename\*?=["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE)
          .find(contentDisposition)
        if (fileNameMatch != null) {
          val fileName = fileNameMatch.groupValues[1]
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")

          // Handle RFC 5987 encoding (filename*=UTF-8''example.mp4)
          val decodedName = if (fileName.contains("''")) {
            java.net.URLDecoder.decode(fileName.substringAfter("''"), "UTF-8")
          } else {
            fileName
          }

          if (decodedName.isNotBlank()) {
            return sanitizeFileName(decodedName)
          }
        }
      }

      // Fallback to URL path
      sanitizeFileName(extractFileName(url))
    } catch (e: Exception) {
      Timber.w(e, "Failed to extract filename from headers")
      sanitizeFileName(extractFileName(url))
    }
  }

  /**
   * Sanitize filename to ensure it's valid for filesystem
   */
  private fun sanitizeFileName(fileName: String): String {
    return fileName
      .replace(Regex("[^a-zA-Z0-9._-]"), "_")
      .take(255) // Max filename length on most filesystems
      .ifBlank { "download_${System.currentTimeMillis()}" }
  }

  /**
   * Create foreground notification
   */
  private fun createForegroundInfo(taskId: String, progress: Int, fileName: String? = null): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context,
      taskId,
      progress,
      isHttp = true,
      fileName = fileName
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
