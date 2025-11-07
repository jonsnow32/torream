package cloud.app.csplayer.media.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class representing a Media entity with its associated Playback information
 * Uses Room's @Embedded and @Relation annotations for automatic joining
 */
data class MediaWithPlayback(
  @Embedded val media: MediaEntity,

  @Relation(
    parentColumn = "uri",
    entityColumn = "media_uri"
  )
  val playback: MediaPlaybackEntity?
)

