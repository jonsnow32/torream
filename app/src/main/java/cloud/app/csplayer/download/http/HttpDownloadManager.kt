package cloud.app.csplayer.download.http

import cloud.app.csplayer.download.DownloadManager
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.download.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class HttpDownloadManager @Inject constructor(
  private val repo: DownloadRepository
) : DownloadManager {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = ConcurrentHashMap<String, Job>()

  override fun observe(taskId: String) = repo.observeState(taskId)
  override fun observeAll() = repo.observeAllStates()

  override suspend fun enqueue(task: DownloadTask) {
    repo.insertTask(task, DownloadStatus.QUEUED)
  }

  override suspend fun start(taskId: String) {
    // prevent double-start
    if (jobs.containsKey(taskId)) return

    val state = repo.observeState(taskId).first() ?: return
    val task = state.task
    val job = scope.launch {
      var connection: HttpURLConnection? = null
      var input: InputStream? = null
      var out: FileOutputStream? = null

      try {
        val targetFile = File(task.targetPath)
        val tempFile = File(task.targetPath + ".part")
        tempFile.parentFile?.mkdirs()

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val url = URL(task.source)
        connection = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = 15_000
          readTimeout = 30_000
          // resume when partial exists
          if (existingBytes > 0L) {
            setRequestProperty("Range", "bytes=$existingBytes-")
          }
          connect()
        }

        val responseCode = connection.responseCode
        if (responseCode in 400..599) {
          val err = "HTTP error $responseCode"
          repo.updateState(state.copy(status = DownloadStatus.FAILED, error = err))
          return@launch
        }

        val contentLengthHeaderStr = connection.getHeaderField("Content-Length")
        val contentLengthHeader = contentLengthHeaderStr?.toLongOrNull() ?: -1L

        val totalBytes = when {
          task.totalBytes > 0 -> task.totalBytes
          contentLengthHeader > 0 -> existingBytes + contentLengthHeader
          else -> -1L
        }

        // mark as downloading
        repo.updateState(state.copy(status = DownloadStatus.DOWNLOADING))

        input = connection.inputStream
        out = FileOutputStream(tempFile, true)

        val buffer = ByteArray(8 * 1024)
        var read = 0
        var downloaded = 0L
        var lastReportTime = System.currentTimeMillis()
        var lastReportBytes = 0L

        while (isActive && input.read(buffer).also { read = it } != -1) {
          out.write(buffer, 0, read)
          downloaded += read

          val now = System.currentTimeMillis()
          // update progress every 300ms or on loop end
          if (now - lastReportTime >= 300) {
            val totalSoFar = existingBytes + downloaded
            val progress =
              if (totalBytes > 0) ((totalSoFar * 100) / max(1L, totalBytes)).toInt() else 0
            val dt = max(1, now - lastReportTime)
            val bytesDelta = totalSoFar - lastReportBytes
            val speed = (bytesDelta * 1000L) / dt // bytes/sec

            repo.updateState(
              (repo.observeState(taskId).first() ?: state).copy(
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = totalSoFar,
                progress = progress,
                downloadSpeedBytesPerSec = speed,
                error = null
              )
            )

            lastReportTime = now
            lastReportBytes = totalSoFar
          }
        }

        // finished or cancelled
        if (!isActive) {
          // cancelled by pause/cancel
          val current = repo.observeState(taskId).first()
          val newStatus = current?.status ?: DownloadStatus.PAUSED
          repo.updateState((current ?: state).copy(status = newStatus))
          return@launch
        }

        out.flush()
        // rename temp to final target
        if (tempFile.exists()) {
          tempFile.renameTo(targetFile)
        }

        // final update
        val finalTotal = if (totalBytes > 0) totalBytes else existingBytes + downloaded
        repo.updateState(
          (repo.observeState(taskId).first() ?: state).copy(
            status = DownloadStatus.FINISHED,
            downloadedBytes = finalTotal,
            progress = 100,
            downloadSpeedBytesPerSec = 0L,
            error = null
          )
        )
      } catch (_: CancellationException) {
        // job cancelled (pause or cancel) - preserve state handled by caller
        // nothing to do here
      } catch (t: Throwable) {
        val err = t.message ?: "unknown error"
        val current = repo.observeState(taskId).firstOrNull()
        repo.updateState((current ?: state).copy(status = DownloadStatus.FAILED, error = err))
      } finally {
        try {
          input?.close()
        } catch (_: Exception) {
        }
        try {
          out?.close()
        } catch (_: Exception) {
        }
        connection?.disconnect()
        jobs.remove(taskId)
      }
    }

    jobs[taskId] = job
  }

  override suspend fun pause(taskId: String) {
    val job = jobs.remove(taskId)
    job?.cancelAndJoin()
    val current = repo.observeState(taskId).firstOrNull()
    current?.let {
      repo.updateState(it.copy(status = DownloadStatus.PAUSED, downloadSpeedBytesPerSec = 0L))
    }
  }

  override suspend fun resume(taskId: String) {
    // resume simply starts again (will use existing .part file)
    start(taskId)
  }

  override suspend fun cancel(taskId: String) {
    val job = jobs.remove(taskId)
    job?.cancelAndJoin()
    val current = repo.observeState(taskId).firstOrNull()
    current?.let {
      repo.updateState(it.copy(status = DownloadStatus.CANCELED, downloadSpeedBytesPerSec = 0L))
    }

    // optionally remove partial file
    val tempFile = File((current?.task?.targetPath ?: "") + ".part")
    if (tempFile.exists()) {
      try {
        tempFile.delete()
      } catch (_: Exception) {
      }
    }
  }

  // helper to avoid throwing when there is no value
  private suspend fun <T> kotlinx.coroutines.flow.Flow<T?>.firstOrNull(): T? {
    return try {
      this.first()
    } catch (_: Throwable) {
      null
    }
  }
}
