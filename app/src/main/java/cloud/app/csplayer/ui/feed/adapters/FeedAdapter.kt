package cloud.app.csplayer.ui.feed.adapters

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.ui.adapter.GridAdapter
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AdViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.AudioViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.FolderViewHolder
import cloud.app.csplayer.ui.feed.viewholders.HttpDownloadViewHolder
import cloud.app.csplayer.ui.feed.viewholders.TorrentDownloadViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoSmallViewHolder
import cloud.app.csplayer.ui.feed.viewholders.VideoViewHolder
import cloud.app.csplayer.ui.feed.viewholders.horizontal.HorizontalListViewHolder


class FeedAdapter(
  private val clickListener: FeedAction,
  private val adManager: AdManager? = null,
  private var filterConfig: cloud.app.csplayer.ui.feed.FeedFilterConfig? = null,
  private val downloadRepository: DownloadRepository
) : PagingDataAdapter<FeedData, FeedViewHolder<*>>(DiffCallback), GridAdapter {

  private val viewPool = RecyclerView.RecycledViewPool()

  fun updateFilterConfig(config: cloud.app.csplayer.ui.feed.FeedFilterConfig) {
    filterConfig = config
  }

  object DiffCallback : DiffUtil.ItemCallback<FeedData>() {
    override fun areItemsTheSame(oldItem: FeedData, newItem: FeedData): Boolean {
      // Items are the same if they have the same ID and type
      return oldItem.id == newItem.id && oldItem.type == newItem.type
    }

    override fun areContentsTheSame(oldItem: FeedData, newItem: FeedData): Boolean {
      // With true pagination from Room, items with same ID should have same content
      // This prevents unnecessary rebinds during pagination
      // If you need to detect actual content changes (e.g., after metadata update),
      // implement proper comparison here
      return when {
        oldItem is FeedData.MediaItem && newItem is FeedData.MediaItem -> {
          // Compare key fields that affect UI
          oldItem.title == newItem.title &&
            oldItem.media.duration == newItem.media.duration &&
            oldItem.media.name == newItem.media.name
        }
        oldItem is FeedData.FolderItem && newItem is FeedData.FolderItem -> {
          oldItem.title == newItem.title &&
            oldItem.folder.mediaCount == newItem.folder.mediaCount
        }
        oldItem is FeedData.HttpDownloadItem && newItem is FeedData.HttpDownloadItem -> {
          // Rebind when progress or pause state or filename changes
          oldItem.title == newItem.title &&
            oldItem.fileName == newItem.fileName &&
            oldItem.progress == newItem.progress &&
            oldItem.status == newItem.status
        }
        oldItem is FeedData.TorrentDownloadItem && newItem is FeedData.TorrentDownloadItem -> {
          // DownloadState is a data class — equality covers all relevant fields
          oldItem.title == newItem.title && oldItem.downloadState == newItem.downloadState
        }
        else -> true // For other types, assume same if IDs match
      }
    }
  }

  override val adapter = this
  override fun getItemViewType(position: Int): Int = runCatching { getItem(position)!! }.getOrNull()?.type?.ordinal ?: 0
  override fun getSpanSize(position: Int, width: Int, count: Int) =
    when (FeedData.Type.entries[getItemViewType(position)]) {
      FeedData.Type.Folder,
      FeedData.Type.PlayList,
      FeedData.Type.HorizontalList,
      FeedData.Type.Video,
      FeedData.Type.Ad,
      FeedData.Type.HTTPDownload,
      FeedData.Type.TorrentDownload,
      FeedData.Type.Audio -> count

      FeedData.Type.VideoSmall -> 2
      FeedData.Type.AudioSmall -> 2
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
      FeedData.Type.Ad -> AdViewHolder(parent, adManager = adManager)
      FeedData.Type.Video -> VideoViewHolder(parent, clickListener, filterConfig)
      FeedData.Type.Audio -> AudioViewHolder(parent, clickListener, filterConfig)
      FeedData.Type.Folder -> FolderViewHolder(parent, clickListener)
      FeedData.Type.PlayList -> TODO()
      FeedData.Type.VideoSmall -> VideoSmallViewHolder(parent, clickListener, filterConfig)
      FeedData.Type.AudioSmall -> AudioSmallViewHolder(parent, clickListener, filterConfig)
      FeedData.Type.FolderSmall -> FolderSmallViewHolder(parent, clickListener)
      FeedData.Type.PlayListSmall -> TODO()
      FeedData.Type.HTTPDownload -> HttpDownloadViewHolder(parent, clickListener, downloadRepository)
      FeedData.Type.TorrentDownload -> TorrentDownloadViewHolder(parent, clickListener, downloadRepository)
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
      is HttpDownloadViewHolder -> if (feed is FeedData.HttpDownloadItem) holder.bind(feed)
      is TorrentDownloadViewHolder -> if (feed is FeedData.TorrentDownloadItem) holder.bind(feed)
      is VideoViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is VideoSmallViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is AudioViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is AudioSmallViewHolder -> if (feed is FeedData.MediaItem) holder.bind(feed)
      is FolderViewHolder,
      is FolderSmallViewHolder -> if (feed is FeedData.FolderItem) holder.bind(feed)
      else -> {}
    }
  }

  override fun onViewRecycled(holder: FeedViewHolder<*>) {
    super.onViewRecycled(holder)
    // Clean up ad resources when ViewHolder is recycled
    when (holder) {
      is AdViewHolder -> holder.onRecycled()
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

}
