package cloud.streamless.torream.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Custom Firebase Analytics events for video playback and downloads. */
object AnalyticsLogger {
  private const val EVENT_VIDEO_PLAY = "video_play"
  private const val EVENT_DOWNLOAD_START = "download_start"

  fun logVideoPlay(context: Context, title: String?) {
    FirebaseAnalytics.getInstance(context).logEvent(EVENT_VIDEO_PLAY, Bundle().apply {
      putString("title", title ?: "unknown")
    })
  }

  fun logDownloadStart(context: Context, type: String, fileName: String?) {
    FirebaseAnalytics.getInstance(context).logEvent(EVENT_DOWNLOAD_START, Bundle().apply {
      putString("download_type", type)
      putString("file_name", fileName ?: "unknown")
    })
  }
}
