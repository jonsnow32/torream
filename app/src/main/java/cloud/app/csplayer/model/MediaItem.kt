package cloud.app.csplayer.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface MediaItem {
  val id: String
  val title: String
  val cover: String?
  val description: String?
  val background: String?
  val subtitle: String?
  val extras: Map<String, String>

  val isPrivate: Boolean get() = false

  fun copy(
    id: String = this.id,
    title: String = this.title,
    cover: String? = this.cover,
    description: String? = this.description,
    subtitle: String? = this.subtitle,
    extras: Map<String, String> = this.extras,
  ): MediaItem = when (this) {
    is Audio -> copy(
      id = id,
      title = title,
      cover = cover,
      description = description,
      subtitle = subtitle,
      extras = extras,
    )
    is Video -> copy(
      id = id,
      title = title,
      cover = cover,
      description = description,
      subtitle = subtitle,
      extras = extras,
    )
    is PlayList -> copy(
      id = id,
      title = title,
      cover = cover,
      description = description,
      subtitle = subtitle,
      extras = extras,
    )
    is Folder -> copy(
      id = id,
      title = title,
      cover = cover,
      description = description,
      subtitle = subtitle,
      extras = extras,
    )
  }
}
