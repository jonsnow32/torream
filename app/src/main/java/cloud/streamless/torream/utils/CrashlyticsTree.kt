package cloud.streamless.torream.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Forwards WARN/ERROR logs to Crashlytics: messages become breadcrumb logs,
 * and any attached Throwable is recorded as a non-fatal exception.
 */
class CrashlyticsTree : Timber.Tree() {
  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    if (priority < Log.WARN) return

    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.log("${tag ?: "Torream"}: $message")
    if (t != null) {
      crashlytics.recordException(t)
    }
  }
}
