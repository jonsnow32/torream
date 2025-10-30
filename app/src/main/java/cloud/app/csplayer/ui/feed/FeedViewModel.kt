package cloud.app.csplayer.ui.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.R
import cloud.app.csplayer.media.model.FolderViewMode
import cloud.app.csplayer.media.model.MediaTypeFilter
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.utils.PREFERENCES_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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

  // SharedPreferences for storing display type
  private val sharedPreferences: SharedPreferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  // Display type state - loaded from preferences
  val displayType = MutableStateFlow(loadDisplayType())

  // Folder view mode state - loaded from preferences
  val folderViewMode = MutableStateFlow(loadFolderViewMode())

  // Media type filter state - loaded from preferences
  val mediaTypeFilter = MutableStateFlow(loadMediaTypeFilter())

  // PagingData flow for the adapter - now loads real data from MediaRepository
  // Recreates when displayType, folderViewMode, or mediaTypeFilter changes
  val feedData: Flow<PagingData<FeedData>> = combine(
    displayType,
    folderViewMode,
    mediaTypeFilter
  ) { currentDisplayType, currentFolderViewMode, currentMediaTypeFilter ->
    Triple(currentDisplayType, currentFolderViewMode, currentMediaTypeFilter)
  }.flatMapLatest { (currentDisplayType, currentFolderViewMode, currentMediaTypeFilter) ->
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
          currentDisplayType,
          currentFolderViewMode,
          currentMediaTypeFilter
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

  fun changeDisplayType() {
    val newDisplayType = when (displayType.value) {
      DisplayType.GRID -> DisplayType.LIST
      DisplayType.LIST -> DisplayType.GRID
    }
    displayType.value = newDisplayType
    saveDisplayType(newDisplayType)
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
   * Load display type from SharedPreferences
   */
  private fun loadDisplayType(): DisplayType {
    val savedOrdinal = sharedPreferences.getInt(KEY_DISPLAY_TYPE, DisplayType.GRID.ordinal)
    return DisplayType.entries.getOrNull(savedOrdinal) ?: DisplayType.GRID
  }

  /**
   * Save display type to SharedPreferences
   */
  private fun saveDisplayType(displayType: DisplayType) {
    sharedPreferences.edit {
      putInt(KEY_DISPLAY_TYPE, displayType.ordinal)
    }
    Timber.d("Saved display type: $displayType")
  }

  /**
   * Load folder view mode from SharedPreferences
   */
  private fun loadFolderViewMode(): FolderViewMode {
    val savedValue = sharedPreferences.getString(context.getString(R.string.folder_view_mode_key), "hierarchical")
    return FolderViewMode.fromValue(savedValue)
  }

  /**
   * Load media type filter from SharedPreferences
   */
  private fun loadMediaTypeFilter(): MediaTypeFilter {
    val savedValue = sharedPreferences.getString(context.getString(R.string.media_type_filter_key), "all")
    return MediaTypeFilter.fromValue(savedValue)
  }

  enum class DisplayType {
    GRID,
    LIST
  }

  companion object {
    private const val KEY_DISPLAY_TYPE = "feed_display_type"
  }
}



