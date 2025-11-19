// kotlin
package cloud.app.csplayer.download.torrent

import cloud.app.csplayer.download.DownloadManager
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.download.DownloadTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import timber.log.Timber

@Singleton
class TorrentDownloadManager @Inject constructor(
  private val repo: DownloadRepository
) : DownloadManager {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile
  private var session: SessionManager? = null
  private val handles = ConcurrentHashMap<String, TorrentHandle>() // infoHash -> handle
  private val pollJobs = ConcurrentHashMap<String, Job>()

  // Removed eager SessionManager creation to avoid native lib load on main thread.

  private suspend fun ensureSessionInitialized() {
    if (session != null) return
    withContext(Dispatchers.IO) {
      synchronized(this@TorrentDownloadManager) {
        if (session == null) {
          Timber.d("Initializing libtorrent SessionManager on thread=%s", Thread.currentThread().name)
          val s = SessionManager()
          s.start() // runs on IO
          try {
            s.startDht()
            Timber.i("SessionManager started and DHT initialized")
          } catch (_: Throwable) {
            // best-effort
            Timber.w("Failed to start DHT (non-fatal)")
          }
          session = s
        }
      }
    }
  }

  override fun observe(taskId: String) = repo.observeState(taskId)
  override fun observeAll() = repo.observeAllStates()

  override suspend fun enqueue(task: DownloadTask) {
    Timber.d("enqueue task=%s type=%s", task.id, task.type)
    repo.insertTask(task, DownloadStatus.QUEUED)
  }

  override suspend fun start(taskId: String) {
    Timber.d("start(taskId=%s) - ensure session init", taskId)
    // ensure session/native libs initialized off main thread
    ensureSessionInitialized()

    val state = repo.observeState(taskId).firstOrNull() ?: return
    val task = state.task
    // avoid double-start
    if (handles.containsKey(taskId)) return

    // determine savePath: if targetPath is a file, use parent dir, else use provided path
    val saveDir = File(task.targetPath).let { if (it.isDirectory) it else it.parentFile ?: it }
    if (!saveDir.exists()) saveDir.mkdirs()

    val s = session ?: run {
      Timber.e("Session not initialized when starting task=%s", taskId)
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = "Session not initialized"))
      return
    }

    // add torrent to session: support magnet link, .torrent file, or bare info-hash
    val ti = try {
      when {
        task.source.startsWith("magnet:", ignoreCase = true) -> {
          // fetch metadata for magnet then download
          val tmp = File(saveDir, "magnet_tmp_${taskId}").apply { mkdirs() }
          val meta = s.fetchMagnet(task.source, 30, tmp)
          if (meta == null) throw IllegalStateException("Failed to fetch magnet metadata")
          Timber.d("Magnet metadata fetched for task=%s, size=%d", taskId, meta.size)
          TorrentInfo.bdecode(meta)
        }

        File(task.source).exists() -> {
          Timber.d("Using .torrent file for task=%s path=%s", taskId, task.source)
          // .torrent file
          TorrentInfo(File(task.source))
        }

        else -> {
          // treat as info-hash: construct magnet and fetch
          val magnet = "magnet:?xt=urn:btih:${task.source}"
          val tmp = File(saveDir, "magnet_tmp_${taskId}").apply { mkdirs() }
          val meta = s.fetchMagnet(magnet, 30, tmp)
            ?: throw IllegalStateException("Failed to fetch magnet metadata for infoHash")
          Timber.d("Fetched metadata for info-hash task=%s", taskId)
          TorrentInfo.bdecode(meta)
        }
      }
    } catch (t: Throwable) {
      Timber.e(t, "Failed to build TorrentInfo for task=%s", taskId)
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = t.message))
      return
    }

    // download via session
    try {
      s.download(ti, saveDir)
    } catch (t: Throwable) {
      Timber.e(t, "Failed to start download for task=%s", taskId)
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = t.message))
      return
    }

    // find handle by info hash
    val infoHash = ti.infoHash().toHex()
    val sha = Sha1Hash.parseHex(infoHash)
    // The handle might not be immediately available after download() returns.
    // Retry find for a short period before giving up — this avoids spurious failures
    // on slower devices or when metadata fetch is still being processed.
    var handle: TorrentHandle? = null
    try {
      val maxAttempts = 10
      val delayMs = 300L
      for (attempt in 1..maxAttempts) {
        handle = s.find(sha)
        if (handle != null) {
          Timber.d("Found torrent handle for task=%s infoHash=%s after %d attempts", taskId, infoHash, attempt)
          break
        }
        Timber.d("Torrent handle not found yet for task=%s infoHash=%s (attempt %d/%d)", taskId, infoHash, attempt, maxAttempts)
        delay(delayMs)
      }
    } catch (t: Throwable) {
      Timber.w(t, "Interrupted while waiting for torrent handle for task=%s", taskId)
    }

    if (handle == null) {
      Timber.e("Failed to find torrent handle for task=%s infoHash=%s after retries", taskId, infoHash)
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = "Failed to find torrent handle"))
      return
    }

    handles[taskId] = handle
    Timber.d("Download handle obtained for task=%s infoHash=%s", taskId, infoHash)

    // set repo state to DOWNLOADING
    repo.updateState(state.copy(status = DownloadStatus.DOWNLOADING))

    // polling job: map TorrentStatus -> DownloadState
    val job = scope.launch {
      try {
        while (isActive && handle.isValid()) {
          val ts: TorrentStatus = handle.status()
          val progress = (ts.progress() * 100f).roundToInt()
          val downloaded = ts.totalDone()
          val speed = ts.downloadRate().toLong() // bytes/sec

          val stateName = ts.state().name
          val statusMapped = when (stateName.uppercase()) {
            "FINISHED" -> DownloadStatus.FINISHED
            "DOWNLOADING" -> DownloadStatus.DOWNLOADING
            "SEEDING" -> DownloadStatus.SEEDING
            "CHECKING_FILES", "DOWNLOADING_METADATA", "CHECKING_RESUME_DATA", "ALLOCATING" -> DownloadStatus.DOWNLOADING
            "PAUSED" -> DownloadStatus.PAUSED
            else -> if (handle.isValid()) DownloadStatus.DOWNLOADING else DownloadStatus.FINISHED
          }

          Timber.d("task=%s progress=%d downloaded=%d speed=%d status=%s", taskId, progress, downloaded, speed, statusMapped)
          // construct updated DownloadState
          val current = repo.observeState(taskId).firstOrNull()
          val updated = (current ?: state).copy(
            status = statusMapped,
            downloadedBytes = downloaded,
            progress = progress,
            downloadSpeedBytesPerSec = speed,
            error = null
          )
          repo.updateState(updated)

          // stop polling if finished or seeding
          if (statusMapped == DownloadStatus.FINISHED || statusMapped == DownloadStatus.SEEDING) {
            break
          }

          delay(500L)
        }
      } catch (_: CancellationException) {
        // job cancelled -> do nothing (pause/cancel handlers will update repo)
        Timber.d("Polling job cancelled for task=%s", taskId)
      } catch (t: Throwable) {
        Timber.e(t, "Polling failed for task=%s", taskId)
        val cur = repo.observeState(taskId).firstOrNull()
        repo.updateState((cur ?: state).copy(status = DownloadStatus.FAILED, error = t.message))
      } finally {
        pollJobs.remove(taskId)
        Timber.d("Polling job finished for task=%s", taskId)
      }
    }

    pollJobs[taskId] = job
  }

  override suspend fun pause(taskId: String) {
    Timber.d("pause(taskId=%s)", taskId)
    val handle = handles[taskId]
    handle?.apply {
      try {
        pause()
        Timber.d("Paused handle for task=%s", taskId)
      } catch (_: Throwable) {
        Timber.w("Error while pausing task=%s", taskId)
      }
    }
    pollJobs.remove(taskId)?.cancelAndJoin()
    val current = repo.observeState(taskId).firstOrNull()
    current?.let { repo.updateState(it.copy(status = DownloadStatus.PAUSED, downloadSpeedBytesPerSec = 0L)) }
  }

  override suspend fun resume(taskId: String) {
    Timber.d("resume(taskId=%s)", taskId)
    val handle = handles[taskId]
    handle?.apply {
      try {
        resume()
        Timber.d("Resumed handle for task=%s", taskId)
      } catch (_: Throwable) {
        Timber.w("Error while resuming task=%s", taskId)
      }
    }
    // restart polling if needed
    start(taskId)
  }

  override suspend fun cancel(taskId: String) {
    Timber.d("cancel(taskId=%s)", taskId)
    // stop poll job
    pollJobs.remove(taskId)?.cancelAndJoin()
    // remove from session (do not delete files by default)
    val handle = handles.remove(taskId)
    val s = session
    if (handle != null && s != null && handle.isValid()) {
      try {
        s.remove(handle)
        Timber.d("Removed handle from session for task=%s", taskId)
      } catch (_: Throwable) {
        // best-effort
        Timber.w("Failed to remove handle for task=%s", taskId)
      }
    }

    val current = repo.observeState(taskId).firstOrNull()
    current?.let { repo.updateState(it.copy(status = DownloadStatus.CANCELED, downloadSpeedBytesPerSec = 0L)) }
  }
}
