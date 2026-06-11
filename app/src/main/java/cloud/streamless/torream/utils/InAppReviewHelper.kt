package cloud.streamless.torream.utils

import android.app.Activity
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import cloud.streamless.torream.BuildConfig
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Asks for a Play Store review after the user has had a few good playback sessions.
 * The Play API decides whether the dialog is actually shown (quota-limited),
 * so this can be called liberally — but we still gate locally to pick a good moment.
 */
object InAppReviewHelper {

  private const val PREF_SESSION_COUNT = "in_app_review_session_count"
  private const val PREF_LAST_REQUEST_AT = "in_app_review_last_request_at"

  // Test mode in debug builds: trigger after 10s of playback, no cooldown,
  // and use FakeReviewManager since the real dialog needs a Play Store install
  private val TEST_MODE = BuildConfig.DEBUG

  // A session counts as "good" if the user watched at least this long
  private val MIN_SESSION_MS =
    if (TEST_MODE) TimeUnit.SECONDS.toMillis(10) else TimeUnit.MINUTES.toMillis(5)
  private val MIN_SESSIONS = if (TEST_MODE) 1 else 3
  private val REQUEST_COOLDOWN_MS = if (TEST_MODE) 0L else TimeUnit.DAYS.toMillis(60)

  fun onPlaybackSessionEnded(activity: Activity, watchedMs: Long) {
    if (watchedMs < MIN_SESSION_MS) return
    try {
      val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
      val count = prefs.getInt(PREF_SESSION_COUNT, 0) + 1
      prefs.edit { putInt(PREF_SESSION_COUNT, count) }

      val lastRequestAt = prefs.getLong(PREF_LAST_REQUEST_AT, 0L)
      if (count < MIN_SESSIONS) return
      if (System.currentTimeMillis() - lastRequestAt < REQUEST_COOLDOWN_MS) return

      val manager: ReviewManager =
        if (TEST_MODE) FakeReviewManager(activity) else ReviewManagerFactory.create(activity)
      manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
        if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
        manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
          if (TEST_MODE) {
            Toast.makeText(activity, "Review flow launched (test mode)", Toast.LENGTH_SHORT).show()
          }
        }
        prefs.edit {
          putLong(PREF_LAST_REQUEST_AT, System.currentTimeMillis())
          putInt(PREF_SESSION_COUNT, 0)
        }
        Timber.d("In-app review flow launched")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to request in-app review")
    }
  }
}
