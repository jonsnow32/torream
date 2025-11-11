package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize
import cloud.app.csplayer.utils.loadThumbnail

class VideoSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedClickListener,
  private val filterConfig: cloud.app.csplayer.ui.feed.FeedFilterConfig? = null
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
