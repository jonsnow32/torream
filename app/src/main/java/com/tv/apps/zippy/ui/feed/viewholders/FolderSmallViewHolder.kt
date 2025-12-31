package com.tv.apps.zippy.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.ItemFolderBinding
import com.tv.apps.zippy.databinding.ItemFolderSmallBinding
import com.tv.apps.zippy.ui.feed.FeedAction
import com.tv.apps.zippy.ui.feed.FeedData
import com.tv.apps.zippy.ui.feed.FeedViewHolder

class FolderSmallViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
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

    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
  }
}
