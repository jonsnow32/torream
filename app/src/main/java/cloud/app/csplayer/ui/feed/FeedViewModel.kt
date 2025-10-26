package cloud.app.csplayer.ui.feed

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cloud.app.csplayer.model.Audio
import cloud.app.csplayer.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor() : ViewModel() {
  // sample observable data for the fragment
  val title = MutableLiveData("Feed")
  val feedData = MutableStateFlow<List<FeedData>>(emptyList())

  init {
    val sampleFeed = listOf(
      FeedData.AdItem(
        id = "ad_1",
        title = "Sample Ad",
        isNativeAdPlaceholder = true
      ),
      FeedData.VideoItem(
        id = "1",
        title = "Sample Video 1",
        video = Video(
          id = "1",
          title = "Sample Video 1",
          description = "This is a sample video description.",
        )
      ),
      FeedData.VideoItem(
        id = "12",
        title = "Sample Video 1",
        video = Video(
          id = "12",
          title = "Sample Video 1",
          description = "This is a sample video description.",
        )
      ),
      FeedData.VideoItem(
        id = "13",
        title = "Sample Video 1",
        video = Video(
          id = "14",
          title = "Sample Video 1",
          description = "This is a sample video description.",
        )
      ),
      FeedData.AudioItem(
        id = "2",
        title = "Sample Audio 1",
        audio = Audio(
          id = "2",
          title = "Sample Audio 1",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "2",
        title = "Sample Audio 1",
        audio = Audio(
          id = "2",
          title = "Sample Audio 1",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "2",
        title = "Sample Audio 1",
        audio = Audio(
          id = "2",
          title = "Sample Audio 1",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "3",
        title = "Sample Audio 1",
        audio = Audio(
          id = "3",
          title = "Sample Audio2",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "3",
        title = "Sample Audio 1",
        audio = Audio(
          id = "3",
          title = "Sample Audio2",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "3",
        title = "Sample Audio 1",
        audio = Audio(
          id = "3",
          title = "Sample Audio2",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "3",
        title = "Sample Audio 1",
        audio = Audio(
          id = "3",
          title = "Sample Audio2",
          subtitle = "Sample Artist"
        )
      ),
      FeedData.AudioItem(
        id = "3",
        title = "Sample Audio 1",
        audio = Audio(
          id = "3",
          title = "Sample Audio2",
          subtitle = "Sample Artist"
        )
      )
    )
    feedData.value = sampleFeed
  }
}



