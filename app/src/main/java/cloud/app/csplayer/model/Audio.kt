package cloud.app.csplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class Audio(
  override val id: String = "",
  override val title: String = "",
  override val cover: String? = null,
  override val description: String? = null,
  override val background: String? = null,
  override val subtitle: String? = null,
  override val extras: Map<String, String> = emptyMap(),
  val mediaItems: List<MediaItem> = emptyList(),
) : MediaItem {

}
