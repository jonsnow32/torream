package cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf

import cloud.app.csplayer.model.MediaItem
import cloud.app.csplayer.ui.feed.FeedData

sealed interface ShelfType {

  enum class Type {
    ThreeAudioItem,
    TwoVideoItem
  }

  val id: String

  class ThreeItem(
    override val id: String,
    val items : Triple<FeedData.AudioItem, FeedData.AudioItem?, FeedData.AudioItem?>,
  ) : ShelfType {
    val type = Type.ThreeAudioItem
  }

  class TwoItem(
    override val id: String,
    val items : Pair<FeedData.VideoItem, FeedData.VideoItem?>,
  ) : ShelfType {
    val type = Type.TwoVideoItem
  }
}
