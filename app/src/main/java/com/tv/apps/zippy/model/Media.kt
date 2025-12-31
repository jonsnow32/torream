package com.tv.apps.zippy.model

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class Media(
  val id: Long,
  val uri: String,
  val path: String,
  val name: String,
  val size: Long,
  val duration: Long,
  val width: Int,
  val height: Int,
  val dateModified: Long,
  val mimeType: String,

  //playback info
  val position: Long = 0L,
  val speed: Float = 1.0f,
  val aspectRatio: String? = null,
  val audioTrackIndex: Int = -1,
  val textTrackIndex: Int = -1,
  val zoomType: String = "fit",
  val subtitles: List<SubtitleData>? = null,
  val isFinished: Boolean = false,
  val plays: Int = 0,
)

// Helper to convert stored string back to Uri when needed
fun Media.toUri(): Uri = Uri.parse(this.uri)
