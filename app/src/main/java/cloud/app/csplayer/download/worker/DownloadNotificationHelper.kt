package cloud.app.csplayer.download.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cloud.app.csplayer.R

object DownloadNotificationHelper {

  private const val CHANNEL_ID = "download_channel"
  private const val CHANNEL_NAME = "Downloads"

  fun createDownloadNotification(
    context: Context,
    taskId: String,
    progress: Int,
    isHttp: Boolean
  ): Notification {
    createNotificationChannel(context)

    val type = if (isHttp) "HTTP" else "Torrent"
    val title = "$type Download"
    val contentText = if (progress > 0) {
      "Downloading... $progress%"
    } else {
      "Starting download..."
    }

    // Create intent to open app when tapped (you can customize this)
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(contentText)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setProgress(100, progress, progress == 0)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
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
}

