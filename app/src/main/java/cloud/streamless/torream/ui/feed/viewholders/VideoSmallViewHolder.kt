package cloud.streamless.torream.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.streamless.torream.databinding.ItemVideoSmallBinding
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.FeedData
import cloud.streamless.torream.ui.feed.FeedViewHolder
import cloud.streamless.torream.utils.formatDuration
import cloud.streamless.torream.utils.formatFileSize
import cloud.streamless.torream.utils.loadThumbnail

class VideoSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedAction,
  private val filterConfig: cloud.streamless.torream.ui.feed.FeedFilterConfig? = null
) : FeedViewHolder<FeedData.MediaItem>(
  ItemVideoSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ).root
) {
  private val binding: ItemVideoSmallBinding = ItemVideoSmallBinding.bind(itemView)
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title

    // Show/hide duration based on filterConfig
    if (filterConfig?.showDuration == true) {
      binding.tvDuration.text = feed.media.duration.formatDuration()
      binding.tvDuration.visibility = android.view.View.VISIBLE
    } else {
      binding.tvDuration.visibility = android.view.View.GONE
    }

    // Show/hide thumbnail
    if (filterConfig?.showThumbnail == true) {
      binding.imgCover.loadThumbnail(feed.media.uri)
      binding.imgCover.visibility = android.view.View.VISIBLE
    }

    // Update playback progress
    if (filterConfig?.showProgress == true) {
      updatePlaybackProgress(feed.media.duration, feed.media.position)
    } else {
      binding.progressPlayback.visibility = android.view.View.GONE
    }

    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }

  private fun updatePlaybackProgress(duration: Long, position: Long) {
    if (duration > 0 && position > 0) {
      val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
      binding.progressPlayback.progress = progress
      binding.progressPlayback.visibility = android.view.View.VISIBLE
    } else {
      binding.progressPlayback.visibility = android.view.View.GONE
    }
  }
}
