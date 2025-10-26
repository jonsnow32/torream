package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemAudioSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class AudioSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemAudioSmallBinding = ItemAudioSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.AudioItem>(binding.root) {
  override fun bind(feed: FeedData.AudioItem) {
    binding.title.text = feed.audio.title
    binding.subtitle.text = feed.audio.description
    //binding.imgCover = feed.audio.cover.toma
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
