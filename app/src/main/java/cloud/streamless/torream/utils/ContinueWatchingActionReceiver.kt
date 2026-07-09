package cloud.streamless.torream.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ContinueWatchingActionReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val uri = intent.getStringExtra(ContinueWatchingReminderWorker.EXTRA_MEDIA_URI) ?: return
    val name = intent.getStringExtra(ContinueWatchingReminderWorker.EXTRA_MEDIA_NAME) ?: ""
    val position = intent.getLongExtra(ContinueWatchingReminderWorker.EXTRA_POSITION, 0L)

    NotificationManagerCompat.from(context).cancel(ContinueWatchingReminderWorker.NOTIFICATION_ID)

    when (intent.action) {
      ACTION_SNOOZE_1H -> scheduleSnooze(context, uri, name, position, 1L)
      ACTION_SNOOZE_2H -> scheduleSnooze(context, uri, name, position, 2L)
      ACTION_DISMISS -> dismissItem(context, uri)
    }
  }

  private fun scheduleSnooze(context: Context, uri: String, name: String, position: Long, hours: Long) {
    val data = Data.Builder()
      .putBoolean(ContinueWatchingReminderWorker.INPUT_SNOOZE_MODE, true)
      .putString(ContinueWatchingReminderWorker.EXTRA_MEDIA_URI, uri)
      .putString(ContinueWatchingReminderWorker.EXTRA_MEDIA_NAME, name)
      .putLong(ContinueWatchingReminderWorker.EXTRA_POSITION, position)
      .build()

    WorkManager.getInstance(context)
      .enqueue(
        OneTimeWorkRequestBuilder<ContinueWatchingReminderWorker>()
          .setInitialDelay(hours, TimeUnit.HOURS)
          .setInputData(data)
          .build()
      )

    Timber.d("Snoozed continue watching for '$name' for ${hours}h")
  }

  private fun dismissItem(context: Context, uri: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val current = prefs.getStringSet(ContinueWatchingReminderWorker.PREF_DISMISSED_URIS, emptySet())
      ?.toMutableSet() ?: mutableSetOf()
    current.add(uri)
    prefs.edit().putStringSet(ContinueWatchingReminderWorker.PREF_DISMISSED_URIS, current).apply()
    Timber.d("Dismissed continue watching for $uri")
  }

  companion object {
    const val ACTION_SNOOZE_1H = "cloud.streamless.torream.CW_SNOOZE_1H"
    const val ACTION_SNOOZE_2H = "cloud.streamless.torream.CW_SNOOZE_2H"
    const val ACTION_DISMISS = "cloud.streamless.torream.CW_DISMISS"
  }
}
