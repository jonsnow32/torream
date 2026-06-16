package cloud.streamless.torream.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "media",
  indices = [Index(
    value = ["uri"],
    unique = true
  ), Index(value = ["parent_path"]), Index(value = ["media_store_id"])]
)

data class MediaEntity(
  @PrimaryKey @ColumnInfo(name = "uri") val uri: String,
  @ColumnInfo(name = "path") val path: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "parent_path") val parentPath: String,
  @ColumnInfo(name = "size") val size: Long,
  @ColumnInfo(name = "duration") val duration: Long,
  @ColumnInfo(name = "width") val width: Int,
  @ColumnInfo(name = "height") val height: Int,
  @ColumnInfo(name = "date_modified") val dateModified: Long,
  @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
  @ColumnInfo(name = "mime_type") val mimeType: String = "",

  // User metadata (preserved during sync)
  @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
  @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
  @ColumnInfo(name = "custom_metadata") val customMetadata: String? = null,
  @ColumnInfo(name = "is_private") val isPrivate: Boolean = false
)
