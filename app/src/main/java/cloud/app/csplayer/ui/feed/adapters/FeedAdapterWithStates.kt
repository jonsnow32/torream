package cloud.app.csplayer.ui.feed.adapters

import adapters.FeedAdapter
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.ui.adapter.GridAdapter

/**
 * A wrapper that combines a PagingDataAdapter with loading/empty/error state adapters
 * while maintaining GridAdapter interface for proper grid layout support.
 *
 * Uses composition pattern with ConcatAdapter to switch between different state adapters.
 */
class FeedAdapterWithStates(
  private val mainAdapter: FeedAdapter,
  private val loadingAdapter: LoadingAdapter,
  private val emptyAdapter: EmptyAdapter,
  private val errorAdapter: ErrorAdapter
) : GridAdapter {

  private val concatAdapter: ConcatAdapter = ConcatAdapter(
    ConcatAdapter.Config.Builder()
      .setIsolateViewTypes(true)
      .build(),
    mainAdapter
  )

  override val adapter: RecyclerView.Adapter<*> = concatAdapter

  private var isLoading = false
  private var isError = false
  private var isEmpty = false

  init {
    // Listen to load state changes from the PagingDataAdapter
    mainAdapter.addLoadStateListener { loadStates ->
      val refresh = loadStates.refresh

      isLoading = refresh is LoadState.Loading
      isError = refresh is LoadState.Error
      isEmpty = refresh is LoadState.NotLoading && mainAdapter.itemCount == 0

      updateAdapters()
    }
  }

  /**
   * Update error message dynamically based on exception type
   */
  fun updateErrorMessage(message: String?, buttonText: String? = null) {
    errorAdapter.updateErrorMessage(message, buttonText)
  }

  private fun updateAdapters() {
    // Remove all current adapters
    val currentAdapters = concatAdapter.adapters.toList()
    currentAdapters.forEach { concatAdapter.removeAdapter(it) }

    // Add appropriate adapter based on state
    when {
      isLoading -> concatAdapter.addAdapter(loadingAdapter)
      isError -> concatAdapter.addAdapter(errorAdapter)
      isEmpty -> concatAdapter.addAdapter(emptyAdapter)
      else -> concatAdapter.addAdapter(mainAdapter)
    }
  }

  override fun getSpanSize(position: Int, width: Int, count: Int): Int {
    // Determine which adapter this position belongs to
    var currentPosition = position
    for (childAdapter in concatAdapter.adapters) {
      val itemCount = childAdapter.itemCount
      if (currentPosition < itemCount) {
        // Found the adapter for this position
        return when (childAdapter) {
          is GridAdapter -> childAdapter.getSpanSize(currentPosition, width, count)
          else -> count // Full span for non-grid adapters
        }
      }
      currentPosition -= itemCount
    }
    return count // Default to full span
  }
}
