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

@Singleton
class TorrentDownloadManager @Inject constructor(
  private val repo: DownloadRepository
) : DownloadManager {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val session = SessionManager()
  private val handles = ConcurrentHashMap<String, TorrentHandle>() // infoHash -> handle
  private val pollJobs = ConcurrentHashMap<String, Job>()

  init {
    session.start() // starts libtorrent session
    try {
      session.startDht()
    } catch (_: Throwable) {
      // best-effort
    }
  }

  override fun observe(taskId: String) = repo.observeState(taskId)
  override fun observeAll() = repo.observeAllStates()

  override suspend fun enqueue(task: DownloadTask) {
    repo.insertTask(task, DownloadStatus.QUEUED)
  }

  override suspend fun start(taskId: String) {
    val state = repo.observeState(taskId).firstOrNull() ?: return
    val task = state.task
    // avoid double-start
    if (handles.containsKey(taskId)) return

    // determine savePath: if targetPath is a file, use parent dir, else use provided path
    val saveDir = File(task.targetPath).let { if (it.isDirectory) it else it.parentFile ?: it }
    if (!saveDir.exists()) saveDir.mkdirs()

    // add torrent to session: support magnet link, .torrent file, or bare info-hash
    val ti = try {
      when {
        task.source.startsWith("magnet:", ignoreCase = true) -> {
          // fetch metadata for magnet then download
          val tmp = File(saveDir, "magnet_tmp_${taskId}").apply { mkdirs() }
          val meta = session.fetchMagnet(task.source, 30, tmp)
          if (meta == null) throw IllegalStateException("Failed to fetch magnet metadata")
          TorrentInfo.bdecode(meta)
        }

        File(task.source).exists() -> {
          // .torrent file
          TorrentInfo(File(task.source))
        }

        else -> {
          // treat as info-hash: construct magnet and fetch
          val magnet = "magnet:?xt=urn:btih:${task.source}"
          val tmp = File(saveDir, "magnet_tmp_${taskId}").apply { mkdirs() }
          val meta = session.fetchMagnet(magnet, 30, tmp)
          if (meta == null) throw IllegalStateException("Failed to fetch magnet metadata for infoHash")
          TorrentInfo.bdecode(meta)
        }
      }
    } catch (t: Throwable) {
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = t.message))
      return
    }

    // download via session
    try {
      session.download(ti, saveDir)
    } catch (t: Throwable) {
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = t.message))
      return
    }

    // find handle by info hash
    val infoHash = ti.infoHash().toHex()
    val sha = Sha1Hash.parseHex(infoHash)
    val handle = session.find(sha) ?: run {
      repo.updateState(state.copy(status = DownloadStatus.FAILED, error = "Failed to find torrent handle"))
      return
    }

    handles[taskId] = handle

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
      } catch (t: Throwable) {
        val cur = repo.observeState(taskId).firstOrNull()
        repo.updateState((cur ?: state).copy(status = DownloadStatus.FAILED, error = t.message))
      } finally {
        pollJobs.remove(taskId)
      }
    }

    pollJobs[taskId] = job
  }

  override suspend fun pause(taskId: String) {
    val handle = handles[taskId]
    handle?.apply {
      try {
        pause()
      } catch (_: Throwable) {
      }
    }
    pollJobs.remove(taskId)?.cancelAndJoin()
    val current = repo.observeState(taskId).firstOrNull()
    current?.let { repo.updateState(it.copy(status = DownloadStatus.PAUSED, downloadSpeedBytesPerSec = 0L)) }
  }

  override suspend fun resume(taskId: String) {
    val handle = handles[taskId]
    handle?.apply {
      try {
        resume()
      } catch (_: Throwable) {
      }
    }
    // restart polling if needed
    start(taskId)
  }

  override suspend fun cancel(taskId: String) {
    // stop poll job
    pollJobs.remove(taskId)?.cancelAndJoin()
    // remove from session (do not delete files by default)
    val handle = handles.remove(taskId)
    if (handle != null && handle.isValid()) {
      try {
        // remove torrent from session
        session.remove(handle)
      } catch (_: Throwable) {
        // best-effort
      }
    }

    val current = repo.observeState(taskId).firstOrNull()
    current?.let { repo.updateState(it.copy(status = DownloadStatus.CANCELED, downloadSpeedBytesPerSec = 0L)) }
  }
}
