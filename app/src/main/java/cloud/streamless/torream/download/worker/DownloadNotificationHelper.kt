package cloud.streamless.torream.download.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object DownloadNotificationHelper {

  private const val CHANNEL_ID = "download_channel"
  private const val CHANNEL_NAME = "Downloads"
  const val ACTION_OPEN_DOWNLOADS = "cloud.streamless.torream.OPEN_DOWNLOADS"
  const val ACTION_PAUSE_ALL = "cloud.streamless.torream.PAUSE_ALL_DOWNLOADS"
  const val ACTION_CANCEL_ALL = "cloud.streamless.torream.CANCEL_ALL_DOWNLOADS"

  fun createDownloadNotification(
    context: Context,
    @Suppress("unused") taskId: String,
    progress: Int,
    isHttp: Boolean,
    fileName: String? = null
  ): Notification {
    createNotificationChannel(context)

    val type = if (isHttp) "HTTP" else "Torrent"
    val title = fileName ?: "$type Download"
    val contentText = if (progress > 0) {
      "Downloading... $progress%"
    } else {
      "Starting download..."
    }

    // Create intent to open Downloads screen in Library
    val intent = Intent(context, Class.forName("cloud.streamless.torream.MainActivity")).apply {
      action = ACTION_OPEN_DOWNLOADS
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Create Pause All action
    val pauseAllIntent = Intent(context, DownloadNotificationReceiver::class.java).apply {
      action = ACTION_PAUSE_ALL
    }

    val pauseAllPendingIntent = PendingIntent.getBroadcast(
      context,
      1,
      pauseAllIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Create Cancel All action
    val cancelAllIntent = Intent(context, DownloadNotificationReceiver::class.java).apply {
      action = ACTION_CANCEL_ALL
    }
    val cancelAllPendingIntent = PendingIntent.getBroadcast(
      context,
      2,
      cancelAllIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(contentText)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setProgress(100, progress, progress == 0)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .addAction(android.R.drawable.ic_media_pause, "Pause All", pauseAllPendingIntent)
      .addAction(android.R.drawable.ic_delete, "Cancel All", cancelAllPendingIntent)
      .build()
  }

  private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
        description = "Shows download progress"
      }

      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  /**
   * Check if notification permission is granted on Android 13+
   * On older versions, return true since permission is not required
   */
  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
      true // Permission not required on older Android versions
    }
  }
}

