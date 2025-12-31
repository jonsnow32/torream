package com.tv.apps.zippy.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import com.tv.apps.zippy.model.SubtitleData

/**
 * Entity to store playback information for media files
 * Tracks playback position, subtitle settings, playback speed, aspect ratio, and track selections
 */
@Entity(
  tableName = "media_playback", foreignKeys = [ForeignKey(
    entity = MediaEntity::class,
    parentColumns = ["uri"],
    childColumns = ["media_uri"],
    onDelete = ForeignKey.CASCADE
  )], indices = [Index(value = ["media_uri"], unique = true), Index(value = ["last_played_at"])]
)
data class MediaPlaybackEntity(
  @PrimaryKey @ColumnInfo(name = "media_uri") val mediaUri: String,

  // Playback position in milliseconds
  @ColumnInfo(name = "position") val position: Long = 0,
  // Playback speed (1.0 = normal speed, 0.5 = half speed, 2.0 = double speed)
  @ColumnInfo(name = "speed") val speed: Float = 1.0f,
  // Video aspect ratio override (e.g., "16:9", "4:3", "default")
  @ColumnInfo(name = "aspect_ratio") val aspectRatio: String? = null,
  // Selected audio track index (-1 = default)
  @ColumnInfo(name = "audio_track_index") val audioTrackIndex: Int = -1,
  // Selected text/subtitle track index (-1 = no subtitles)
  @ColumnInfo(name = "text_track_index") val textTrackIndex: Int = -1,
  // Zoom/Scale type for video (e.g., "fit", "fill", "stretch", "crop")
  @ColumnInfo(name = "zoom_type") val zoomType: String = "fit",
  // Subtitle configuration as JSON string
  // Contains: font size, color, background, position, etc.
  @ColumnInfo(name = "subtitles") val subtitles: List<SubtitleData>? = null,
  // Timestamp when last played
  @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = System.currentTimeMillis(),
  // Whether the media was finished/completed
  @ColumnInfo(name = "is_finished") val isFinished: Boolean = false
)
