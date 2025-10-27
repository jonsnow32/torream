package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemVideoSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class VideoSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemVideoSmallBinding = ItemVideoSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    binding.subtitle.text =
      parent.context.getString(R.string.ms_bytes, feed.media.duration, feed.media.size)
    //binding.imgCover = feed.media.cover
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
