package com.tv.apps.zippy.ui.player.mpv

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.tv.apps.zippy.R
import timber.log.Timber

/**
 * PipActionManager - Manages Picture-in-Picture remote actions
 *
 * Provides controls in PIP window:
 * - Play/Pause
 * - Previous (if in playlist)
 * - Next (if in playlist)
 * - Fast rewind
 * - Fast forward
 */
@RequiresApi(Build.VERSION_CODES.O)
class PipActionManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onRewind: () -> Unit,
    private val onFastForward: () -> Unit
) {

    companion object {
        private const val TAG = "PipActionManager"

        // Action identifiers
        private const val ACTION_PLAY_PAUSE = "com.tv.apps.zippy.PIP_PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.tv.apps.zippy.PIP_PREVIOUS"
        private const val ACTION_NEXT = "com.tv.apps.zippy.PIP_NEXT"
        private const val ACTION_REWIND = "com.tv.apps.zippy.PIP_REWIND"
        private const val ACTION_FAST_FORWARD = "com.tv.apps.zippy.PIP_FAST_FORWARD"

        // Request codes
        private const val REQUEST_PLAY_PAUSE = 1001
        private const val REQUEST_PREVIOUS = 1002
        private const val REQUEST_NEXT = 1003
        private const val REQUEST_REWIND = 1004
        private const val REQUEST_FAST_FORWARD = 1005
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    Timber.tag(TAG).d("PIP Action: Play/Pause")
                    onPlayPause()
                }
                ACTION_PREVIOUS -> {
                    Timber.tag(TAG).d("PIP Action: Previous")
                    onPrevious()
                }
                ACTION_NEXT -> {
                    Timber.tag(TAG).d("PIP Action: Next")
                    onNext()
                }
                ACTION_REWIND -> {
                    Timber.tag(TAG).d("PIP Action: Rewind")
                    onRewind()
                }
                ACTION_FAST_FORWARD -> {
                    Timber.tag(TAG).d("PIP Action: Fast Forward")
                    onFastForward()
                }
            }
        }
    }

    private var isReceiverRegistered = false

    /**
     * Register broadcast receiver for PIP actions
     */
    fun register() {
        if (isReceiverRegistered) return

        try {
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_PREVIOUS)
                addAction(ACTION_NEXT)
                addAction(ACTION_REWIND)
                addAction(ACTION_FAST_FORWARD)
            }

            // Use RECEIVER_NOT_EXPORTED for security (these are internal actions only)
            ContextCompat.registerReceiver(
                context,
                broadcastReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            isReceiverRegistered = true
            Timber.tag(TAG).d("PIP actions registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to register PIP actions")
        }
    }

    /**
     * Unregister broadcast receiver
     */
    fun unregister() {
        if (!isReceiverRegistered) return

        try {
            context.unregisterReceiver(broadcastReceiver)
            isReceiverRegistered = false
            Timber.tag(TAG).d("PIP actions unregistered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to unregister PIP actions")
        }
    }

    /**
     * Create PIP actions list
     *
     * Creates up to 5 actions based on current state.
     * Android system will display as many as the device supports (typically 3-5).
     *
     * @param isPlaying Current playback state
     * @param hasPlaylist Whether in playlist mode
     * @param hasPrevious Whether previous item exists
     * @param hasNext Whether next item exists
     */
    fun createPipActions(
        isPlaying: Boolean,
        hasPlaylist: Boolean = false,
        hasPrevious: Boolean = false,
        hasNext: Boolean = false
    ): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        // 1. Previous action (playlist navigation)
        if (hasPlaylist && hasPrevious) {
            actions.add(
                createRemoteAction(
                    ACTION_PREVIOUS,
                    REQUEST_PREVIOUS,
                    R.drawable.ic_skip_previous_black_24dp,
                    "Previous",
                    "Previous video"
                )
            )
        }

        // 2. Rewind action (seek back)
        actions.add(
            createRemoteAction(
                ACTION_REWIND,
                REQUEST_REWIND,
                R.drawable.go_back_30,
                "Rewind",
                "Rewind 10 seconds"
            )
        )

        // 3. Play/Pause action (always available)
        actions.add(
            createRemoteAction(
                ACTION_PLAY_PAUSE,
                REQUEST_PLAY_PAUSE,
                if (isPlaying) R.drawable.netflix_pause else R.drawable.netflix_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause playback" else "Resume playback"
            )
        )

        // 4. Fast forward action (seek forward)
        actions.add(
            createRemoteAction(
                ACTION_FAST_FORWARD,
                REQUEST_FAST_FORWARD,
                R.drawable.forward_10_24dp,
                "Fast Forward",
                "Fast forward 10 seconds"
            )
        )

        // 5. Next action (playlist navigation)
        if (hasPlaylist && hasNext) {
            actions.add(
                createRemoteAction(
                    ACTION_NEXT,
                    REQUEST_NEXT,
                    R.drawable.ic_baseline_skip_next_24,
                    "Next",
                    "Next video"
                )
            )
        }

        Timber.tag(TAG).d("Created ${actions.size} PIP actions (playlist: $hasPlaylist). Android will show as many as device supports.")
        return actions
    }

    /**
     * Create a remote action
     */
    private fun createRemoteAction(
        action: String,
        requestCode: Int,
        @DrawableRes icon: Int,
        title: String,
        description: String
    ): RemoteAction {
        val intent = Intent(action).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteAction(
            Icon.createWithResource(context, icon),
            title,
            description,
            pendingIntent
        )
    }
}

