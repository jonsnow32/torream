package cloud.app.csplayer.media.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cloud.app.csplayer.media.entities.TorrentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TorrentDao {
    @Query("SELECT * FROM torrents ORDER BY date_added DESC")
    fun observeAll(): Flow<List<TorrentEntity>>

    @Query("SELECT * FROM torrents ORDER BY date_added DESC")
    suspend fun getAll(): List<TorrentEntity>

    @Query("SELECT * FROM torrents WHERE info_hash = :infoHash")
    suspend fun getByInfoHash(infoHash: String): TorrentEntity?

    @Query("SELECT * FROM torrents WHERE info_hash = :infoHash")
    fun observeByInfoHash(infoHash: String): Flow<TorrentEntity?>

    @Query("SELECT * FROM torrents WHERE status = :status ORDER BY date_added DESC")
    suspend fun getByStatus(status: String): List<TorrentEntity>

    @Query("SELECT * FROM torrents WHERE status = :status ORDER BY date_added DESC")
    fun observeByStatus(status: String): Flow<List<TorrentEntity>>

    @Query("SELECT * FROM torrents WHERE status IN (:statuses) ORDER BY date_added DESC")
    suspend fun getByStatuses(statuses: List<String>): List<TorrentEntity>

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(torrent: TorrentEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(torrents: List<TorrentEntity>)

    @Update
    suspend fun update(torrent: TorrentEntity)

    @Delete
    suspend fun delete(torrent: TorrentEntity)

    @Query("DELETE FROM torrents WHERE info_hash = :infoHash")
    suspend fun deleteByInfoHash(infoHash: String)

    @Query("DELETE FROM torrents")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM torrents")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM torrents WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    @Query("UPDATE torrents SET status = :status, progress = :progress, download_speed = :downloadSpeed, upload_speed = :uploadSpeed, downloaded_size = :downloadedSize, num_peers = :numPeers, num_seeds = :numSeeds WHERE info_hash = :infoHash")
    suspend fun updateProgress(
        infoHash: String,
        status: String,
        progress: Float,
        downloadSpeed: Long,
        uploadSpeed: Long,
        downloadedSize: Long,
        numPeers: Int,
        numSeeds: Int
    )

    @Query("UPDATE torrents SET status = :status, error = :error WHERE info_hash = :infoHash")
    suspend fun updateStatus(infoHash: String, status: String, error: String? = null)

    @Query("UPDATE torrents SET date_completed = :dateCompleted WHERE info_hash = :infoHash")
    suspend fun markAsCompleted(infoHash: String, dateCompleted: Long)
}
