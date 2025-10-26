package cloud.app.csplayer.ui.feed

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cloud.app.csplayer.ui.feed.MockData.sampleFeed1
import cloud.app.csplayer.ui.feed.MockData.smallFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor() : ViewModel() {

  // sample observable data for the fragment
  val title = MutableLiveData("Feed")
  val feedData = MutableStateFlow<List<FeedData>>(emptyList())

  val displayType = MutableStateFlow(DisplayType.GRID)

  init {
    feedData.value = smallFeed + sampleFeed1
  }


  fun changeDisplayType() {
    when (displayType.value) {
      DisplayType.GRID -> {
        feedData.value = sampleFeed1
        displayType.value = DisplayType.LIST
      }
      DisplayType.LIST -> {
        displayType.value = DisplayType.GRID
        feedData.value = smallFeed
      }
    }
  }


  enum class DisplayType {
    GRID,
    LIST
  }
}



