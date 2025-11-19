package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoBinding
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize
import cloud.app.csplayer.utils.loadThumbnail

class VideoViewHolder(
  parent: ViewGroup,
  val clickListener: FeedAction,
  private val filterConfig: cloud.app.csplayer.ui.feed.FeedFilterConfig? = null
) : FeedViewHolder<FeedData.MediaItem>(
  ItemVideoBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ).root
) {
  private val binding: ItemVideoBinding = ItemVideoBinding.bind(itemView)
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title

    // Show/hide fields based on filterConfig
    if (filterConfig?.showSize == true) {
      binding.subtitle.text = feed.media.size.formatFileSize()
      binding.subtitle.visibility = android.view.View.VISIBLE
    } else {
      binding.subtitle.visibility = android.view.View.GONE
    }

    if (filterConfig?.showPath == true) {
      binding.txtPath.text = feed.media.path
      binding.txtPath.visibility = android.view.View.VISIBLE
    } else {
      binding.txtPath.visibility = android.view.View.GONE
    }

    if (filterConfig?.showDuration == true) {
      binding.tvDuration.text = feed.media.duration.formatDuration()
      binding.tvDuration.visibility = android.view.View.VISIBLE
    } else {
      binding.tvDuration.visibility = android.view.View.GONE
    }

    // Show/hide thumbnail
    if (filterConfig?.showThumbnail == true) {
      binding.imgCover.loadThumbnail(feed.media.uri)
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
