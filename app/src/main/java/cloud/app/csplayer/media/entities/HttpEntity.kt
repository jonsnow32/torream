package cloud.app.csplayer.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cloud.app.csplayer.download.DownloadStatus


@Entity(
  tableName = "http_downloads",
  indices = [Index(
    value = ["url"],
    unique = true
  )]
)
data class HttpEntity(
  @PrimaryKey @ColumnInfo(name = "url") val url: String,
  @ColumnInfo(name = "target_path") val targetPath: String,
  @ColumnInfo(name = "temp_path") val tempPath: String? = null,
  @ColumnInfo(name = "total_bytes") val totalBytes: Long = 0L,
  @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0L,
  @ColumnInfo(name = "progress") val progress: Int = 0, // 0..100
  @ColumnInfo(name = "accept_ranges") val acceptRanges: Boolean = false,
  @ColumnInfo(name = "e_tag") val etag: String? = null,
  @ColumnInfo(name = "last_modified") val lastModified: String? = null,
  @ColumnInfo(name = "status") val status: String = DownloadStatus.QUEUED.name, // store enum.name()
  @ColumnInfo(name = "error") val error: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "update_at") val updatedAt: Long = System.currentTimeMillis()
)
