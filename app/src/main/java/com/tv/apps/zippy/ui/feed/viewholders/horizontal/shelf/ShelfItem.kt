package com.tv.apps.zippy.ui.feed.viewholders.horizontal.shelf

import com.tv.apps.zippy.ui.feed.FeedData

sealed interface ShelfItem {

  enum class Type {
    ThreeAudioItem,
    TwoVideoItem
  }

  val id: String

  data class ThreeItem(
    override val id: String,
    val items : Triple<FeedData.MediaItem, FeedData.MediaItem?, FeedData.MediaItem?>,
  ) : ShelfItem {
    val type = Type.ThreeAudioItem
  }

  data class TwoItem(
    override val id: String,
    val items : Pair<FeedData.MediaItem, FeedData.MediaItem?>,
  ) : ShelfItem {
    val type = Type.TwoVideoItem
  }
}
