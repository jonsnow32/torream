package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemPlaylistSmallBinding
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class PlaylistSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val binding: ItemPlaylistSmallBinding = ItemPlaylistSmallBinding.inflate(
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


    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }

    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }
}

