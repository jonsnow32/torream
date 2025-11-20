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

@HiltWorker
class TorrentDownloadWorker @AssistedInject constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val repo: DownloadRepository,
  private val mediaStore: cloud.app.csplayer.media.dataSource.MediaStoreDataSource
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

      // Prepare save directory - create unique subdirectory for each torrent
      val baseDir = File(task.targetPath).let { if (it.isDirectory) it else it.parentFile ?: it }

      // Create subdirectory based on taskId to avoid conflicts
      // This ensures each torrent has its own directory
      val saveDir = File(baseDir, taskId)
      if (!saveDir.exists()) {
        saveDir.mkdirs()
        Timber.d("Created torrent save directory: ${saveDir.absolutePath}")
      }

      // Add torrent to session
      val ti = when {
        task.source.startsWith("magnet:", ignoreCase = true) -> {
          // Fetch metadata for magnet link
          Timber.d("Starting magnet metadata fetch for taskId=$taskId")
          repo.updateState(state.copy(status = DownloadStatus.QUEUED, error = null))

          val tmp = File(saveDir, "magnet_tmp_${taskId}").apply {
            if (exists()) {
              deleteRecursively() // Clean up any stale temp files
            }
            mkdirs()
          }
          Timber.d("Temp directory created: ${tmp.absolutePath}")

          var meta: ByteArray? = null
          // Use more attempts with shorter timeouts to stay within WorkManager's limits
          // and give DHT more time to bootstrap between attempts
          val maxAttempts = 6
          val perAttemptTimeoutSeconds = 10 // Shorter per-attempt timeout

          repo.updateState(
            state.copy(
              status = DownloadStatus.QUEUED,
              error = "Connecting to DHT network and finding peers..."
            )
          )

          // Optimized DHT bootstrap - wait smartly
          var dhtNodes = 0L
          var bootstrapWaitTime = 0L
          val maxBootstrapWait = 10000L // Max 10s instead of 15s

          while (dhtNodes == 0L && bootstrapWaitTime < maxBootstrapWait && isActive) {
            delay(1000L)
            bootstrapWaitTime += 1000L

            try {
              dhtNodes = s.stats().dhtNodes()
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
            return@withContext Result.retry()
          }

          // Final DHT check
          try {
            val stats = s.stats()
            val dhtNodes = stats.dhtNodes()
            Timber.d("DHT Status before metadata fetch: $dhtNodes nodes connected")

            if (dhtNodes == 0L) {
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
              return@withContext Result.retry()
            }

            try {
              Timber.d("Magnet fetch attempt $attempt/$maxAttempts (timeout: ${perAttemptTimeoutSeconds}s)")

              repo.updateState(
                state.copy(
                  status = DownloadStatus.QUEUED,
                  error = "Fetching metadata from peers (attempt $attempt/$maxAttempts)..."
                )
              )

              meta = s.fetchMagnet(task.source, perAttemptTimeoutSeconds, tmp)

              if (meta != null) {
                Timber.i("Magnet metadata fetched successfully on attempt $attempt (${meta.size} bytes)")
                break
              }
              Timber.w("Magnet fetch attempt $attempt returned null metadata (no peers responded)")
            } catch (t: Throwable) {
              Timber.w("Magnet fetch attempt $attempt failed: ${t.javaClass.simpleName} - ${t.message}")
            }

            // Wait before retry with shorter delays to stay within time budget
            if (attempt < maxAttempts) {
              val delayMs = 2000L // Fixed 2s delay between attempts
              Timber.d("Waiting ${delayMs}ms before retry")

              if (!isActive) {
                Timber.d("Worker cancelled during retry wait")
                return@withContext Result.retry()
              }

              delay(delayMs)
            }
          }

          if (meta == null) {
            val err = "Unable to download torrent metadata. This magnet link may be dead (no peers found) or your network may be blocking torrent connections. Try a different magnet link or check your network settings."
            Timber.e("Magnet fetch failed for taskId=$taskId after $maxAttempts attempts")
            repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
            return@withContext Result.failure(workDataOf(KEY_ERROR to err))
          }

          // Clean up temp directory after successful fetch
          try {
            tmp.deleteRecursively()
          } catch (e: Exception) {
            Timber.w("Failed to clean up temp directory: ${e.message}")
          }

          Timber.d("Decoding magnet metadata (${meta.size} bytes)")
          TorrentInfo.bdecode(meta)
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
          // Assume it's an info hash
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
      while (isActive) {
        val status = h.status()
        val progress = (status.progress() * 100).roundToInt()
        val totalBytes = status.totalDone()
        val downloadRate = status.downloadRate().toLong()
        val uploadRate = status.uploadRate().toLong()

        // Fetch current state for accurate updates
        currentState = repo.observeState(taskId).first() ?: currentState

        repo.updateState(
          currentState.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = totalBytes,
            totalBytes = ti.totalSize(),
            progress = progress,
            speed = downloadRate,
            uploadSpeed = uploadRate,
            downloadSpeedBytesPerSec = downloadRate
          )
        )

        setForeground(createForegroundInfo(taskId, progress))
        setProgress(workDataOf(KEY_PROGRESS to progress))

        // Check if complete
        if (status.state() == TorrentStatus.State.SEEDING || status.isFinished) {
          currentState = repo.observeState(taskId).first() ?: currentState

          // For torrents, just save the directory path
          // The play logic will search for video files in this directory
          val downloadedFilePath = saveDir.absolutePath

          Timber.i("Torrent completed, saved to directory: $downloadedFilePath")

          // Update task with downloaded directory path
          val updatedTaskWithFile = currentState.task.copy(downloadedFilePath = downloadedFilePath)

          repo.updateState(
            currentState.copy(
              task = updatedTaskWithFile,
              status = DownloadStatus.COMPLETED,
              downloadedBytes = ti.totalSize(),
              totalBytes = ti.totalSize(),
              progress = 100,
              speed = 0L,
              uploadSpeed = uploadRate,
              downloadSpeedBytesPerSec = 0L,
              completedAt = System.currentTimeMillis()
            )
          )

          // Scan all video files in torrent directory into MediaStore
          try {
            val videoFiles = saveDir.walkTopDown()
              .filter { it.isFile }
              .filter { file ->
                val extension = file.extension.lowercase()
                extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
              }
              .toList()

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

          Timber.i("Torrent download completed: $taskId")
          return@withContext Result.success()
        }

        // Check for errors - libtorrent4j doesn't have hasError() method
        // We'll rely on exceptions and state checks instead

        delay(POLL_INTERVAL_MS)
      }

      // Worker was cancelled - fetch current state
      currentState = repo.observeState(taskId).first() ?: currentState
      repo.updateState(currentState.copy(status = DownloadStatus.PAUSED))
      return@withContext Result.retry()

    } catch (e: Exception) {
      val err = e.message ?: "Unknown error"
      Timber.e(e, "Torrent download failed for taskId=$taskId - Error: $err")
      try {
        repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
      } catch (repoError: Exception) {
        Timber.e(repoError, "Failed to update state for failed download")
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

  private fun createForegroundInfo(taskId: String, progress: Int): ForegroundInfo {
    val notification = DownloadNotificationHelper.createDownloadNotification(
      context = context,
      taskId = taskId,
      progress = progress,
      isHttp = false
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
}

