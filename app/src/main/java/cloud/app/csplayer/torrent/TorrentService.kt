package cloud.app.csplayer.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cloud.app.csplayer.MainActivity
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.model.TorrentState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for torrent downloads
 */
@AndroidEntryPoint
class TorrentService : Service() {

    private val binder = TorrentBinder()

    @Inject
    lateinit var torrentManager: TorrentManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var notificationManager: NotificationManager? = null

    inner class TorrentBinder : Binder() {
        fun getService(): TorrentService = this@TorrentService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("TorrentService: onCreate")

        torrentManager.startPeriodicUpdates()

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        startForeground(NOTIFICATION_ID, createNotification())

        observeTorrentStates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("TorrentService: onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun observeTorrentStates() {
        scope.launch {
            torrentManager.torrentStates.collectLatest { states ->
                if (states.isEmpty()) {
                    // No active torrents, stop service
                    stopSelf()
                } else {
                    // Update notification with current state
                    val notification = createNotification(states.values.toList())
                    notificationManager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Torrent Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows torrent download progress"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(states: List<TorrentState> = emptyList()): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Torrent Downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (states.isEmpty()) {
            builder.setContentText("No active downloads")
        } else {
            val activeDownloads = states.count { it.status == TorrentDownloadStatus.DOWNLOADING }
            val totalProgress = states.sumOf { it.progress.toDouble() } / states.size

            builder.setContentText("$activeDownloads active downloads")
            builder.setProgress(100, (totalProgress * 100).toInt(), false)

            // Show detailed info for first torrent
            val firstTorrent = states.first()
            builder.setStyle(NotificationCompat.BigTextStyle()
                .bigText("${firstTorrent.name}\n" +
                    "Progress: ${(firstTorrent.progress * 100).toInt()}%\n" +
                    "Speed: ${formatSpeed(firstTorrent.downloadSpeed)}\n" +
                    "Peers: ${firstTorrent.numPeers}"))
        }

        return builder.build()
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> "%.2f MB/s".format(bytesPerSecond / (1024f * 1024f))
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Timber.d("TorrentService: onDestroy")
        scope.cancel()
        torrentManager.shutdown()
    }

    companion object {
        private const val CHANNEL_ID = "torrent_service_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, TorrentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorrentService::class.java)
            context.stopService(intent)
        }
    }
}

