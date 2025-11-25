package cloud.app.csplayer.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "playlists",
  indices = [Index(value = ["created_at"])]
)
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  val id: Long = 0,

  @ColumnInfo(name = "name")
  val name: String,

  @ColumnInfo(name = "description")
  val description: String? = null,

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis(),

  @ColumnInfo(name = "updated_at")
  val updatedAt: Long = System.currentTimeMillis(),

  @ColumnInfo(name = "thumbnail_path")
  val thumbnailPath: String? = null,

  @ColumnInfo(name = "item_count")
  val itemCount: Int = 0
)

