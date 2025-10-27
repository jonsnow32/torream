package cloud.app.csplayer.media.model

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Media(
  val id: Long,
  val uri: String,
  val path: String,
  val name: String,
  val size: Long,
  val duration: Long,
  val width: Int,
  val height: Int,
  val dateModified: Long,
  val mimeType: String
)

// Helper to convert stored string back to Uri when needed
fun Media.toUri(): Uri = Uri.parse(this.uri)
