package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemPlaylistBinding
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class PlaylistViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val binding: ItemPlaylistBinding = ItemPlaylistBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.PlaylistItem>(binding.root) {

  override fun bind(feed: FeedData.PlaylistItem) {
    binding.title.text = feed.title

    // Show item count
    val itemCountText = parent.context.resources.getQuantityString(
      R.plurals.playlist_items,
      feed.itemCount,
      feed.itemCount
    )
    binding.subtitle.text = itemCountText

    // Show description if available
    if (feed.description.isNullOrBlank()) {
      binding.description.isVisible = false
    } else {
      binding.description.isVisible = true
      binding.description.text = feed.description
    }


    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }

    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }
}

