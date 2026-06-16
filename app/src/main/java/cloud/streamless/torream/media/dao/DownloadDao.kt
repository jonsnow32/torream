package cloud.streamless.torream.media.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cloud.streamless.torream.media.entities.HttpEntity
import cloud.streamless.torream.media.entities.TorrentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
  // HTTP
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHttp(http: HttpEntity)

  @Query("SELECT * FROM http_downloads WHERE url = :url")
  fun getHttpByUrlFlow(url: String): Flow<HttpEntity?>

  @Query("SELECT * FROM http_downloads WHERE is_private = 0")
  fun getAllHttpFlow(): Flow<List<HttpEntity>>

  @Query("SELECT * FROM http_downloads WHERE is_private = 1")
  fun getPrivateHttpFlow(): Flow<List<HttpEntity>>

  @Query("UPDATE http_downloads SET is_private = :isPrivate, target_path = :newPath WHERE url = :url")
  suspend fun setHttpPrivate(url: String, isPrivate: Boolean, newPath: String)

  @Delete
  suspend fun deleteHttp(http: HttpEntity)

  @Query("DELETE FROM http_downloads WHERE url = :url")
  suspend fun deleteHttpById(url: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTorrent(torrent: TorrentEntity)

  @Query("SELECT * FROM torrents WHERE info_hash = :infoHash")
  fun getTorrentByIdFlow(infoHash: String): Flow<TorrentEntity?>

  @Query("SELECT * FROM torrents WHERE is_private = 0 ORDER BY date_added DESC")
  fun getAllTorrentFlow(): Flow<List<TorrentEntity>>

  @Query("SELECT * FROM torrents WHERE is_private = 1 ORDER BY date_added DESC")
  fun getPrivateTorrentFlow(): Flow<List<TorrentEntity>>

  @Query("UPDATE torrents SET is_private = :isPrivate, target_path = :newPath WHERE info_hash = :infoHash")
  suspend fun setTorrentPrivate(infoHash: String, isPrivate: Boolean, newPath: String)

  @Delete
  suspend fun deleteTorrent(torrent: TorrentEntity)

  @Query("DELETE FROM torrents WHERE info_hash = :infoHash")
  suspend fun deleteTorrentById(infoHash: String)
}
