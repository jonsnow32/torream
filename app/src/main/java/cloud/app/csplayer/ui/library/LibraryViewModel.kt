package cloud.app.csplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.media.repository.MediaPlaybackRepository
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.TorrentRepository
import cloud.app.csplayer.ui.feed.FeedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject



@HiltViewModel
class LibraryViewModel @Inject constructor(
  val torrentRepository: TorrentRepository,
  val mediaRepository: MediaRepository,
  val playbackRepository: MediaPlaybackRepository,

) : ViewModel() {


  val section = MutableStateFlow(LibrarySection.HISTORY)

  @OptIn(ExperimentalCoroutinesApi::class)
  val feedData: Flow<PagingData<FeedData>> = section
    .flatMapLatest { section ->
      Pager(
        config = PagingConfig(
          pageSize = 20,
          enablePlaceholders = false
        ),
        pagingSourceFactory = {
          LibraryPagingSource(
            repository = mediaRepository,
            playbackRepository = playbackRepository,
            torrentRepository = torrentRepository,
            section = section
          )
        }
      ).flow.cachedIn(viewModelScope)
    }

}

