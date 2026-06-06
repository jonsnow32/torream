package cloud.streamless.torream.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.ItemAudioSmallBinding
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.FeedData
import cloud.streamless.torream.ui.feed.FeedViewHolder
import cloud.streamless.torream.utils.formatDuration

class AudioSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedAction,
  private val filterConfig: cloud.streamless.torream.ui.feed.FeedFilterConfig? = null
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
