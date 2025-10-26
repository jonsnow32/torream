package cloud.app.csplayer.ui.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import timber.log.Timber

class FeedPagingSource : PagingSource<Int, FeedData>() {

  // Combine mock data as source
  private val allData = mutableListOf<FeedData>()

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedData> {
    return try {
      val page = params.key ?: 0
      val pageSize = params.loadSize
      val startIndex = page * pageSize
      val endIndex = minOf(startIndex + pageSize, allData.size)

      Timber.d("Loading page $page, startIndex: $startIndex, endIndex: $endIndex")

      if (startIndex >= allData.size) {
        // No more data
        LoadResult.Page(
          data = emptyList(),
          prevKey = if (page > 0) page - 1 else null,
          nextKey = null
        )
      } else {
        val data = allData.subList(startIndex, endIndex)
        LoadResult.Page(
          data = data,
          prevKey = if (page > 0) page - 1 else null,
          nextKey = if (endIndex < allData.size) page + 1 else null
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading feed data")
      LoadResult.Error(e)
    }
  }

  override fun getRefreshKey(state: PagingState<Int, FeedData>): Int? {
    // Return the page key for the page closest to the most recently accessed index
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }
}

