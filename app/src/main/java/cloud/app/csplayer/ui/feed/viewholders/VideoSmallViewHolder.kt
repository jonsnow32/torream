package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize

class VideoSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemVideoSmallBinding = ItemVideoSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    binding.fileSize.text = feed.media.size.formatFileSize()
    binding.tvDuration.text = feed.media.duration.formatDuration()
    //binding.imgCover = feed.media.cover
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
