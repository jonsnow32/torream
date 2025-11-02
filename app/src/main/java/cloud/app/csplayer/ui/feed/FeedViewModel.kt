package cloud.app.csplayer.ui.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.R
import cloud.app.csplayer.media.model.MediaTypeFilter
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.utils.PREFERENCES_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Data class to hold feed data parameters for paging source
 */
private data class FeedDataParams(
  val viewMode: FeedFilterConfig.ViewMode,
  val groupMode: FeedFilterConfig.GroupMode,
  val mediaType: MediaTypeFilter,
  val searchQuery: String?
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val mediaRepository: MediaRepository
) : ViewModel() {

  // Title - shows app name by default, or folder name when browsing
  val title = MutableLiveData(context.getString(R.string.app_name))

  // Root folder path - if set, only show files from this folder
  private var rootFolderPath: String? = null

  // SharedPreferences for storing settings
  private val sharedPreferences: SharedPreferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  // Feed filter configuration state - single source of truth
  val filterConfig = MutableStateFlow(FeedFilterConfig.load(context))

  // Derived properties for convenience
  val viewMode: Flow<FeedFilterConfig.ViewMode> = filterConfig.map { config ->
    config.viewMode
  }

  val groupMode: Flow<FeedFilterConfig.GroupMode> = filterConfig.map { config ->
    config.groupMode
  }

  // Media type filter state - loaded from preferences
  val mediaTypeFilter = MutableStateFlow(loadMediaTypeFilter())

  // Search query state
  val searchQuery = MutableStateFlow<String?>(null)

  // PagingData flow for the adapter - now loads real data from MediaRepository
  // Recreates when displayType, folderViewMode, mediaTypeFilter, or searchQuery changes
  val feedData: Flow<PagingData<FeedData>> = combine(
    filterConfig,
    mediaTypeFilter,
    searchQuery
  ) { config, mediaType, query ->
    FeedDataParams(config.viewMode, config.groupMode, mediaType, query)
  }.distinctUntilChanged().flatMapLatest { params ->
    Pager(
      config = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
        initialLoadSize = 20
      ),
      pagingSourceFactory = {
        FeedPagingSource(
          mediaRepository,
          rootFolderPath,
          params.viewMode,
          params.groupMode,
          params.mediaType,
          params.searchQuery
        )
      }
    ).flow
  }.cachedIn(viewModelScope)


  // Expose sync state from MediaRepository for UI feedback
  val syncState = mediaRepository.observeSyncState()

  /**
   * Set root folder path to filter feed
   * @param path Root folder path. If null, loads all folders from MediaRepository.
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

  /**
   * Refresh MediaRepository to sync from MediaStore
   * Call this after permission is granted to trigger data sync
   */
  fun refreshMediaRepository() {
    viewModelScope.launch {
      try {
        mediaRepository.refreshMedia()
        Timber.d("MediaRepository refreshed successfully")
      } catch (e: Exception) {
        Timber.e(e, "Error refreshing media repository")
      }
    }
  }

  /**
   * Load media type filter from SharedPreferences
   */
  private fun loadMediaTypeFilter(): MediaTypeFilter {
    val savedValue =
      sharedPreferences.getString(context.getString(R.string.media_type_filter_key), "all")
    return MediaTypeFilter.fromValue(savedValue)
  }
}



