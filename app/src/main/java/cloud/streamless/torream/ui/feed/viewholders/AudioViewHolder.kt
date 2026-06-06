package cloud.streamless.torream.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.ItemAudioBinding
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.FeedData
import cloud.streamless.torream.ui.feed.FeedViewHolder
import cloud.streamless.torream.utils.formatDuration
import cloud.streamless.torream.utils.formatFileSize
import cloud.streamless.torream.utils.loadThumbnail

class AudioViewHolder(
  parent: ViewGroup,
  val clickListener: FeedAction,
  private val filterConfig: cloud.streamless.torream.ui.feed.FeedFilterConfig? = null
) : FeedViewHolder<FeedData.MediaItem>(
  ItemAudioBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ).root
) {
  private val binding: ItemAudioBinding = ItemAudioBinding.bind(itemView)
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title

    // Show/hide size based on filterConfig
    if (filterConfig?.showSize == true) {
      binding.subtitle.text = feed.media.size.formatFileSize()
      binding.subtitle.visibility = android.view.View.VISIBLE
    } else {
      binding.subtitle.visibility = android.view.View.GONE
    }

    // Show/hide duration based on filterConfig
    if (filterConfig?.showDuration == true) {
      binding.tvDuration.text = feed.media.duration.formatDuration()
      binding.tvDuration.visibility = android.view.View.VISIBLE
    }

    // Show/hide path based on filterConfig
    if (filterConfig?.showPath == true) {
      binding.path.text = feed.media.path
      binding.path.visibility = android.view.View.VISIBLE
    } else {
      binding.path.visibility = android.view.View.GONE
    }

    // Load album art/thumbnail asynchronously
    if (filterConfig?.showThumbnail == true) {
      binding.imgCover.setImageResource(R.drawable.outline_music_note_24)
      binding.imgCover.visibility = android.view.View.VISIBLE
    } else {
      binding.imgCover.visibility = android.view.View.GONE
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
