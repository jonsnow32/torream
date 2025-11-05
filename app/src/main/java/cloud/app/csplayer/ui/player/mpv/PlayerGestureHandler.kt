package cloud.app.csplayer.ui.player.mpv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.formatDuration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


const val MINIMUM_SEEK_TIME = 7L         // when swipe seeking
const val MINIMUM_VERTICAL_SWIPE = 2.0f     // in percentage
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f   // in percentage
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200L
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200L    // this also affects the UI show response time
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15        // in both directions

enum class TouchAction {
  Brightness,
  Volume,
  Time,
}

/**
 * Comprehensive gesture handler for the player
 * Handles:
 * - Swipe gestures (brightness, volume, seeking)
 * - Double tap for seek forward/backward
 * - Single tap for UI toggle
 * - Touch validation
 */
class PlayerGestureHandler(
  private val context: Context,
  private val sWidth: Int,
  private val sHeight: Int,
  private val onBrightnessUpdate: (Float, Boolean) -> Unit, // brightness, showUI
  private val onVolumeUpdate: (Float, Boolean) -> Unit,     // volume ratio, showUI
  private val onSeekUpdate: (Long, String, Boolean) -> Unit, // time, text, showUI
  private val onSeekCommit: (Long) -> Unit,                  // final seek position
  private val onDoubleTapRewind: () -> Unit,
  private val onDoubleTapForward: () -> Unit,
  private val onSingleTap: () -> Unit,
  private val onTogglePlayPause: () -> Unit,
  private val isLocked: () -> Boolean,
  private val isShowing: () -> Boolean,
  private val hideUIForBrightness: () -> Unit
) {
  // Touch state variables
  private var isCurrentTouchValid = false
  private var currentTouchStart: Utils.Vector2? = null
  private var currentTouchLast: Utils.Vector2? = null
  private var currentTouchAction: TouchAction? = null
  private var currentLastTouchAction: TouchAction? = null
  private var currentTouchStartPlayerTime: Long? = null
  private var currentTouchStartTime: Long? = null
  private var currentLastTouchEndTime: Long = 0
  private var currentClickCount: Int = 0
  private var currentDoubleTapIndex = 0

  // Requested volume and brightness for smooth swiping
  private var currentRequestedVolume: Float = 0.0f
  private var currentRequestedBrightness: Float = 1.0f

  // Settings
  private var swipeHorizontalEnabled = false
  private var swipeVerticalEnabled = false
  private var doubleTapEnabled = false
  private var doubleTapPauseEnabled = true
  private var useTrueSystemBrightness = true

  // Audio manager for volume control
  private var audioManager: AudioManager? = null

  // Icons
  private val brightnessIcons = intArrayOf(
    R.drawable.sun_1,
    R.drawable.sun_2,
    R.drawable.sun_3,
    R.drawable.sun_4,
    R.drawable.sun_5,
    R.drawable.sun_6,
  )

  private val volumeIcons = intArrayOf(
    R.drawable.ic_baseline_volume_mute_24,
    R.drawable.ic_baseline_volume_down_24,
    R.drawable.ic_baseline_volume_up_24,
  )

  init {
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  }

  /**
   * Update gesture handler settings
   */
  fun updateSettings(
    horizontalEnabled: Boolean,
    verticalEnabled: Boolean,
    doubleTapEnabled: Boolean,
    doubleTapPauseEnabled: Boolean
  ) {
    this.swipeHorizontalEnabled = horizontalEnabled
    this.swipeVerticalEnabled = verticalEnabled
    this.doubleTapEnabled = doubleTapEnabled
    this.doubleTapPauseEnabled = doubleTapPauseEnabled
  }

  /**
   * Main motion event handler
   */
  @SuppressLint("SetTextI18n")
  fun handleMotionEvent(
    view: View?,
    event: MotionEvent?,
    currentPlayerTime: Long?,
    duration: Long?,
    isValidTouch: (Float, Float) -> Boolean
  ): Boolean {
    if (event == null || view == null) return false
    val currentTouch = Utils.Vector2(event.x, event.y)
    val startTouch = currentTouchStart

    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        handleActionDown(currentTouch, isValidTouch, currentPlayerTime)
      }

      MotionEvent.ACTION_UP -> {
        handleActionUp(currentTouch, startTouch, duration)
      }

      MotionEvent.ACTION_MOVE -> {
        handleActionMove(currentTouch, startTouch, duration)
      }
    }

    currentTouchLast = currentTouch
    return true
  }

  private fun handleActionDown(
    currentTouch: Utils.Vector2,
    isValidTouch: (Float, Float) -> Boolean,
    currentPlayerTime: Long?
  ) {
    // Validates if the touch is inside of the player area
    isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)

    if (isCurrentTouchValid) {
      currentTouchStartTime = System.currentTimeMillis()
      currentTouchStart = currentTouch
      currentTouchLast = currentTouch
      currentTouchStartPlayerTime = currentPlayerTime

      getBrightness()?.let {
        currentRequestedBrightness = it
      }
      getVolume()?.let { currentVolume ->
        getMaxVolume()?.let { maxVolume ->
          currentRequestedVolume = currentVolume.toFloat() / maxVolume.toFloat()
        }
      }
    }
  }

  private fun handleActionUp(
    currentTouch: Utils.Vector2,
    startTouch: Utils.Vector2?,
    duration: Long?
  ) {
    if (isCurrentTouchValid && !isLocked()) {
      // Commit seek time
      if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
        val startTime = currentTouchStartPlayerTime
        if (startTime != null) {
          calculateNewTime(startTime, startTouch, currentTouch, duration)?.let { seekTo ->
            if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
              onSeekCommit(seekTo)
            }
          }
        }
      }
    }

    // Check if click is eligible for double tap or single tap
    val holdTime = currentTouchStartTime?.let { System.currentTimeMillis() - it }
    if (isCurrentTouchValid
      && currentTouchAction == null
      && currentLastTouchAction == null
      && holdTime != null
      && holdTime < DOUBLE_TAB_MAXIMUM_HOLD_TIME
    ) {
      if (!isLocked()
        && (System.currentTimeMillis() - currentLastTouchEndTime) < DOUBLE_TAB_MINIMUM_TIME_BETWEEN
      ) {
        currentClickCount++

        if (currentClickCount >= 1) {
          currentDoubleTapIndex++
          handleDoubleTap(currentTouch)
        }
      } else {
        // Valid click but not fast enough for double tap
        currentClickCount = 0
        onSingleTap()
      }
    } else {
      currentClickCount = 0
    }

    // Reset variables
    isCurrentTouchValid = false
    currentTouchStart = null
    currentLastTouchAction = currentTouchAction
    currentTouchAction = null
    currentTouchStartPlayerTime = null
    currentTouchLast = null
    currentTouchStartTime = null

    // Reset UI (notify listeners)
    onSeekUpdate(0, "", false)
    onVolumeUpdate(0f, false)
    onBrightnessUpdate(0f, false)

    currentLastTouchEndTime = System.currentTimeMillis()
  }

  private fun handleActionMove(
    currentTouch: Utils.Vector2,
    startTouch: Utils.Vector2?,
    duration: Long?
  ) {
    if (startTouch != null && isCurrentTouchValid && !isLocked()) {
      // Assign action if unassigned
      if (currentTouchAction == null) {
        val diffFromStart = startTouch - currentTouch

        if (swipeVerticalEnabled) {
          if (abs(diffFromStart.y * 100 / sHeight) > MINIMUM_VERTICAL_SWIPE) {
            currentTouchAction = if (startTouch.x < sWidth / 2) {
              // Hide UI when adjusting brightness for better UX
              if (isShowing()) {
                hideUIForBrightness()
              }
              TouchAction.Brightness
            } else {
              TouchAction.Volume
            }
          }
        }
        if (swipeHorizontalEnabled) {
          if (abs(diffFromStart.x * 100 / sHeight) > MINIMUM_HORIZONTAL_SWIPE) {
            currentTouchAction = TouchAction.Time
          }
        }
      }

      // Display action
      val lastTouch = currentTouchLast
      if (lastTouch != null) {
        val diffFromLast = lastTouch - currentTouch
        val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / sHeight.toFloat()

        when (currentTouchAction) {
          TouchAction.Time -> handleTimeSwipe(startTouch, currentTouch, duration)
          TouchAction.Brightness -> handleBrightnessSwipe(verticalAddition)
          TouchAction.Volume -> handleVolumeSwipe(verticalAddition)
          else -> Unit
        }
      }
    }
  }

  private fun handleTimeSwipe(
    startTouch: Utils.Vector2,
    currentTouch: Utils.Vector2,
    duration: Long?
  ) {
    val startTime = currentTouchStartPlayerTime ?: return

    calculateNewTime(startTime, startTouch, currentTouch, duration)?.let { newMs ->
      val skipMs = newMs - startTime
      val text = "${newMs.formatDuration()} [${
        (if (abs(skipMs) < 0) "" else (if (skipMs > 0) "+" else "-"))
      }${(abs(skipMs)).formatDuration()}]"
      onSeekUpdate(newMs, text, true)
    }
  }

  private fun handleBrightnessSwipe(verticalAddition: Float) {
    currentRequestedBrightness = min(
      1.0f,
      max(currentRequestedBrightness + verticalAddition, 0.0f)
    )

    setBrightness(currentRequestedBrightness)
    onBrightnessUpdate(currentRequestedBrightness, true)
  }

  private fun handleVolumeSwipe(verticalAddition: Float) {
    getMaxVolume()?.let { maxVolume ->
      getVolume()?.let { currentVolume ->
        // Clamps volume and adds swipe
        currentRequestedVolume = min(
          1.0f,
          max(currentRequestedVolume + verticalAddition, 0.0f)
        )

        // Adjust volume
        val desiredVolume = round(currentRequestedVolume * maxVolume).toInt()
        if (desiredVolume != currentVolume) {
          val newVolumeAdjusted =
            if (desiredVolume < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
          audioManager?.adjustVolume(newVolumeAdjusted, 0)
        }

        onVolumeUpdate(currentRequestedVolume, true)
      }
    }
  }

  private fun handleDoubleTap(currentTouch: Utils.Vector2) {
    if (doubleTapPauseEnabled) {
      when {
        currentTouch.x < sWidth / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * sWidth) -> {
          if (doubleTapEnabled) onDoubleTapRewind()
        }

        currentTouch.x > sWidth / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * sWidth) -> {
          if (doubleTapEnabled) onDoubleTapForward()
        }

        else -> {
          onTogglePlayPause()
        }
      }
    } else if (doubleTapEnabled) {
      if (currentTouch.x < sWidth / 2) {
        onDoubleTapRewind()
      } else {
        onDoubleTapForward()
      }
    }
  }

  private fun calculateNewTime(
    startTime: Long,
    touchStart: Utils.Vector2?,
    touchEnd: Utils.Vector2?,
    duration: Long?
  ): Long? {
    if (touchStart == null || touchEnd == null || duration == null) return null
    val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / sWidth.toFloat()

    return max(
      min(
        startTime + ((duration * (diffX * diffX)) * (if (diffX < 0.0f) -1 else 1)).toLong(),
        duration
      ), 0
    )
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
        getBrightness()
      }
    } else {
      try {
        (context as? Activity)?.window?.attributes?.screenBrightness
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
          Settings.System.SCREEN_BRIGHTNESS_MODE,
          Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
          context.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS,
          (brightness * 255).toInt()
        )
      } catch (e: Exception) {
        useTrueSystemBrightness = false
        setBrightness(brightness)
      }
    } else {
      try {
        val lp = (context as? Activity)?.window?.attributes
        lp?.screenBrightness = brightness
        (context as? Activity)?.window?.attributes = lp
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  private fun getVolume(): Int? {
    return try {
      audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
    } catch (e: Exception) {
      null
    }
  }

  private fun getMaxVolume(): Int? {
    return try {
      audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    } catch (e: Exception) {
      null
    }
  }

  fun getBrightnessIcon(brightness: Float): Int {
    return brightnessIcons[min(
      brightnessIcons.size - 1,
      max(0, round(brightness * (brightnessIcons.size - 1)).toInt())
    )]
  }

  fun getVolumeIcon(volumeRatio: Float): Int {
    return volumeIcons[min(
      volumeIcons.size - 1,
      max(0, round(volumeRatio * (volumeIcons.size - 1)).toInt())
    )]
  }
}


