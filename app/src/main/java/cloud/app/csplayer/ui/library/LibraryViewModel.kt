package cloud.app.csplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import cloud.app.csplayer.media.repository.MediaPlaybackRepository
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.TorrentRepository
import cloud.app.csplayer.ui.feed.FeedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
  val torrentRepository: TorrentRepository,
  val favoriteRepository: MediaRepository,
  val historyRepository: MediaPlaybackRepository
) : ViewModel() {


  val downloadData: Flow<PagingData<FeedData>>
    get() {
      TODO("get data from merge TorrentRepository HttpRepository when ready")
    }

  val favoriteData: Flow<PagingData<FeedData>>
    get() {
      TODO("get data from FavoriteRepository when ready")
    }
  val historyData: Flow<PagingData<FeedData>>
    get() {
      TODO("get data from HistoryRepository when ready")
    }
}

