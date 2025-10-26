package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemFolderBinding
import cloud.app.csplayer.databinding.ItemFolderSmallBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class FolderSmallViewHolder(
  parent: ViewGroup,
  val clickListener: FeedClickListener,
  val binding: ItemFolderSmallBinding = ItemFolderSmallBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.FolderItem>(binding.root) {
  override fun bind(feed: FeedData.FolderItem) {
    binding.title.text = feed.folder.title
    binding.txtPath.text = feed.folder.path
    //binding.imgCover = feed.folder.cover.toma
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }
}
