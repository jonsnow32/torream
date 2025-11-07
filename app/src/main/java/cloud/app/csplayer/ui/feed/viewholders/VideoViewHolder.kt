package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize
import cloud.app.csplayer.utils.loadThumbnail

class VideoViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemVideoBinding = ItemVideoBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    binding.subtitle.text = feed.media.size.formatFileSize()
    binding.txtPath.text = feed.media.path
    binding.tvDuration.text = feed.media.duration.formatDuration()
    // Load thumbnail asynchronously
    binding.imgCover.loadThumbnail(feed.media.uri)
// Update playback progress
    updatePlaybackProgress(feed.media.duration, feed.media.position)

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
