package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemFolderSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class FolderSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemFolderSmallBinding = ItemFolderSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.FolderItem>(binding.root) {
  override fun bind(feed: FeedData.FolderItem) {
    binding.title.text = feed.folder.name
    binding.txtPath.text = feed.folder.path

    binding.itemCount.text = when {
      feed.folder.childCount == 0 -> parent.context.getString(R.string.items, feed.folder.mediaCount)
      feed.folder.mediaCount == 0 -> parent.context.getString(R.string.folders, feed.folder.childCount)
      else -> parent.context.getString(R.string.folder_items, feed.folder.childCount, feed.folder.mediaCount)
    }

    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
