package com.tv.apps.zippy.download

import com.tv.apps.zippy.media.entities.TorrentEntity
import com.tv.apps.zippy.media.dao.DownloadDao
import com.tv.apps.zippy.media.entities.HttpEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
  private val downloadDao: DownloadDao
) : DownloadRepository {

  /**
   * Deserialize headers from JSON string to Map
   */
  private fun deserializeHeaders(headersJson: String?): Map<String, String>? {
    if (headersJson == null || headersJson.isEmpty()) return null
    return try {
      val headerMap = mutableMapOf<String, String>()
      // Parse simple JSON format: {"key1":"value1","key2":"value2"}
      val entries = headersJson.removeSurrounding("{", "}").split(",")
      entries.forEach { entry ->
        val parts = entry.split(":", limit = 2)
        if (parts.size == 2) {
          val key = parts[0].trim().removeSurrounding("\"")
          val value = parts[1].trim().removeSurrounding("\"")
          if (key.isNotBlank() && value.isNotBlank()) {
            headerMap[key] = value
          }
        }
      }
      if (headerMap.isNotEmpty()) headerMap else null
    } catch (e: Exception) {
      Timber.w(e, "Failed to deserialize headers")
      null
    }
  }

  /**
   * Serialize headers Map to JSON string
   */
  private fun serializeHeaders(headers: Map<String, String>?): String? {
    if (headers == null || headers.isEmpty()) return null
    return try {
      val entries = headers.entries.joinToString(",") { (key, value) ->
        "\"${key.replace("\"", "\\\"")}\":\"${value.replace("\"", "\\\"")}\""
      }
      "{$entries}"
    } catch (e: Exception) {
      Timber.w(e, "Failed to serialize headers")
      null
    }
  }

  override suspend fun loadAllTask(): List<DownloadTask> {
    // Load HTTP tasks from database
    val httpEntities: List<HttpEntity> = try {
      downloadDao.getAllHttpFlow().first()
    } catch (_: Exception) {
      emptyList()
    }

    val httpTasks = httpEntities.map { e ->
      DownloadTask(
        id = e.url, // URL as unique ID for HTTP downloads
        type = DownloadType.HTTP,
        source = e.url,
        targetPath = e.targetPath,
        fileName = e.fileName,
        headers = deserializeHeaders(e.headers),
        totalBytes = e.totalBytes,
        createdAt = e.createdAt
      )
    }

    // Load torrent entities from database
    val torrentEntities: List<TorrentEntity> = try {
      downloadDao.getAllTorrentFlow().first()
    } catch (_: Exception) {
      emptyList()
    }

    val torrentTasks = torrentEntities.map { e ->
      DownloadTask(
        id = e.infoHash,
        type = DownloadType.TORRENT,
        source = e.infoHash,
        targetPath = e.targetPath,
        fileName = e.fileName,
        totalBytes = e.totalSize,
        createdAt = e.dateAdded
      )
    }

    // Merge and sort by createdAt (earliest first)
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
          targetPath = task.targetPath,
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
        // Persist HTTP downloads to database
        val entity = HttpEntity(
          url = task.source,
          targetPath = task.targetPath,
          fileName = task.fileName,
          tempPath = null,
          totalBytes = task.totalBytes,
          downloadedBytes = 0L,
          progress = 0,
          acceptRanges = false,
          etag = null,
          lastModified = null,
          headers = serializeHeaders(task.headers),
          status = initialStatus.name,
          error = null,
          createdAt = task.createdAt,
          updatedAt = System.currentTimeMillis()
        )
        downloadDao.insertHttp(entity)
      }

      DownloadType.HLS -> {
        // HLS downloads are persisted as HTTP entities with .m3u8 URL
        val entity = HttpEntity(
          url = task.source,
          targetPath = task.targetPath,
          fileName = task.fileName,
          tempPath = null,
          totalBytes = task.totalBytes,
          downloadedBytes = 0L,
          progress = 0,
          acceptRanges = false,
          etag = null,
          lastModified = null,
          headers = serializeHeaders(task.headers),
          status = initialStatus.name,
          error = null,
          createdAt = task.createdAt,
          updatedAt = System.currentTimeMillis()
        )
        downloadDao.insertHttp(entity)
      }
    }
  }

  override fun observeState(taskId: String): Flow<DownloadState?> {
    // taskId corresponds to task.id which for torrent equals infoHash, for http equals URL
    val httpFlow: Flow<DownloadState?> = downloadDao.getHttpByUrlFlow(taskId)
      .map { it?.let { e -> httpEntityToDownloadState(e) } }

    val torrentFlow: Flow<DownloadState?> = downloadDao.getTorrentByIdFlow(taskId)
      .map { it?.let { e -> torrentEntityToDownloadState(e) } }

    return combine(httpFlow, torrentFlow) { h: DownloadState?, t: DownloadState? -> h ?: t }
      .distinctUntilChanged()
  }

  override fun observeAllStates(): Flow<List<DownloadState>> {
    val httpListFlow: Flow<List<DownloadState>> = downloadDao.getAllHttpFlow()
      .map { list -> list.map { e -> httpEntityToDownloadState(e) } }

    val torrentListFlow: Flow<List<DownloadState>> = downloadDao.getAllTorrentFlow()
      .map { list -> list.map { e -> torrentEntityToDownloadState(e) } }

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
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            status = state.status.name,
            progress = state.progress.toFloat(),
            downloadSpeed = state.speed,
            uploadSpeed = 0L,
            totalSize = state.task.totalBytes,
            downloadedSize = state.downloadedBytes,
            numPeers = state.numPeers,
            numSeeds = state.numSeeds,
            error = state.error,
            dateAdded = state.task.createdAt,
            dateCompleted = if (state.status == DownloadStatus.FINISHED) System.currentTimeMillis() else null,
            isAutoManaged = true
          )).copy(
            // copy over fields from current or state
            // Use task.fileName if available (set by worker), otherwise keep current name or use source
            name = state.task.fileName ?: current?.name ?: state.task.source,
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            status = state.status.name,
            progress = state.progress.toFloat(),
            downloadSpeed = state.speed,
            uploadSpeed = current?.uploadSpeed ?: 0L,
            totalSize = if (state.task.totalBytes > 0) state.task.totalBytes else current?.totalSize ?: 0L,
            downloadedSize = state.downloadedBytes,
            numPeers = state.numPeers,
            numSeeds = state.numSeeds,
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
        // Persist HTTP download state to database
        try {
          val url = state.task.source
          val current = try {
            downloadDao.getHttpByUrlFlow(url).first()
          } catch (_: Exception) {
            null
          }

          val updated = (current ?: HttpEntity(
            url = url,
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            tempPath = null,
            totalBytes = state.task.totalBytes,
            downloadedBytes = state.downloadedBytes,
            progress = state.progress,
            speed = state.speed,
            acceptRanges = false,
            etag = null,
            lastModified = null,
            status = state.status.name,
            error = state.error,
            createdAt = state.task.createdAt,
            updatedAt = System.currentTimeMillis()
          )).copy(
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            totalBytes = if (state.task.totalBytes > 0) state.task.totalBytes else current?.totalBytes ?: 0L,
            downloadedBytes = state.downloadedBytes,
            progress = state.progress,
            status = state.status.name,
            speed = state.speed,
            error = state.error,
            updatedAt = System.currentTimeMillis()
          )

          downloadDao.insertHttp(updated)
        } catch (_: Exception) {
          // best-effort
        }
      }

      DownloadType.HLS -> {
        // HLS downloads are stored as HTTP entities
        try {
          val url = state.task.source
          val current = try {
            downloadDao.getHttpByUrlFlow(url).first()
          } catch (_: Exception) {
            null
          }

          val updated = (current ?: HttpEntity(
            url = url,
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            tempPath = null,
            totalBytes = state.task.totalBytes,
            downloadedBytes = state.downloadedBytes,
            progress = state.progress,
            speed = state.speed,
            acceptRanges = false,
            etag = null,
            lastModified = null,
            status = state.status.name,
            error = state.error,
            createdAt = state.task.createdAt,
            updatedAt = System.currentTimeMillis()
          )).copy(
            targetPath = state.task.targetPath,
            fileName = state.task.fileName,
            totalBytes = if (state.task.totalBytes > 0) state.task.totalBytes else current?.totalBytes ?: 0L,
            downloadedBytes = state.downloadedBytes,
            progress = state.progress,
            status = state.status.name,
            speed = state.speed,
            error = state.error,
            updatedAt = System.currentTimeMillis()
          )

          downloadDao.insertHttp(updated)
        } catch (_: Exception) {
          // best-effort
        }
      }
    }
  }

  override suspend fun deleteTask(taskId: String) {
    // Try to delete HTTP download by URL (taskId for HTTP is the URL)
    try {
      downloadDao.deleteHttpById(taskId)
    } catch (_: Exception) {
      // ignore if not found
    }

    // Try to delete torrent by infoHash
    try {
      downloadDao.deleteTorrentById(taskId)
    } catch (_: Exception) {
      // ignore if not found
    }
  }

  // mapping helpers
  private fun httpEntityToDownloadState(e: HttpEntity): DownloadState {
    val task = DownloadTask(
      id = e.url, // URL as unique ID for HTTP
      type = DownloadType.HTTP,
      source = e.url,
      targetPath = e.targetPath,
      fileName = e.fileName,
      headers = deserializeHeaders(e.headers),
      totalBytes = e.totalBytes,
      createdAt = e.createdAt,
    )
    val status = try {
      DownloadStatus.valueOf(e.status)
    } catch (_: Throwable) {
      DownloadStatus.FAILED
    }
    return DownloadState(
      task = task,
      status = status,
      downloadedBytes = e.downloadedBytes,
      progress = e.progress.coerceIn(0, 100),
      speed = e.speed, // Speed is not stored in HttpEntity, will be updated by worker
      error = e.error
    )
  }

  private fun torrentEntityToDownloadState(e: TorrentEntity): DownloadState {
    val task = DownloadTask(
      id = e.infoHash, // ID is the infoHash (or hashCode)
      type = DownloadType.TORRENT,
      source = e.magnetUri ?: e.torrentFilePath ?: e.name, // Source is the actual magnet/file
      targetPath = e.targetPath,
      fileName = e.fileName,
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
      speed = e.downloadSpeed,
      numSeeds = e.numSeeds,
      numPeers = e.numPeers,
      error = e.error
    )
  }
}
