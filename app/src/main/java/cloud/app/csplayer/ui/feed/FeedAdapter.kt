package cloud.app.csplayer.ui.feed

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.ui.adapter.GridAdapter
import cloud.app.csplayer.ui.feed.viewholders.AdViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoViewHolder
import cloud.app.csplayer.ui.feed.viewholders.horizontal.HorizontalListViewHolder
import cloud.app.csplayer.utils.observe
import kotlinx.coroutines.flow.combine

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
      FeedData.Type.HorizontalList -> count
      FeedData.Type.Video -> 2
      FeedData.Type.Audio -> 1
    }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): FeedViewHolder<*> {
    val type = FeedData.Type.entries[viewType]
    return when (type) {
      FeedData.Type.HorizontalList -> HorizontalListViewHolder(parent, clickListener, viewPool)
      FeedData.Type.Ad -> AdViewHolder(parent)
      FeedData.Type.Video -> VideoViewHolder(parent)
      FeedData.Type.Audio -> AudioViewHolder(parent)
      FeedData.Type.Folder -> FolderViewHolder(parent)
      FeedData.Type.PlayList -> TODO()
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
      is VideoViewHolder -> if (feed is FeedData.VideoItem) holder.bind(feed)
      is AudioViewHolder -> if (feed is FeedData.AudioItem) holder.bind(feed)
      is FolderViewHolder -> if (feed is FeedData.FolderItem) holder.bind(feed)
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
