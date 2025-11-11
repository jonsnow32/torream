package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemAudioBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.formatDuration
import cloud.app.csplayer.utils.formatFileSize
import cloud.app.csplayer.utils.loadThumbnail

class AudioViewHolder(
  parent: ViewGroup,
  val clickListener: FeedClickListener,
  private val filterConfig: cloud.app.csplayer.ui.feed.FeedFilterConfig? = null
) : FeedViewHolder<FeedData.MediaItem>(
  ItemAudioBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ).root
) {
  private val binding: ItemAudioBinding = ItemAudioBinding.bind(itemView)
  override fun bind(feed: FeedData.MediaItem) {
    binding.title.text = feed.title

    // Show/hide size based on filterConfig
    if (filterConfig?.showSize == true) {
      binding.subtitle.text = feed.media.size.formatFileSize()
      binding.subtitle.visibility = android.view.View.VISIBLE
    } else {
      binding.subtitle.visibility = android.view.View.GONE
    }

    // Show/hide duration based on filterConfig
    if (filterConfig?.showDuration == true) {
      binding.tvDuration.text = feed.media.duration.formatDuration()
      binding.tvDuration.visibility = android.view.View.VISIBLE
    }

    // Show/hide path based on filterConfig
    if (filterConfig?.showPath == true) {
      binding.path.text = feed.media.path
      binding.path.visibility = android.view.View.VISIBLE
    } else {
      binding.path.visibility = android.view.View.GONE
    }

    // Load album art/thumbnail asynchronously
    if (filterConfig?.showThumbnail == true) {
      binding.imgCover.setImageResource(R.drawable.outline_music_note_24)
      binding.imgCover.visibility = android.view.View.VISIBLE
    } else {
      binding.imgCover.visibility = android.view.View.GONE
    }

    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
