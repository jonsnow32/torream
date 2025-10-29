package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemAudioSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration

class AudioSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemAudioSmallBinding = ItemAudioSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    // Load album art/thumbnail asynchronously
    binding.imgCover.setImageResource(R.drawable.outline_music_note_24)
    binding.tvDuration.text = feed.media.duration.formatDuration()
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
