package com.tv.apps.zippy.download.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tv.apps.zippy.download.DownloadCoordinator
import com.tv.apps.zippy.download.DownloadRepository
import com.tv.apps.zippy.download.DownloadState
import com.tv.apps.zippy.download.DownloadStatus
import com.tv.apps.zippy.media.dataSource.MediaStoreDataSource
import com.tv.apps.zippy.utils.UnifiedFile
import com.tv.apps.zippy.utils.UnifiedFileFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * HLS Download Worker
 * Downloads HLS/M3U8 streams by:
 * 1. Parsing the M3U8 playlist
 * 2. Downloading all segments
 * 3. Concatenating segments into a single file
 * 4. Cleaning up temporary files
 */
@HiltWorker
class HlsDownloadWorker @AssistedInject constructor(
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
  private var totalSegments = 0
  private var downloadedSegments = 0
  private var lastProgressTime = System.currentTimeMillis()

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

    Timber.i("HlsDownloadWorker started: $taskId")
    if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      setForeground(createForegroundInfo(taskId, 0))
    } else {
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

    var task = state.task
    Timber.d("Downloading HLS from: ${task.source}")
    Timber.d("Save to: ${task.targetPath}")

    try {
      // Step 1: Prepare download directory
      targetDir = UnifiedFileFactory.fromUri(context, task.targetPath.toUri()) ?: run {
        val error = "Invalid target directory"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      // Step 2: Download and parse M3U8 playlist
      val playlistUrl = task.source
      val fileName = task.fileName ?: extractFileName(playlistUrl)
      val segments = downloadAndParsePlaylist(playlistUrl, task.headers)
      if (segments.isEmpty()) {
        val error = "No segments found in HLS playlist"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      totalSegments = segments.size
      Timber.i("Found $totalSegments segments in HLS playlist")

      // Step 3: Create output file for concatenated segments
      val outputFileName = "${fileName}.mp4"
      val outputFile = targetDir.createFile(outputFileName, "video/mp4") ?: run {
        val error = "Failed to create output file: $outputFileName"
        Timber.e(error)
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = error))
        return@withContext Result.failure(workDataOf(KEY_ERROR to error))
      }

      task = task.copy(fileName = outputFile.name)
      state = state.copy(task = task)
      repo.updateState(state)

      // Step 4: Download and concatenate all segments
      val totalBytes = downloadAndConcatenateSegments(
        segments = segments,
        outputFile = outputFile,
        taskId = taskId,
        state = state,
        headers = task.headers
      )

      // Step 5: Mark as complete - proceed regardless of cancellation status
      // (cancellation will be caught in the outer try-catch)

      val finalUri = outputFile.uri.toString()
      repo.updateState(
        state.copy(
          task = task.copy(
            fileName = outputFile.name,
            targetPath = finalUri
          ),
          status = DownloadStatus.COMPLETED,
          downloadedBytes = totalBytes,
          totalBytes = totalBytes,
          progress = 100,
          speed = 0L,
          completedAt = System.currentTimeMillis()
        )
      )

      try {
        mediaStore.scanMedia(finalUri)
        Timber.d("Scanned to MediaStore: ${outputFile.name}")
      } catch (e: Exception) {
        Timber.w(e, "Failed to scan to MediaStore")
      }

      coordinator.processQueuedDownloads()
      return@withContext Result.success()

    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException || !isActive) {
        Timber.d("Download cancelled: $taskId")
        repo.updateState(state.copy(status = DownloadStatus.PAUSED))
        return@withContext Result.retry()
      }

      Timber.e(e, "HLS download failed: $taskId")
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
   * Download and parse M3U8 playlist
   * Returns list of segment URLs
   */
  private fun downloadAndParsePlaylist(
    playlistUrl: String,
    headers: Map<String, String>?
  ): List<String> {
    return try {
      val url = URL(playlistUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 15000
      connection.readTimeout = 15000

      // Apply default User-Agent if not provided
      val headersToUse = mutableMapOf<String, String>()
      // Override with custom headers if provided
      headers?.forEach { (key, value) ->
        headersToUse[key] = value
      }

      // Apply all headers
      headersToUse.forEach { (key, value) ->
        connection.setRequestProperty(key, value)
        Timber.d("Header: $key = $value")
      }

      connection.connect()

      if (connection.responseCode != 200) {
        Timber.e("Failed to fetch playlist: HTTP ${connection.responseCode}")
        Timber.d("Response headers: ${connection.headerFields}")
        return emptyList()
      }

      // Read playlist content
      val playlistContent = connection.inputStream.bufferedReader().use { it.readText() }
      connection.disconnect()

      Timber.d("Playlist content length: ${playlistContent.length} bytes")

      // Parse M3U8 format
      parseM3U8Playlist(playlistContent, playlistUrl)
    } catch (e: Exception) {
      Timber.e(e, "Failed to download/parse playlist")
      emptyList()
    }
  }

  /**
   * Parse M3U8 playlist file
   * Extracts segment URLs from the playlist
   */
  private fun parseM3U8Playlist(content: String, baseUrl: String): List<String> {
    val segments = mutableListOf<String>()
    val baseUri = URI(baseUrl)

    content.lines().forEach { line ->
      val trimmedLine = line.trim()

      // Skip comments and empty lines
      if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
        return@forEach
      }

      // Handle relative and absolute URLs
      val segmentUrl = when {
        trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://") -> trimmedLine
        trimmedLine.startsWith("/") -> "${baseUri.scheme}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$trimmedLine"
        else -> {
          // Relative URL - resolve relative to playlist URL
          val basePath = baseUri.path.substringBeforeLast('/')
          "${baseUri.scheme}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$basePath/$trimmedLine"
        }
      }

      segments.add(segmentUrl)
    }

    return segments
  }

  /**
   * Download all segments and concatenate them into output file
   */
  private suspend fun downloadAndConcatenateSegments(
    segments: List<String>,
    outputFile: UnifiedFile,
    taskId: String,
    state: DownloadState,
    headers: Map<String, String>?
  ): Long {
    var totalBytesWritten = 0L
    val buffer = ByteArray(BUFFER_SIZE)

    outputFile.openOutputStream(false).use { output ->
      segments.forEachIndexed { index, segmentUrl ->

        try {
          val url = URL(segmentUrl)
          val connection = url.openConnection() as HttpURLConnection
          connection.requestMethod = "GET"
          connection.connectTimeout = 15000
          connection.readTimeout = 15000

          // Apply default User-Agent if not provided
          val headersToUse = mutableMapOf<String, String>()
          headersToUse["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
          headersToUse["Accept"] = "*/*"
          headersToUse["Accept-Language"] = "en-US,en;q=0.9"
          headersToUse["Accept-Encoding"] = "gzip, deflate"
          headersToUse["Connection"] = "keep-alive"

          // Override with custom headers if provided
          headers?.forEach { (key, value) ->
            headersToUse[key] = value
          }

          // Apply all headers
          headersToUse.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
          }

          connection.connect()

          if (connection.responseCode != 200) {
            Timber.w("Failed to download segment $index: HTTP ${connection.responseCode}")
            return@forEachIndexed
          }

          // Download segment and write to output
          connection.inputStream.use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
              totalBytesWritten += bytesRead
            }
          }

          downloadedSegments++

          // Update progress
          val now = System.currentTimeMillis()
          if (now - lastProgressTime >= PROGRESS_UPDATE_INTERVAL_MS) {
            val progress = ((downloadedSegments * 100) / totalSegments).coerceIn(0, 99)

            repo.updateState(
              state.copy(
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = totalBytesWritten,
                progress = progress
              )
            )

            // Only show notification in background - don't call setForeground during download loop
            // as it can conflict with worker completion
            if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
              showProgressNotification(taskId, progress)
            }

            setProgress(workDataOf(KEY_PROGRESS to progress))
            lastProgressTime = now
          }

          connection.disconnect()
        } catch (e: Exception) {
          Timber.e(e, "Failed to download segment $index: ${segmentUrl.substringAfterLast('/')}")
        }
      }

      output.flush()
    }

    Timber.i("Downloaded and concatenated all $downloadedSegments segments, total size: ${totalBytesWritten / (1024 * 1024)} MB")
    return totalBytesWritten
  }

  /**
   * Extract filename from M3U8 URL
   */
  private fun extractFileName(url: String): String {
    return try {
      val path = URL(url).path
      path.substringAfterLast('/')
        .substringBefore('?')
        .removeSuffix(".m3u8")
        .takeIf { it.isNotBlank() }
        ?.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        ?.take(200)
        ?: "hls_download_${System.currentTimeMillis()}"
    } catch (e: Exception) {
      Timber.e(e, "Failed to extract filename")
      "hls_download_${System.currentTimeMillis()}"
    }
  }

  /**
   * Show progress notification
   */
  private suspend fun showProgressNotification(taskId: String, progress: Int) {
    if (!DownloadNotificationHelper.hasNotificationPermission(context)) {
      Timber.d("Notification permission not granted, skipping notification for taskId=$taskId")
      return
    }

    try {
      val state = repo.observeState(taskId).first()
      val fileName = state?.task?.fileName

      val notification = DownloadNotificationHelper.createDownloadNotification(
        context,
        taskId,
        progress,
        isHttp = false,
        fileName = fileName
      )
      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      notifyUser(notificationManager, taskId, notification)
    } catch (e: Exception) {
      Timber.w(e, "Failed to show notification for taskId=$taskId")
    }
  }

  private fun notifyUser(
    notificationManager: NotificationManager,
    taskId: String,
    notification: android.app.Notification
  ) {
    try {
      @Suppress("MissingPermission")
      notificationManager.notify(taskId.hashCode(), notification)
    } catch (e: Exception) {
      Timber.w(e, "Failed to notify user")
    }
  }

  /**
   * Create foreground notification
   */
  private suspend fun createForegroundInfo(taskId: String, progress: Int): ForegroundInfo {
    @Suppress("MissingPermission")
    val checkPerm = !DownloadNotificationHelper.hasNotificationPermission(context)

    // Permission already checked before calling this
    if (checkPerm) {
      // Return a dummy ForegroundInfo if permission not granted
      return ForegroundInfo(taskId.hashCode(), android.app.Notification())
    }

    val state = repo.observeState(taskId).first()
    val fileName = state?.task?.fileName

    @Suppress("MissingPermission")
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context,
      taskId,
      progress,
      isHttp = false,
      fileName = fileName
    )

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

