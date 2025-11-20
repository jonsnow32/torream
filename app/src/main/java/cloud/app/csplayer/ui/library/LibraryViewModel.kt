package cloud.app.csplayer.ui.library

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.cachedIn
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedFilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class LibraryViewModel @Inject constructor(
  val mediaRepository: MediaRepository,
  val downloadRepository: DownloadRepository,
  val sharedPreferences: SharedPreferences
) : ViewModel() {

  val filterConfig = MutableStateFlow(FeedFilterConfig.load(sharedPreferences))

  val section = MutableStateFlow(LibrarySection.HISTORY)

  // Trigger to force recreate Pager / PagingSource when necessary (e.g. after delete)
  private val refreshTrigger = MutableStateFlow(0)

  fun invalidatePaging() {
    refreshTrigger.value += 1
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
                // Use title from task if available, otherwise fallback to parsing path
                val fileName = ds.task.title ?: ds.task.targetPath.substringAfterLast('/', ds.task.source.substringAfterLast('/'))
                feed.copy(
                  title = fileName,
                  fileName = fileName,
                  progress = ds.progress,
                  status = ds.status
                )
              } else feed
            }

            is FeedData.TorrentDownloadItem -> {
              val ds = stateById[feed.id]
              if (ds != null) {
                val status = when (ds.status) {
                  DownloadStatus.QUEUED,
                  DownloadStatus.DOWNLOADING -> TorrentDownloadStatus.DOWNLOADING

                  DownloadStatus.PAUSED -> TorrentDownloadStatus.PAUSED
                  DownloadStatus.SEEDING -> TorrentDownloadStatus.SEEDING
                  DownloadStatus.FINISHED -> TorrentDownloadStatus.FINISHED
                  DownloadStatus.COMPLETED -> TorrentDownloadStatus.FINISHED
                  DownloadStatus.FAILED,
                  DownloadStatus.CANCELED -> TorrentDownloadStatus.ERROR
                }
                Timber.i("progress for torrent ${feed.id} is ${ds.progress}")
                // Use title from task if available (set by worker), otherwise fallback to parsing
                val torrentName = ds.task.title ?: ds.task.targetPath.substringAfterLast('/', ds.task.source)
                val updated = feed.torrentState.copy(
                  name = torrentName,
                  status = status,
                  progress = ds.progress / 100f, // Convert from percentage (0-100) to fraction (0.0-1.0)
                  downloadSpeed = ds.downloadSpeedBytesPerSec,
                  downloadedSize = ds.downloadedBytes,
                  error = ds.error
                )

                feed.copy(
                  title = torrentName,
                  torrentState = updated
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
