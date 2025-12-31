package com.tv.apps.zippy.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "playlist_items",
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlist_id"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = ["playlist_id"]),
    Index(value = ["media_uri"]),
    Index(value = ["playlist_id", "position"], unique = true)
  ]
)
data class PlaylistItemEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  val id: Long = 0,

  @ColumnInfo(name = "playlist_id")
  val playlistId: Long,

  @ColumnInfo(name = "media_uri")
  val mediaUri: String,

  @ColumnInfo(name = "position")
  val position: Int,

  @ColumnInfo(name = "added_at")
  val addedAt: Long = System.currentTimeMillis()
)

