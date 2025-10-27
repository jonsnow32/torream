package cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf

import cloud.app.csplayer.ui.feed.FeedData

sealed interface ShelfItem {

  enum class Type {
    ThreeAudioItem,
    TwoVideoItem
  }

  val id: String

  class ThreeItem(
    override val id: String,
    val items : Triple<FeedData.MediaItem, FeedData.MediaItem?, FeedData.MediaItem?>,
  ) : ShelfItem {
    val type = Type.ThreeAudioItem
  }

  class TwoItem(
    override val id: String,
    val items : Pair<FeedData.MediaItem, FeedData.MediaItem?>,
  ) : ShelfItem {
    val type = Type.TwoVideoItem
  }
}
