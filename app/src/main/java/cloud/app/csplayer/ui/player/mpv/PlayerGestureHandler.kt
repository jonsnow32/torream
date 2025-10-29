package cloud.app.csplayer.ui.player.mpv

import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.Utils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
/**
 * Handles touch gestures for the player including swipe for brightness, volume, and seeking
 */
class PlayerGestureHandler(
    private val context: Context,
    private val onBrightnessChange: (Float, String) -> Unit,
    private val onVolumeChange: (Int, String) -> Unit,
    private val onSeek: (Long, String) -> Unit
) {
    private var currentTouchStart: Utils.Vector2? = null
    private var currentTouchLast: Utils.Vector2? = null
    private var currentTouchAction: TouchAction? = null
    private var currentTouchStartTime: Long? = null
    private var currentTouchStartPlayerTime: Long? = null

    private var swipeHorizontalEnabled = false
    private var swipeVerticalEnabled = false
    private var useTrueSystemBrightness = true

    companion object {
        private const val HORIZONTAL_MULTIPLIER = 1.5f
        private const val VERTICAL_MULTIPLIER = 2f
        private const val MINIMUM_HORIZONTAL_SWIPE = 5f
        private const val MINIMUM_VERTICAL_SWIPE = 5f
    }

    fun updateSettings(horizontalEnabled: Boolean, verticalEnabled: Boolean) {
        swipeHorizontalEnabled = horizontalEnabled
        swipeVerticalEnabled = verticalEnabled
    }

    fun handleMotionEvent(
        event: MotionEvent?,
        currentPlayerTime: Long?,
        duration: Long?,
        currentVolume: Int?,
        maxVolume: Int?,
        isValidTouch: (Float, Float) -> Boolean
    ): Boolean {
        if (event == null) return false

        val currentTouch = Utils.Vector2(event.rawX, event.rawY)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isValidTouch(currentTouch.x, currentTouch.y)) return false
                currentTouchStart = currentTouch
                currentTouchLast = currentTouch
                currentTouchStartTime = System.currentTimeMillis()
                currentTouchStartPlayerTime = currentPlayerTime
                currentTouchAction = null
            }

            MotionEvent.ACTION_MOVE -> {
                val touchStart = currentTouchStart ?: return false
                val deltaX = currentTouch.x - touchStart.x
                val deltaY = currentTouch.y - touchStart.y

                // Determine touch action if not set
                if (currentTouchAction == null) {
                    if (abs(deltaX) >= MINIMUM_HORIZONTAL_SWIPE && swipeHorizontalEnabled) {
                        currentTouchAction = TouchAction.Time
                    } else if (abs(deltaY) >= MINIMUM_VERTICAL_SWIPE && swipeVerticalEnabled) {
                        currentTouchAction = if (touchStart.x < CommonActivitty.screenWidth / 2) {
                            TouchAction.Brightness
                        } else {
                            TouchAction.Volume
                        }
                    }
                }

                // Handle the determined action
                when (currentTouchAction) {
                    TouchAction.Time -> handleSeekGesture(touchStart, currentTouch, currentTouchStartPlayerTime, duration)
                    TouchAction.Brightness -> handleBrightnessGesture(touchStart, currentTouch)
                    TouchAction.Volume -> handleVolumeGesture(touchStart, currentTouch, currentVolume, maxVolume)
                    null -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                currentTouchStart = null
                currentTouchLast = null
                currentTouchAction = null
                currentTouchStartTime = null
                currentTouchStartPlayerTime = null
            }
        }

        currentTouchLast = currentTouch
        return true
    }

    private fun handleSeekGesture(
        touchStart: Utils.Vector2,
        touchCurrent: Utils.Vector2,
        startPlayerTime: Long?,
        duration: Long?
    ) {
        if (startPlayerTime == null || duration == null) return

        val newTime = calculateNewTime(startPlayerTime, touchStart, touchCurrent, duration)
        if (newTime != null) {
            val diff = newTime - startPlayerTime
            val text = formatTimeDiff(diff)
            onSeek(newTime, text)
        }
    }

    private fun handleBrightnessGesture(touchStart: Utils.Vector2, touchCurrent: Utils.Vector2) {
        val currentBrightness = getBrightness() ?: return
        val diffY = (touchCurrent.y - touchStart.y) * VERTICAL_MULTIPLIER / CommonActivitty.screenHeight
        val newBrightness = (currentBrightness - diffY).coerceIn(0f, 1f)

        setBrightness(newBrightness)
        val brightnessPercent = (newBrightness * 100).toInt()
        onBrightnessChange(newBrightness, "$brightnessPercent%")
    }

    private fun handleVolumeGesture(
        touchStart: Utils.Vector2,
        touchCurrent: Utils.Vector2,
        currentVolume: Int?,
        maxVolume: Int?
    ) {
        if (currentVolume == null || maxVolume == null) return

        val diffY = (touchCurrent.y - touchStart.y) * VERTICAL_MULTIPLIER / CommonActivitty.screenHeight
        val newVolume = (currentVolume - (diffY * maxVolume)).toInt().coerceIn(0, maxVolume)

        val volumePercent = (newVolume.toFloat() / maxVolume * 100).toInt()
        onVolumeChange(newVolume, "$volumePercent%")
    }

    private fun calculateNewTime(
        startTime: Long,
        touchStart: Utils.Vector2,
        touchEnd: Utils.Vector2,
        duration: Long
    ): Long {
        val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / CommonActivitty.screenWidth.toFloat()
        return max(
            min(
                startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(),
                duration
            ), 0
        )
    }

    private fun formatTimeDiff(diff: Long): String {
        val sign = if (diff >= 0) "+" else "-"
        val seconds = abs(diff)
        return "$sign${seconds}s"
    }

    private fun getBrightness(): Float? {
        return if (useTrueSystemBrightness) {
            try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                useTrueSystemBrightness = false
                null
            }
        } else {
            try {
                (context as? android.app.Activity)?.window?.attributes?.screenBrightness
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun setBrightness(brightness: Float) {
        if (useTrueSystemBrightness) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (brightness * 255).toInt()
                )
            } catch (e: Exception) {
                // Fall back to window brightness if we don't have permission
                useTrueSystemBrightness = false
                setBrightness(brightness)
            }
        } else {
            try {
                val lp = (context as? android.app.Activity)?.window?.attributes
                lp?.screenBrightness = brightness
                (context as? android.app.Activity)?.window?.attributes = lp
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}


