package adapters

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.ui.adapter.GridAdapter
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.ui.feed.FeedViewModel
import cloud.app.csplayer.ui.feed.adapters.EmptyAdapter
import cloud.app.csplayer.ui.feed.adapters.ErrorAdapter
import cloud.app.csplayer.ui.feed.adapters.FeedAdapterWithStates
import cloud.app.csplayer.ui.feed.adapters.LoadingAdapter
import cloud.app.csplayer.ui.feed.viewholders.AdViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoViewHolder
import cloud.app.csplayer.ui.feed.viewholders.horizontal.HorizontalListViewHolder
import cloud.app.csplayer.utils.observe


class FeedAdapter(private val clickListener: FeedClickListener) :
  PagingDataAdapter<FeedData, FeedViewHolder<*>>(DiffCallback), GridAdapter {

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
  override fun getItemViewType(position: Int): Int = runCatching { getItem(position)!! }.getOrNull()?.type?.ordinal ?: 0
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

  @Suppress("UNCHECKED_CAST")
  override fun onBindViewHolder(
    holder: FeedViewHolder<*>,
    position: Int
  ) {
    val feed = getItem(position) ?: return
    when (holder) {
      is HorizontalListViewHolder -> if (feed is FeedData.HorizontalList) holder.bind(feed)
      is AdViewHolder -> if (feed is FeedData.AdItem) holder.bind(feed)
      is VideoViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is VideoSmallViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is AudioViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is AudioSmallViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is FolderViewHolder,
      is FolderSmallViewHolder -> if (feed is FeedData.FolderItem) holder.bind(feed)
      else -> {}
    }
  }

  /**
   * Wraps this adapter with loading/empty/error state adapters.
   * The returned wrapper maintains GridAdapter interface for proper grid layout support.
   *
   * Usage:
   * ```
   * val adapterWithStates = adapter.withLoadingStates {
   *   // Retry logic when error occurs
   *   viewModel.refresh()
   * }
   * recyclerView.adapter = adapterWithStates
   * ```
   *
   * The wrapper automatically:
   * - Shows LoadingAdapter when PagingData is loading
   * - Shows EmptyAdapter when data is empty
   * - Shows ErrorAdapter when there's an error
   * - Shows FeedAdapter when data is available
   *
   * @param errorMessage Custom error message (e.g., for permission error)
   * @param buttonText Custom button text (e.g., "Grant Permission")
   * @param onRetry Callback when user clicks retry button in error state
   * @return A FeedAdapterWithStates wrapper that implements GridAdapter
   */
  fun withLoadingStates(
    errorMessage: String? = null,
    buttonText: String? = null,
    onRetry: () -> Unit = {}
  ): FeedAdapterWithStates {
    return FeedAdapterWithStates(
      mainAdapter = this,
      loadingAdapter = LoadingAdapter(),
      emptyAdapter = EmptyAdapter(),
      errorAdapter = ErrorAdapter(errorMessage, buttonText, onRetry)
    )
  }

  companion object {

    fun Fragment.getFeedAdapter(
      viewModel: FeedViewModel
    ): FeedAdapter {
      val adapter = FeedAdapter(this as FeedClickListener)
      observe(viewModel.feedData) {
        adapter.submitData(lifecycle, it)
      }
      return adapter
    }
  }
}
