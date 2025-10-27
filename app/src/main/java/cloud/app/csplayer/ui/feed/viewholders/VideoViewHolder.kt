package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize

class VideoViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemVideoBinding = ItemVideoBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    binding.subtitle.text = "${feed.media.duration.formatDuration()} • ${feed.media.size.formatFileSize()}"
    //binding.imgCover = feed.media.cover
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
