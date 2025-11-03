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
  private var currentState: AdapterState = AdapterState.MAIN

  private enum class AdapterState {
    LOADING, ERROR, EMPTY, MAIN
  }

  init {
    // Listen to load state changes from the PagingDataAdapter
    mainAdapter.addLoadStateListener { loadStates ->
      val refresh = loadStates.refresh

      val newIsLoading = refresh is LoadState.Loading
      val newIsError = refresh is LoadState.Error
      val newIsEmpty = refresh is LoadState.NotLoading && mainAdapter.itemCount == 0

      // Only update if state actually changed
      if (newIsLoading != isLoading || newIsError != isError || newIsEmpty != isEmpty) {
        isLoading = newIsLoading
        isError = newIsError
        isEmpty = newIsEmpty
        updateAdapters()
      }
    }
  }

  /**
   * Update error message dynamically based on exception type
   */
  fun updateErrorMessage(message: String?, buttonText: String? = null) {
    errorAdapter.updateErrorMessage(message, buttonText)
  }

  private fun updateAdapters() {
    // Determine the new state
    val newState = when {
      isLoading -> AdapterState.LOADING
      isError -> AdapterState.ERROR
      isEmpty -> AdapterState.EMPTY
      else -> AdapterState.MAIN
    }

    // Only swap adapters if state actually changed
    if (newState != currentState) {
      currentState = newState

      // Remove all current adapters
      val currentAdapters = concatAdapter.adapters.toList()
      currentAdapters.forEach { concatAdapter.removeAdapter(it) }

      // Add appropriate adapter based on state
      when (newState) {
        AdapterState.LOADING -> concatAdapter.addAdapter(loadingAdapter)
        AdapterState.ERROR -> concatAdapter.addAdapter(errorAdapter)
        AdapterState.EMPTY -> concatAdapter.addAdapter(emptyAdapter)
        AdapterState.MAIN -> concatAdapter.addAdapter(mainAdapter)
      }
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
