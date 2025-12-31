package com.tv.apps.zippy.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.ItemAudioSmallBinding
import com.tv.apps.zippy.ui.feed.FeedAction
import com.tv.apps.zippy.ui.feed.FeedData
import com.tv.apps.zippy.ui.feed.FeedViewHolder
import com.tv.apps.zippy.utils.formatDuration

class AudioSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedAction,
  private val filterConfig: com.tv.apps.zippy.ui.feed.FeedFilterConfig? = null
) : FeedViewHolder<FeedData.MediaItem>(
  ItemAudioSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ).root
) {
  private val binding: ItemAudioSmallBinding = ItemAudioSmallBinding.bind(itemView)
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title

    // Show/hide thumbnail
    if (filterConfig?.showThumbnail == true) {
      binding.imgCover.setImageResource(R.drawable.outline_music_note_24)
      binding.imgCover.visibility = android.view.View.VISIBLE
    }
    // Show/hide duration based on filterConfig
    if (filterConfig?.showDuration == true) {
      binding.tvDuration.text = feed.media.duration.formatDuration()
      binding.tvDuration.visibility = android.view.View.VISIBLE
    } else {
      binding.tvDuration.visibility = android.view.View.GONE
    }

    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }
}
