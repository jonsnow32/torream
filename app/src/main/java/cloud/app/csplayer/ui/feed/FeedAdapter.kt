package cloud.app.csplayer.ui.feed

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.ui.adapter.GridAdapter
import cloud.app.csplayer.ui.feed.viewholders.AdViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoViewHolder
import cloud.app.csplayer.ui.feed.viewholders.horizontal.HorizontalListViewHolder
import cloud.app.csplayer.utils.observe

class FeedAdapter(viewModel: FeedViewModel, private val clickListener: FeedClickListener) :
  ListAdapter<FeedData, FeedViewHolder<*>>(DiffCallback), GridAdapter {

  private val viewPool = RecyclerView.RecycledViewPool()

  object DiffCallback : DiffUtil.ItemCallback<FeedData>() {
    override fun areContentsTheSame(oldItem: FeedData, newItem: FeedData) = oldItem == newItem
    override fun areItemsTheSame(oldItem: FeedData, newItem: FeedData): Boolean {
      if (newItem.type != oldItem.type) return false
      if (oldItem.id != newItem.id) return false
      return true
    }
  }

  override val adapter = this
  override fun getItemViewType(position: Int): Int = getItem(position).type.ordinal
  override fun getSpanSize(position: Int, width: Int, count: Int) =
    when (FeedData.Type.entries[getItemViewType(position)]) {
      FeedData.Type.Folder,
      FeedData.Type.PlayList,
      FeedData.Type.Ad,
      FeedData.Type.HorizontalList,
      FeedData.Type.Video,
      FeedData.Type.Audio -> count

      FeedData.Type.VideoSmall -> 2
      FeedData.Type.AudioSmall -> 1
      FeedData.Type.FolderSmall -> 2
      FeedData.Type.PlayListSmall -> TODO()
    }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): FeedViewHolder<*> {
    val type = FeedData.Type.entries[viewType]
    return when (type) {
      FeedData.Type.HorizontalList -> HorizontalListViewHolder(parent, clickListener, viewPool)
      FeedData.Type.Ad -> AdViewHolder(parent)
      FeedData.Type.Video -> VideoViewHolder(parent, clickListener)
      FeedData.Type.Audio -> AudioViewHolder(parent, clickListener)
      FeedData.Type.Folder -> FolderViewHolder(parent, clickListener)
      FeedData.Type.PlayList -> TODO()
      FeedData.Type.VideoSmall -> VideoSmallViewHolder(parent, clickListener)
      FeedData.Type.AudioSmall -> AudioSmallViewHolder(parent, clickListener)
      FeedData.Type.FolderSmall -> FolderSmallViewHolder(parent, clickListener)
      FeedData.Type.PlayListSmall -> TODO()
    }
  }

  override fun onBindViewHolder(
    holder: FeedViewHolder<*>,
    position: Int
  ) {
    val feed = runCatching { getItem(position) }.getOrNull() ?: return
    when (holder) {
      is HorizontalListViewHolder -> if (feed is FeedData.HorizontalList) holder.bind(feed)
      is AdViewHolder -> if (feed is FeedData.AdItem) holder.bind(feed)

      is VideoViewHolder,
      is VideoSmallViewHolder -> if (feed is FeedData.VideoItem) holder.bind(feed)

      is AudioViewHolder,
      is AudioSmallViewHolder -> if (feed is FeedData.AudioItem) holder.bind(feed)

      is FolderViewHolder,
      is FolderSmallViewHolder -> if (feed is FeedData.FolderItem) holder.bind(feed)
      else -> {}
    }
  }

  companion object {
    fun Fragment.getFeedAdapter(
      viewModel: FeedViewModel
    ): FeedAdapter {
      val adapter = FeedAdapter(viewModel, this as FeedClickListener)
      observe(viewModel.feedData) {
        //adapter.saveState()
        adapter.submitList(it)
      }
      return adapter
    }
  }
}
