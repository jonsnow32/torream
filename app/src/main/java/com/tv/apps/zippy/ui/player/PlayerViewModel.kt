package com.tv.apps.zippy.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.MimeTypes
import androidx.core.net.toUri
import com.tv.apps.zippy.model.PlaybackData
import com.tv.apps.zippy.model.SubtitleData
import com.tv.apps.zippy.model.SubtitleOrigin
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.ui.subtitles.SubtitleFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class PlayerViewModel @Inject constructor(
  private val application: Application,
  private val arguments: SavedStateHandle
) : AndroidViewModel(application) {

  private val _allLinks = MutableLiveData<List<VideoLink>>()
  val allLinks: LiveData<List<VideoLink>> get() =  _allLinks

  private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
  val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

  private val _currentLinkIndex = MutableLiveData<Int>()
  val currentLinkIndex: LiveData<Int> = _currentLinkIndex

  private val _currentSubtitleIndex = MutableLiveData<Int>()
  val currentSubtitleIndex: LiveData<Int> = _currentSubtitleIndex

  private val _isSameEpisode = MutableLiveData<Boolean>()
  val isSameEpisode: LiveData<Boolean> = _isSameEpisode

  init {
    // Try multiple ways to get PlaybackData (in priority order):
    // 1. From URI (inter-app via FileProvider) - NO SIZE LIMIT
    // 2. From Parcelable (inter-fragment) - ~1MB limit

    val playbackData = getPlaybackDataFromUri()
      ?: arguments.get<PlaybackData>(PlaybackData.KEY_PLAYBACK_DATA)

    if (playbackData != null) {
      // New way: use PlaybackData object
      _currentLinkIndex.postValue(playbackData.videoStartIndex)
      _currentSubtitleIndex.postValue(playbackData.subtitleStartIndex)
      _isSameEpisode.postValue(playbackData.isSameEpisode)
      _currentSubs.postValue(playbackData.subtitles.toSet())
      _allLinks.postValue(playbackData.videoLinks)


    }
  }

  /**
   * Try to read PlaybackData from content:// URI (for inter-app communication)
   * This bypasses Bundle size limit (~1MB)
   */
  private fun getPlaybackDataFromUri(): PlaybackData? {
    return try {
      val uriString = arguments.get<String>(PlaybackData.KEY_PLAYBACK_JSON_URI)
      if (uriString != null) {
        val uri = uriString.toUri()
        PlaybackData.readFromUri(application, uri)
      } else {
        null
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
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
