package cloud.app.csplayer.ui.feed

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
  @ApplicationContext private val context: Context
) : ViewModel() {

  // Title - shows app name by default, or folder name when browsing
  val title = MutableLiveData(context.getString(R.string.app_name))

  // Root folder path - if set, only show files from this folder
  private var rootFolderPath: String? = null

  // PagingData flow for the adapter - now loads real data from device storage
  val feedData: Flow<PagingData<FeedData>> = Pager(
    config = PagingConfig(
      pageSize = 20,
      enablePlaceholders = false,
      initialLoadSize = 20
    ),
    pagingSourceFactory = { FeedPagingSource(context, rootFolderPath) }
  ).flow.cachedIn(viewModelScope)

  val displayType = MutableStateFlow(DisplayType.GRID)

  /**
   * Set root folder path to filter feed
   * @param path Root folder path. If null, loads all folders from preferences.
   */
  fun setRootFolder(path: String?) {
    rootFolderPath = path
    if (path != null) {
      // Extract folder name from path or URI
      val folderName = when {
        // Content URI (SAF)
        path.startsWith("content://") -> {
          // Extract from URI encoded path
          // e.g., content://.../tree/primary%3AMovies → Movies
          val decoded = android.net.Uri.decode(path)
          decoded.substringAfterLast(':').substringAfterLast('/')
        }
        // Regular file path
        else -> {
          path.substringAfterLast('/')
        }
      }
      title.value = folderName.ifBlank { "Folder" }
    } else {
      // Reset to app name when viewing all folders
      title.value = context.getString(R.string.app_name)
    }
  }

  // TODO: Refactor this to work with PagingData - may need to recreate Pager or use different approach
  fun changeDisplayType() {
    when (displayType.value) {
      DisplayType.GRID -> {
        displayType.value = DisplayType.LIST
      }

      DisplayType.LIST -> {
        displayType.value = DisplayType.GRID
      }
    }
  }


  enum class DisplayType {
    GRID,
    LIST
  }
}



