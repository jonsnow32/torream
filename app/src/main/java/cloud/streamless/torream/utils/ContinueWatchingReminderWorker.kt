package cloud.streamless.torream.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.MainActivity
import cloud.streamless.torream.R
import cloud.streamless.torream.media.dao.MediaDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Daily worker that reminds the user about the most recent unfinished video,
 * but only if the app hasn't been opened for a while. Disabled via the
 * "continue watching reminder" switch in Settings > General.
 *
 * When invoked via snooze (INPUT_SNOOZE_MODE=true), skips all time-gate checks
 * and shows a notification for the specific item passed in inputData.
 */
@HiltWorker
class ContinueWatchingReminderWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val mediaDao: MediaDao,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val context = applicationContext
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // Snooze mode: show notification for a specific item without any checks
    if (inputData.getBoolean(INPUT_SNOOZE_MODE, false)) {
      val uri = inputData.getString(EXTRA_MEDIA_URI) ?: return Result.success()
      val name = inputData.getString(EXTRA_MEDIA_NAME) ?: ""
      val position = inputData.getLong(EXTRA_POSITION, 0L)
      if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        showNotification(context, uri, name, position, prefs)
      }
      return Result.success()
    }

    val now = System.currentTimeMillis()

    if (!prefs.getBoolean(context.getString(R.string.continue_watching_reminder_key), true)) {
      return Result.success()
    }
    // User has been in the app recently — no need to remind
    if (now - prefs.getLong(PREF_APP_LAST_OPENED_AT, now) < INACTIVITY_THRESHOLD_MS) {
      return Result.success()
    }
    // Don't remind more often than every few days
    if (now - prefs.getLong(PREF_LAST_REMINDER_AT, 0L) < REMINDER_INTERVAL_MS) {
      return Result.success()
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return Result.success()
    }

    val dismissedUris = prefs.getStringSet(PREF_DISMISSED_URIS, emptySet()) ?: emptySet()

    val item = mediaDao.getRecentlyPlayedWithPlayback(10, 0).firstOrNull {
      val playback = it.playback ?: return@firstOrNull false
      !dismissedUris.contains(it.media.uri) &&
        playback.position > 0L &&
        now - playback.lastPlayedAt < MAX_ITEM_AGE_MS
    } ?: return Result.success()

    showNotification(context, item.media.uri, item.media.name, item.playback?.position ?: 0L, prefs)
    prefs.edit { putLong(PREF_LAST_REMINDER_AT, now) }
    Timber.d("Continue watching reminder shown for ${item.media.name}")
    return Result.success()
  }

  private fun showNotification(
    context: Context,
    mediaUri: String,
    mediaName: String,
    position: Long,
    prefs: android.content.SharedPreferences
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.continue_watching_reminder),
        NotificationManager.IMPORTANCE_DEFAULT
      )
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }

    val contentIntent = Intent(context, MainActivity::class.java).apply {
      action = ACTION_CONTINUE_WATCHING
      putExtra(EXTRA_MEDIA_URI, mediaUri)
      putExtra(EXTRA_MEDIA_NAME, mediaName)
      putExtra(EXTRA_POSITION, position)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context, 0, contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun actionIntent(action: String, requestCode: Int): PendingIntent {
      val intent = Intent(context, ContinueWatchingActionReceiver::class.java).apply {
        this.action = action
        putExtra(EXTRA_MEDIA_URI, mediaUri)
        putExtra(EXTRA_MEDIA_NAME, mediaName)
        putExtra(EXTRA_POSITION, position)
      }
      return PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_baseline_play_arrow_24)
      .setContentTitle(context.getString(R.string.continue_watching_notification_title))
      .setContentText(context.getString(R.string.continue_watching_notification_text, mediaName))
      .setContentIntent(contentPendingIntent)
      .setAutoCancel(true)
      .addAction(
        0,
        context.getString(R.string.cw_snooze_1h),
        actionIntent(ContinueWatchingActionReceiver.ACTION_SNOOZE_1H, 1)
      )
      .addAction(
        0,
        context.getString(R.string.cw_snooze_2h),
        actionIntent(ContinueWatchingActionReceiver.ACTION_SNOOZE_2H, 2)
      )
      .addAction(
        0,
        context.getString(R.string.cw_dont_remind),
        actionIntent(ContinueWatchingActionReceiver.ACTION_DISMISS, 3)
      )
      .build()

    try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
      Timber.e(e, "Notification permission not granted")
    }
  }

  companion object {
    const val WORK_NAME = "continue_watching_reminder"
    const val PREF_APP_LAST_OPENED_AT = "app_last_opened_at"
    const val PREF_DISMISSED_URIS = "continue_watching_dismissed_uris"
    const val ACTION_CONTINUE_WATCHING = "cloud.streamless.torream.CONTINUE_WATCHING"
    const val EXTRA_MEDIA_URI = "media_uri"
    const val EXTRA_MEDIA_NAME = "media_name"
    const val EXTRA_POSITION = "position"
    const val INPUT_SNOOZE_MODE = "snooze_mode"
    const val NOTIFICATION_ID = 9011

    private const val PREF_LAST_REMINDER_AT = "continue_watching_last_reminder_at"
    private const val CHANNEL_ID = "continue_watching_channel"

    // Test mode in debug builds: no inactivity/interval gating, so every run notifies
    private val TEST_MODE = BuildConfig.DEBUG
    private val INACTIVITY_THRESHOLD_MS = if (TEST_MODE) 0L else TimeUnit.DAYS.toMillis(2)
    private val REMINDER_INTERVAL_MS = if (TEST_MODE) 0L else TimeUnit.DAYS.toMillis(3)
    private val MAX_ITEM_AGE_MS = TimeUnit.DAYS.toMillis(30)
  }
}
