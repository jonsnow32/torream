package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemFolderBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class FolderViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemFolderBinding = ItemFolderBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.FolderItem>(binding.root) {
  override fun bind(feed: FeedData.FolderItem) {
    binding.title.text = feed.folder.name
    binding.subtitle.text = when {
      feed.folder.childCount == 0 -> parent.context.getString(R.string.items, feed.folder.mediaCount)
      feed.folder.mediaCount == 0 -> parent.context.getString(R.string.folders, feed.folder.childCount)
      else -> parent.context.getString(R.string.folder_items, feed.folder.childCount, feed.folder.mediaCount)
    }
    binding.txtPath.text = feed.folder.path
    //binding.imgCover = feed.folder.cover.toma
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
