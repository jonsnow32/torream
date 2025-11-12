package cloud.app.csplayer.media.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cloud.app.csplayer.media.entities.HttpEntity
import cloud.app.csplayer.media.entities.TorrentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
  // HTTP
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHttp(http: HttpEntity)

  @Query("SELECT * FROM http_downloads WHERE url = :url")
  fun getHttpByUrlFlow(url: String): Flow<HttpEntity?>

  @Query("SELECT * FROM http_downloads")
  fun getAllHttpFlow(): Flow<List<HttpEntity>>

  @Delete
  suspend fun deleteHttp(http: HttpEntity)

  @Query("DELETE FROM http_downloads WHERE url = :url")
  suspend fun deleteHttpById(url: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTorrent(torrent: TorrentEntity)

  @Query("SELECT * FROM torrents WHERE info_hash = :infoHash")
  fun getTorrentByIdFlow(infoHash: String): Flow<TorrentEntity?>

  @Query("SELECT * FROM torrents ORDER BY date_added DESC")
  fun getAllTorrentFlow(): Flow<List<TorrentEntity>>

  @Delete
  suspend fun deleteTorrent(torrent: TorrentEntity)

  @Query("DELETE FROM torrents WHERE info_hash = :infoHash")
  suspend fun deleteTorrentById(infoHash: String)
}
