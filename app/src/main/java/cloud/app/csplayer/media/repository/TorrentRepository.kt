package cloud.app.csplayer.media.repository

import cloud.app.csplayer.media.dao.TorrentDao
import cloud.app.csplayer.media.entities.TorrentEntity
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.model.TorrentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentRepository @Inject constructor(
    private val torrentDao: TorrentDao
) {

    fun observeAllTorrents(): Flow<List<TorrentState>> {
        return torrentDao.observeAll().map { entities ->
            entities.map { it.toTorrentState() }
        }
    }

    suspend fun getAllTorrents(): List<TorrentState> {
        return torrentDao.getAll().map { it.toTorrentState() }
    }

    suspend fun getTorrentByInfoHash(infoHash: String): TorrentState? {
        return torrentDao.getByInfoHash(infoHash)?.toTorrentState()
    }

    fun observeTorrentByInfoHash(infoHash: String): Flow<TorrentState?> {
        return torrentDao.observeByInfoHash(infoHash).map { it?.toTorrentState() }
    }

    suspend fun getTorrentsByStatus(status: TorrentDownloadStatus): List<TorrentState> {
        return torrentDao.getByStatus(status.name).map { it.toTorrentState() }
    }

    fun observeTorrentsByStatus(status: TorrentDownloadStatus): Flow<List<TorrentState>> {
        return torrentDao.observeByStatus(status.name).map { entities ->
            entities.map { it.toTorrentState() }
        }
    }

    suspend fun insertTorrent(
        infoHash: String,
        name: String,
        magnetUri: String? = null,
        torrentFilePath: String? = null,
        savePath: String,
        status: TorrentDownloadStatus = TorrentDownloadStatus.DOWNLOADING,
        totalSize: Long = 0
    ) {
        val entity = TorrentEntity(
            infoHash = infoHash,
            name = name,
            magnetUri = magnetUri,
            torrentFilePath = torrentFilePath,
            savePath = savePath,
            status = status.name,
            totalSize = totalSize,
            dateAdded = System.currentTimeMillis()
        )
        torrentDao.insert(entity)
    }

    suspend fun updateTorrentState(state: TorrentState) {
        torrentDao.updateProgress(
            infoHash = state.infoHash,
            status = state.status.name,
            progress = state.progress,
            downloadSpeed = state.downloadSpeed,
            uploadSpeed = state.uploadSpeed,
            downloadedSize = state.downloadedSize,
            numPeers = state.numPeers,
            numSeeds = state.numSeeds
        )
    }

    suspend fun updateTorrentStatus(
        infoHash: String,
        status: TorrentDownloadStatus,
        error: String? = null
    ) {
        torrentDao.updateStatus(infoHash, status.name, error)

        // Mark as completed if finished
        if (status == TorrentDownloadStatus.FINISHED) {
            torrentDao.markAsCompleted(infoHash, System.currentTimeMillis())
        }
    }

    suspend fun deleteTorrent(infoHash: String) {
        torrentDao.deleteByInfoHash(infoHash)
    }

    suspend fun deleteAllTorrents() {
        torrentDao.deleteAll()
    }

    suspend fun getTorrentCount(): Int {
        return torrentDao.getCount()
    }

    suspend fun getActiveTorrentsCount(): Int {
        return torrentDao.getCountByStatus(TorrentDownloadStatus.DOWNLOADING.name)
    }

    // Extension function to convert Entity to State
    private fun TorrentEntity.toTorrentState(): TorrentState {
        return TorrentState(
            infoHash = infoHash,
            name = name,
            status = TorrentDownloadStatus.valueOf(status),
            progress = progress,
            downloadSpeed = downloadSpeed,
            uploadSpeed = uploadSpeed,
            totalSize = totalSize,
            downloadedSize = downloadedSize,
            numPeers = numPeers,
            numSeeds = numSeeds,
            error = error
        )
    }
}
