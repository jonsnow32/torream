package cloud.app.csplayer.ui.library

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.TorrentRepository
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedFilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject


@HiltViewModel
class LibraryViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  val torrentRepository: TorrentRepository,
  val mediaRepository: MediaRepository,
  val sharedPreferences: SharedPreferences
) : ViewModel() {

  val filterConfig = MutableStateFlow(FeedFilterConfig.load(sharedPreferences))

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
            torrentRepository = torrentRepository,
            section = section
          )
        }
      ).flow.cachedIn(viewModelScope)
    }

}

