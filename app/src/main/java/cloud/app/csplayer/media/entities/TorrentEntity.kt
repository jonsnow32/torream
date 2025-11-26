package cloud.app.csplayer.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "torrents",
  indices = [Index(
    value = ["info_hash"],
    unique = true
  ), Index(value = ["status"]), Index(value = ["date_added"])]
)
data class TorrentEntity(
  @PrimaryKey @ColumnInfo(name = "info_hash") val infoHash: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "magnet_uri") val magnetUri: String? = null,
  @ColumnInfo(name = "torrent_file_path") val torrentFilePath: String? = null,
  @ColumnInfo(name = "target_path") val targetPath: String,
  @ColumnInfo(name = "file_name") val fileName: String? = null,
  @ColumnInfo(name = "status") val status: String, // DOWNLOADING, PAUSED, SEEDING, FINISHED, ERROR
  @ColumnInfo(name = "progress") val progress: Float = 0f,
  @ColumnInfo(name = "download_speed") val downloadSpeed: Long = 0,
  @ColumnInfo(name = "upload_speed") val uploadSpeed: Long = 0,
  @ColumnInfo(name = "total_size") val totalSize: Long = 0,
  @ColumnInfo(name = "downloaded_size") val downloadedSize: Long = 0,
  @ColumnInfo(name = "num_peers") val numPeers: Int = 0,
  @ColumnInfo(name = "num_seeds") val numSeeds: Int = 0,
  @ColumnInfo(name = "error") val error: String? = null,
  @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "date_completed") val dateCompleted: Long? = null,
  @ColumnInfo(name = "is_auto_managed") val isAutoManaged: Boolean = true
)
