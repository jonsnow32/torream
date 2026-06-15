package cloud.streamless.torream.model

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
  val path: String,
  val name: String,
  val parentPath: String,
  val modified: Long,
  val mediaCount: Int = 0,
  val childCount: Int = 0,
  val thumbnail: String? = null,
  val isPrivate: Boolean = false
)
