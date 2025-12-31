package com.tv.apps.zippy.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.ItemPlaylistSmallBinding
import com.tv.apps.zippy.ui.feed.FeedAction
import com.tv.apps.zippy.ui.feed.FeedData
import com.tv.apps.zippy.ui.feed.FeedViewHolder

class PlaylistSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val binding: ItemPlaylistSmallBinding = ItemPlaylistSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.PlaylistItem>(binding.root) {

  override fun bind(feed: FeedData.PlaylistItem) {
    binding.title.text = feed.title

    // Show item count
    val itemCountText = parent.context.resources.getQuantityString(
      R.plurals.playlist_items,
      feed.itemCount,
      feed.itemCount
    )
    binding.subtitle.text = itemCountText


    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }

    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }
}

