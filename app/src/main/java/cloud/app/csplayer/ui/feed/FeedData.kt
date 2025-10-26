package cloud.app.csplayer.ui.feed

import cloud.app.csplayer.databinding.ItemAudioBinding
import cloud.app.csplayer.databinding.ItemVideoBinding
import cloud.app.csplayer.model.Audio
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.model.MediaItem
import cloud.app.csplayer.model.Video

/**
 * Feed item sealed hierarchy used by the Feed screen.
 */
sealed class FeedData {

  enum class Type {
    Video,
    VideoSmall,
    Audio,
    AudioSmall,
    Folder,
    FolderSmall,
    PlayList,
    PlayListSmall,
    Ad,
    HorizontalList
  }

  abstract val id: String
  abstract val title: String
  abstract val type: Type

  data class AdItem(
    override val id: String,
    override val title: String = "",
    val isNativeAdPlaceholder: Boolean = false
  ) : FeedData() {
    override val type: Type = Type.Ad
  }

  data class HorizontalList(
    override val id: String,
    override val title: String,
    val items: List<MediaItem>
  ) : FeedData() {
    override val type: Type = Type.HorizontalList
  }

  data class VideoItem(
    override val id: String,
    override val title: String,
    val video: Video,
    override val type: Type = Type.Video
  ) : FeedData() {

    init {
      require(type == Type.Video || type == Type.VideoSmall) {
        "VideoItem.type must be Video or VideoSmall, but was $type"
      }
    }

    companion object {
      fun ItemVideoBinding.bind(item: VideoItem) {
        title.text = item.video.title
        subtitle.text = item.video.subtitle
        // Load cover image, etc.
      }
    }
  }

  data class AudioItem(
    override val id: String,
    override val title: String,
    override val type: Type = Type.Audio,
    val audio: Audio,
  ) : FeedData() {

    init {
      require(type == Type.Audio || type == Type.AudioSmall) {
        "AudioItem.type must be Audio or AudioSmall, but was $type"
      }
    }

    companion object {
      fun ItemAudioBinding.bind(item: AudioItem) {
        title.text = item.audio.title
        subtitle.text = item.audio.subtitle
        // Load cover image, etc.
      }
    }
  }

  data class FolderItem(
    override val id: String,
    override val title: String,
    override var type: Type = Type.Folder,
    val folder: Folder,
  ) : FeedData() {

    init {
      require(type == Type.Folder || type == Type.FolderSmall) {
        "FolderItem.type must be Folder or FolderSmall, but was $type"
      }
    }
  }
}
