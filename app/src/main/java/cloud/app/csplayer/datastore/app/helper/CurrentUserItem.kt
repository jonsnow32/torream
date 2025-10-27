package cloud.app.csplayer.datastore.app.helper

import kotlinx.serialization.Serializable

@Serializable
data class CurrentUserItem(
  val extensionId: String,
  val id: String?
)

