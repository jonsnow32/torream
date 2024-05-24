package cloud.app.csplayer.ui.player

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.INFER_TYPE
import cloud.app.csplayer.utils.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import cloud.app.csplayer.utils.Qualities
import cloud.app.csplayer.utils.SubtitleData
import cloud.app.csplayer.utils.SubtitleHelper
import cloud.app.csplayer.utils.SubtitleOrigin

const val EXTRA_POSITION = "position" // long
const val EXTRA_HEADERS = "headers" // String[] : "User-Agent", "Mozilla compatible/1.0", "Authorization", "(Access Token)", "Extra Key", "(Extra Value)" };
const val EXTRA_TITLE = "title" // string
const val EXTRA_VIDEO_URL = "video_url" // string
const val EXTRA_SUBTITLE_LIST = "subtitles"//string[]
const val EXTRA_SUBTITLE_START_INDEX = "subtitle_start_index" //int

class CSPlayerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

  private val _currentLink = MutableLiveData<Pair<ExtractorLink?, ExtractorUri?>>()
  val currentLink: LiveData<Pair<ExtractorLink?, ExtractorUri?>> = _currentLink

  private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
  val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

  fun initialize(arguments: Bundle?) {
    val videoUrl = arguments?.getString(EXTRA_VIDEO_URL);
    val subtitlesArray =
      arguments?.getStringArray(EXTRA_SUBTITLE_LIST); //format ["lang", "url", "lang", "url" ....]
    val headersArray = arguments?.getStringArray(EXTRA_HEADERS);

    val title = arguments?.getString(EXTRA_TITLE);
    val subtitle_index = arguments?.getInt(EXTRA_SUBTITLE_START_INDEX);
    val headerMap = mutableMapOf<String, String>()

    headersArray?.apply {
      val keys = filterIndexed() { index, s -> index % 2 == 0 }
      val values = filterIndexed() { index, s -> index % 2 != 0 }
      keys.forEachIndexed { index, s ->
        headerMap[s] = values[index]
      }
    }

    val subtitles = mutableSetOf<SubtitleData>()
    subtitlesArray?.apply {
      val keys = filterIndexed() { index, s -> index % 2 == 0 }
      val values = filterIndexed() { index, s -> index % 2 != 0 }
      keys.forEachIndexed { index, s ->
        val languageCode = keys[index]
        subtitles += subtitles.plus(
          SubtitleData(
            SubtitleHelper.fromTwoLettersToLanguage(languageCode) ?: "unknow",
            url = values[index],
            origin = SubtitleOrigin.URL,
            mimeType = values[index].toSubtitleMimeType(),
            headers = headerMap,
            languageCode = languageCode
          )
        )
      }
    }

    videoUrl?.let { url ->
      _currentLink.postValue(
        ExtractorLink(
          "",
          title ?: url,
          url,
          "",
          Qualities.Unknown.value,
          type = INFER_TYPE,
          headers = headerMap
        ) to null
      )
    }
    _currentSubs.postValue(subtitles)
  }


  fun addSubtitles(file: Set<SubtitleData>) {
    val currentSubs = _currentSubs.value ?: emptySet()
    // Prevent duplicates
    val allSubs = (currentSubs + file).distinct().toSet()
    // Do not post if there's nothing new
    // Posting will refresh subtitles which will in turn
    // make the subs to english if previously unselected
    if (allSubs != currentSubs) {
      _currentSubs.postValue(allSubs)
    }
  }
}
