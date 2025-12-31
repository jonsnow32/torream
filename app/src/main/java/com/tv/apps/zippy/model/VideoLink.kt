package com.tv.apps.zippy.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class VideoLink(
  val url: String,
  val name: String,
  val headers: Map<String, String> = emptyMap(),
  val subtitles: List<SubtitleData>,

  // Basic media properties
  val width: Int = 0,
  val height: Int = 0,

  // Playback state (integrated from MediaPlaybackEntity)
  val position: Long = 0L,
  val speed: Float = 1.0f,
  val aspectRatio: String? = null,
  val audioTrackIndex: Int = -1,
  val textTrackIndex: Int = -1,
  val zoomType: String = "fit",
  val subtitleConfig: String? = null,
  val isFinished: Boolean = false,
) : Parcelable

