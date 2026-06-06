package cloud.streamless.torream.model

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
  val path: String,
  val name: String,
  val parentPath: String,
  val modified: Long,
  val mediaCount: Int = 0,  // Number of media files in this folder
  val childCount: Int = 0,  // Number of child folders
  val thumbnail: String? = null
)
