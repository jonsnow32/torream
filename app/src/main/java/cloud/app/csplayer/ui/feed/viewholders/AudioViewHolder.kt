package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemAudioBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize
import cloud.app.csplayer.utils.loadThumbnail

class AudioViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemAudioBinding = ItemAudioBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.MediaItem>(binding.root) {
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title
    binding.subtitle.text = feed.media.size.formatFileSize()
    binding.tvDuration.text = feed.media.duration.formatDuration()
    // Load album art/thumbnail asynchronously
    binding.imgCover.loadThumbnail(feed.media.uri)

    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
