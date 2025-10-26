package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemVideoBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class VideoViewHolder(
  parent: ViewGroup,
  val binding: ItemVideoBinding = ItemVideoBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.VideoItem>(binding.root) {
  override fun bind(feed: FeedData.VideoItem) {
    binding.title.text = feed.video.title
    binding.subtitle.text = feed.video.description
    //binding.imgCover = feed.video.cover.toma
  }
}
