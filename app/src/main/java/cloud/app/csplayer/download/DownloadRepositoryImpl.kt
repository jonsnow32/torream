package cloud.app.csplayer.download

import cloud.app.csplayer.media.entities.TorrentEntity
import cloud.app.csplayer.media.dao.DownloadDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
  private val downloadDao: DownloadDao
) : DownloadRepository {

  // In-memory store for HTTP tasks (keyed by url)
  private val httpMutex = Mutex()
  private val httpStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

  override suspend fun loadAllTask(): List<DownloadTask> {
    // Snapshot HTTP tasks under mutex for consistency
    val httpTasks: List<DownloadTask> = httpMutex.withLock {
      httpStates.value.values.map { it.task }
    }

    // Snapshot torrent entities from DB (use first() to get current value of Flow)
    val torrentEntities: List<TorrentEntity> = try {
      downloadDao.getAllTorrentFlow().first()
    } catch (e: Exception) {
      emptyList()
    }

    val torrentTasks = torrentEntities.map { e ->
      DownloadTask(
        id = e.infoHash,
        type = DownloadType.TORRENT,
        source = e.infoHash,
        targetPath = e.savePath,
        title = e.name, // Load title from database
        totalBytes = e.totalSize,
        createdAt = e.dateAdded
      )
    }

    // Merge and sort by createdAt (earliest first). If you prefer newest first, reverse the sort.
    return (httpTasks + torrentTasks).sortedBy { it.createdAt }
  }

  override suspend fun insertTask(task: DownloadTask, initialStatus: DownloadStatus) {
    when (task.type) {
      DownloadType.TORRENT -> {
        // insert a TorrentEntity - use task.id as infoHash for lookup
        val entity = TorrentEntity(
          infoHash = task.id, // Use task.id (extracted hash) not task.source (full magnet)
          name = task.source, // Use full source as display name
          magnetUri = if (task.source.startsWith("magnet:")) task.source else null,
          torrentFilePath = if (task.source.endsWith(".torrent")) task.source else null,
          savePath = task.targetPath,
          status = initialStatus.name,
          progress = 0f,
          downloadSpeed = 0L,
          uploadSpeed = 0L,
          totalSize = task.totalBytes,
          downloadedSize = 0L,
          numPeers = 0,
          numSeeds = 0,
          error = null,
          dateAdded = task.createdAt,
          dateCompleted = null,
          isAutoManaged = true
        )
        downloadDao.insertTorrent(entity)
      }

      DownloadType.HTTP -> {
        httpMutex.withLock {
          val state = DownloadState(
            task = task,
            status = initialStatus,
            downloadedBytes = 0L,
            progress = 0,
            downloadSpeedBytesPerSec = 0L,
            error = null
          )
          httpStates.value += (task.source to state)
        }
      }
    }
  }

  override fun observeState(taskId: String): Flow<DownloadState?> {
    // taskId corresponds to task.id which for torrent we expect equals infoHash, for http we used source url as key
    val httpFlow: Flow<DownloadState?> = httpStates.map { map -> map.values.find { s -> s.task.id == taskId } }
    val torrentFlow: Flow<DownloadState?> =
      downloadDao.getTorrentByIdFlow(taskId).map { it?.let { e -> torrentEntityToDownloadState(e) } }

    return combine(httpFlow, torrentFlow) { h: DownloadState?, t: DownloadState? -> h ?: t }
      .distinctUntilChanged()
  }

  override fun observeAllStates(): Flow<List<DownloadState>> {
    val httpListFlow: Flow<List<DownloadState>> = httpStates.map { it.values.toList() }
    val torrentListFlow: Flow<List<DownloadState>> =
      downloadDao.getAllTorrentFlow().map { list -> list.map { e -> torrentEntityToDownloadState(e) } }

    return combine(httpListFlow, torrentListFlow) { http: List<DownloadState>, torrents: List<DownloadState> ->
      (http + torrents).sortedBy { it.task.createdAt }
    }
  }

  override suspend fun updateState(state: DownloadState) {
    when (state.task.type) {
      DownloadType.TORRENT -> {
        // update torrent progress/status by replacing the entity via insertTorrent (REPLACE)
        val infoHash = state.task.id // Use task.id for lookup, not task.source
        try {
          val current = try {
            downloadDao.getTorrentByIdFlow(infoHash).first()
          } catch (_: Exception) {
            null
          }

          val updated = (current ?: TorrentEntity(
            infoHash = infoHash, // Use task.id
            name = state.task.source, // Display name from source
            magnetUri = if (state.task.source.startsWith("magnet:")) state.task.source else null,
            torrentFilePath = if (state.task.source.endsWith(".torrent")) state.task.source else null,
            savePath = state.task.targetPath,
            status = state.status.name,
            progress = state.progress.toFloat(),
            downloadSpeed = state.downloadSpeedBytesPerSec,
            uploadSpeed = 0L,
            totalSize = state.task.totalBytes,
            downloadedSize = state.downloadedBytes,
            numPeers = 0,
            numSeeds = 0,
            error = state.error,
            dateAdded = state.task.createdAt,
            dateCompleted = if (state.status == DownloadStatus.FINISHED) System.currentTimeMillis() else null,
            isAutoManaged = true
          )).copy(
            // copy over fields from current or state
            // Use task.title if available (set by worker), otherwise keep current name or use source
            name = state.task.title ?: current?.name ?: state.task.source,
            savePath = current?.savePath ?: state.task.targetPath,
            status = state.status.name,
            progress = state.progress.toFloat(),
            downloadSpeed = state.downloadSpeedBytesPerSec,
            uploadSpeed = current?.uploadSpeed ?: 0L,
            totalSize = if (state.task.totalBytes > 0) state.task.totalBytes else current?.totalSize ?: 0L,
            downloadedSize = state.downloadedBytes,
            numPeers = current?.numPeers ?: 0,
            numSeeds = current?.numSeeds ?: 0,
            error = state.error,
            dateAdded = current?.dateAdded ?: state.task.createdAt,
            dateCompleted = if (state.status == DownloadStatus.FINISHED) System.currentTimeMillis() else current?.dateCompleted,
            isAutoManaged = current?.isAutoManaged ?: true
          )

          downloadDao.insertTorrent(updated)
        } catch (_: Exception) {
          // best-effort
        }
      }

      DownloadType.HTTP -> {
        httpMutex.withLock {
          val byKey = httpStates.value
          val key = state.task.source
          // The state parameter already contains updated task with title from Worker
          // Just store it directly
          httpStates.value = byKey + (key to state)
        }
      }
    }
  }

  override suspend fun deleteTask(taskId: String) {
    // try delete http by matching id or url
    httpMutex.withLock {
      val remaining = httpStates.value.filterValues { it.task.id != taskId }
      httpStates.value = remaining
    }

    // remove torrent if exists
    try {
      downloadDao.deleteTorrentById(taskId)
    } catch (_: Exception) {
      // ignore
    }
  }

  // mapping helper
  private fun torrentEntityToDownloadState(e: TorrentEntity): DownloadState {
    val task = DownloadTask(
      id = e.infoHash, // ID is the infoHash (or hashCode)
      type = DownloadType.TORRENT,
      source = e.magnetUri ?: e.torrentFilePath ?: e.name, // Source is the actual magnet/file
      targetPath = e.savePath,
      title = e.name, // Title is the torrent name
      totalBytes = e.totalSize,
      createdAt = e.dateAdded
    )
    val status = try {
      DownloadStatus.valueOf(e.status)
    } catch (_: Throwable) {
      DownloadStatus.FAILED
    }
    return DownloadState(
      task = task,
      status = status,
      downloadedBytes = e.downloadedSize,
      progress = (e.progress.coerceIn(0f, 100f)).toInt(),
      downloadSpeedBytesPerSec = e.downloadSpeed,
      error = e.error
    )
  }
}
