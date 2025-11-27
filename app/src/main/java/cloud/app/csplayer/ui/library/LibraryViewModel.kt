package cloud.app.csplayer.ui.library

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedFilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject


@HiltViewModel
class LibraryViewModel @Inject constructor(
  val mediaRepository: MediaRepository,
  val downloadRepository: DownloadRepository,
  val favoriteRepository: cloud.app.csplayer.favorites.FavoriteRepository,
  val sharedPreferences: SharedPreferences
) : ViewModel() {

  val filterConfig = MutableStateFlow(FeedFilterConfig.load(sharedPreferences))

  val section = MutableStateFlow(LibrarySection.HISTORY)

  // Trigger to force recreate Pager / PagingSource when necessary (e.g. after delete)
  private val refreshTrigger = MutableStateFlow(0)

  fun invalidatePaging() {
    refreshTrigger.value += 1
  }

  suspend fun clearHistory(): Boolean {
    return mediaRepository.clearHistory().also { success ->
      if (success) {
        invalidatePaging()
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  // Create a Pager per section, then combine emitted PagingData with the live download states
  val feedData: Flow<PagingData<FeedData>> = combine(section, refreshTrigger) { sec, _ -> sec }
    .flatMapLatest { section ->
      Pager(
        config = PagingConfig(
          pageSize = 20,
          enablePlaceholders = false
        ),
        pagingSourceFactory = {
          LibraryPagingSource(
            repository = mediaRepository,
            downloadRepository = downloadRepository,
            favoriteRepository = favoriteRepository,
            section = section
          )
        }
      ).flow
    }
    // Cache the paging flow so it can be safely reused by multiple collectors
    .cachedIn(viewModelScope)
    .let { basePagingFlow ->
      // state flow of download states mapped by id
      val statesFlow =
        downloadRepository.observeAllStates().map { list -> list.associateBy { it.task.id } }

      // Combine latest PagingData with latest download states and map items accordingly
      combine(basePagingFlow, statesFlow) { pagingData, stateById ->
        pagingData.map { feed ->
          when (feed) {
            is FeedData.HttpDownloadItem -> {
              val ds = stateById[feed.id]
              if (ds != null) {
                // Use fileName from task if available, otherwise fallback to parsing path
                val displayName = ds.task.fileName?.substringAfterLast('/')
                  ?: ds.task.targetPath.substringAfterLast('/', ds.task.source.substringAfterLast('/'))
                feed.copy(
                  title = displayName,
                  downloadState = ds
                )
              } else feed
            }

            is FeedData.TorrentDownloadItem -> {
              val ds = stateById[feed.id]
              if (ds != null) {
                val displayName = ds.task.fileName?.substringAfterLast('/')
                  ?: ds.task.source.substringAfterLast('/')
                feed.copy(
                  title = displayName,
                  downloadState = ds
                )
              } else feed
            }

            else -> feed
          }
        }
      }
    }
    .cachedIn(viewModelScope)
}
