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
const val EXTRA_TITLE = "title" // string

const val EXTRA_VIDEO_URLS_NAME_HEADERS = "video_url_headers" // string["url1", "base64(headers1)", "url2", "base64(headers2)" ...]
const val EXTRA_VIDEO_START_INDEX = "video_start_index" //int
const val EXTRA_IS_SAME_EPISODE = "is_same_episode" // boolean

const val EXTRA_SUBTITLE_LIST = "subtitles" // string[]
const val EXTRA_SUBTITLE_START_INDEX = "subtitle_start_index" //int
const val EXTRA_HAS_AD = "has_ad" // boolean


class CSPlayerViewModel(private val arguments: SavedStateHandle) : ViewModel() {

  private val _allLinks = MutableLiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>>()
  val allLinks: LiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>> = _allLinks

  private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
  val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

  private val _currentLinkIndex = MutableLiveData<Int>()
  val currentLinkIndex: LiveData<Int> = _currentLinkIndex

  private val _currentSubtitleIndex = MutableLiveData<Int>()
  val currentSubtitleIndex: LiveData<Int> = _currentSubtitleIndex

  private val _isSameEpisode = MutableLiveData<Boolean>()
  val isSameEpisode: LiveData<Boolean> = _isSameEpisode

  init {
    val title = arguments.get<String>(EXTRA_TITLE);
    val position = arguments.get<Long>(EXTRA_POSITION)

    val extractorLinks = mutableSetOf<Pair<ExtractorLink?, ExtractorUri?>>();
    val videoUrls = arguments.get<Array<String>>(EXTRA_VIDEO_URLS_NAME_HEADERS);
    videoUrls?.apply {
      val urls = filterIndexed() { index, s -> index % 3 == 0 }
      val names = filterIndexed() { index, s -> index % 3 == 1 }
      val headerStrings = filterIndexed() { index, s -> index % 3 == 2 }
      urls.forEachIndexed { index, s ->
        val headerMap = mutableMapOf<String, String>()
        val source = names[index]
        if(!headerStrings[index].isNullOrEmpty()) {
          headerStrings[index].split(".|.").apply {
            val keys = filterIndexed() { index, s -> index % 2 == 0 }
            val values = filterIndexed() { index, s -> index % 2 != 0 }
            keys.forEachIndexed { index, s ->
              headerMap[s] = values[index]
            }
          }
        }
        val pair = ExtractorLink(
          source,
          title ?: s,
          s,
          "",
          Qualities.Unknown.value,
          type = INFER_TYPE,
          headers = headerMap,
          position = position ?: 0
        ) to null
        extractorLinks.add(pair)
      }
    }

    if(extractorLinks.isNotEmpty()) {
      val subtitlesArray =
        arguments.get<Array<String>>(EXTRA_SUBTITLE_LIST); //format ["lang", "url", "lang", "url" ....]
      val headerMap = mutableMapOf<String, String>()
      val subtitles = mutableSetOf<SubtitleData>()
      subtitlesArray?.apply {
        val keys = filterIndexed() { index, s -> index % 2 == 0 }
        val values = filterIndexed() { index, s -> index % 2 != 0 }
        keys.forEachIndexed { index, s ->
          val languageCode = keys[index]
          subtitles += subtitles.plus(
            SubtitleData(
              SubtitleHelper.fromTwoLettersToLanguage(languageCode) ?: languageCode,
              url = values[index],
              origin = if(values[index].startsWith("http")) SubtitleOrigin.URL else SubtitleOrigin.DOWNLOADED_FILE,
              mimeType = values[index].toSubtitleMimeType(),
              headers = headerMap,
              languageCode = languageCode
            )
          )
        }
      }
      _currentSubs.postValue(subtitles)
      _currentSubtitleIndex.postValue(arguments.get<Int>(EXTRA_SUBTITLE_START_INDEX) ?: 0)
      _isSameEpisode.postValue(arguments.get<Boolean>(EXTRA_IS_SAME_EPISODE) ?: true)
      _allLinks.postValue(extractorLinks);
      _currentLinkIndex.postValue(arguments.get<Int>(EXTRA_VIDEO_START_INDEX) ?: 0)
    }
  }

  fun initialize(arguments: Bundle?) {

  }

//  fun getNextLink() : Pair<ExtractorLink?, ExtractorUri?>? {
//    _allLinks.value?.forEachIndexed { index, pair ->
//      if(index == _currentLinkIndex.value)
//        return pair
//    }
//    return null;
//  }

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
