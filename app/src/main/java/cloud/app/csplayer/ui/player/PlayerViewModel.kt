package cloud.app.csplayer.ui.player

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MimeTypes
import cloud.app.csplayer.model.SubtitleData
import cloud.app.csplayer.model.SubtitleOrigin
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.subtitles.SubtitleFile
import cloud.app.csplayer.utils.SubtitleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

const val EXTRA_POSITION = "position" // long
const val EXTRA_TITLE = "title" // string

const val ARG_PLAYLIST_URLS = "playlist_urls" // string["url1", "name1", "headers1", "url2", "name2", "headers2" ...]
const val EXTRA_VIDEO_START_INDEX = "video_start_index" //int
const val EXTRA_IS_SAME_EPISODE = "is_same_episode" // boolean
const val EXTRA_USE_MPV = "use_mpv" // boolean = "is_use_lag" // boolean

const val EXTRA_SUBTITLE_LIST = "subtitles" // string[]
const val EXTRA_SUBTITLE_START_INDEX = "subtitle_start_index" //int
const val EXTRA_HAS_AD = "has_ad" // boolean

@HiltViewModel
class PlayerViewModel @Inject constructor(private val arguments: SavedStateHandle) : ViewModel() {

  private val _allLinks = MutableLiveData<List<VideoLink>>()
  val allLinks: LiveData<List<VideoLink>> = _allLinks

  private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
  val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

  private val _currentLinkIndex = MutableLiveData<Int>()
  val currentLinkIndex: LiveData<Int> = _currentLinkIndex

  private val _currentSubtitleIndex = MutableLiveData<Int>()
  val currentSubtitleIndex: LiveData<Int> = _currentSubtitleIndex

  private val _isSameEpisode = MutableLiveData<Boolean>()
  val isSameEpisode: LiveData<Boolean> = _isSameEpisode

  init {
    val title = arguments.get<String>(EXTRA_TITLE)
    val position = arguments.get<Long>(EXTRA_POSITION)

    val videoLinks = mutableListOf<VideoLink>()
    // Bundle stores arrays as ArrayList, so we need to handle both cases
    val videoUrlsList = try {
      arguments.get<Array<String>>(ARG_PLAYLIST_URLS)?.toList()
    } catch (e: ClassCastException) {
      @Suppress("UNCHECKED_CAST")
      arguments.get<ArrayList<String>>(ARG_PLAYLIST_URLS)
    }

    videoUrlsList?.apply {
      val urls = filterIndexed { index, _ -> index % 3 == 0 }
      val names = filterIndexed { index, _ -> index % 3 == 1 }
      val headerStrings = filterIndexed { index, _ -> index % 3 == 2 }
      urls.forEachIndexed { index, url ->
        val headerMap = mutableMapOf<String, String>()
        val name = names.getOrNull(index) ?: url
        val headerString = headerStrings.getOrNull(index) ?: ""

        if (headerString.isNotEmpty()) {
          headerString.split(".|.").apply {
            val keys = filterIndexed { idx, _ -> idx % 2 == 0 }
            val values = filterIndexed { idx, _ -> idx % 2 != 0 }
            keys.forEachIndexed { idx, key ->
              values.getOrNull(idx)?.let { value ->
                headerMap[key] = value
              }
            }
          }
        }

        videoLinks.add(
          VideoLink(
            url = url,
            name = name,
            headers = headerMap,
            position = position ?: 0L
          )
        )
      }
    }

    if (videoLinks.isNotEmpty()) {
      // Bundle stores arrays as ArrayList, so we need to handle both cases
      val subtitlesArray = try {
        arguments.get<Array<String>>(EXTRA_SUBTITLE_LIST)?.toList()
      } catch (e: ClassCastException) {
        @Suppress("UNCHECKED_CAST")
        arguments.get<ArrayList<String>>(EXTRA_SUBTITLE_LIST)
      }

      val headerMap = mutableMapOf<String, String>()
      val subtitles = mutableSetOf<SubtitleData>()
      subtitlesArray?.apply {
        val keys = filterIndexed { index, _ -> index % 2 == 0 }
        val values = filterIndexed { index, _ -> index % 2 != 0 }
        keys.forEachIndexed { index, languageCode ->
          values.getOrNull(index)?.let { url ->
            subtitles += SubtitleData(
              SubtitleHelper.fromTwoLettersToLanguage(languageCode) ?: languageCode,
              url = url,
              origin = if (url.startsWith("http")) SubtitleOrigin.URL else SubtitleOrigin.DOWNLOADED_FILE,
              mimeType = url.toSubtitleMimeType(),
              headers = headerMap,
              languageCode = languageCode
            )
          }
        }
      }
      _currentSubs.postValue(subtitles)
      _currentSubtitleIndex.postValue(arguments.get<Int>(EXTRA_SUBTITLE_START_INDEX) ?: 0)
      _isSameEpisode.postValue(arguments.get<Boolean>(EXTRA_IS_SAME_EPISODE) ?: true)
      _allLinks.postValue(videoLinks)
      _currentLinkIndex.postValue(arguments.get<Int>(EXTRA_VIDEO_START_INDEX) ?: 0)
    }
  }

  fun initialize(arguments: Bundle?) {

  }

//  fun getNextLink() : VideoLink? {
//    val currentIndex = _currentLinkIndex.value ?: 0
//    val links = _allLinks.value ?: return null
//    return if (currentIndex < links.size) links[currentIndex] else null
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

  companion object {
    fun String.toSubtitleMimeType(): String {
      return when {
        endsWith("vtt", true) -> MimeTypes.TEXT_VTT
        endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
        endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
      }
    }

    fun getSubtitleData(subtitleFile: SubtitleFile): SubtitleData {
      return SubtitleData(
        name = subtitleFile.lang,
        url = subtitleFile.url,
        origin = SubtitleOrigin.URL,
        mimeType = subtitleFile.url.toSubtitleMimeType(),
        headers = emptyMap(),
        languageCode = subtitleFile.lang
      )
    }
  }
}
