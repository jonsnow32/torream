package cloud.app.csplayer.ui.player.mpv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.formatDuration
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.round

// ========== Gesture Configuration Constants ==========

// Swipe thresholds
private const val MINIMUM_SEEK_TIME = 7L              // Minimum time difference for seek commit (ms)
private const val MINIMUM_VERTICAL_SWIPE = 2.0f       // Minimum vertical swipe distance (%)
private const val MINIMUM_HORIZONTAL_SWIPE = 2.0f     // Minimum horizontal swipe distance (%)
private const val VERTICAL_MULTIPLIER = 2.0f          // Sensitivity multiplier for vertical swipes
private const val HORIZONTAL_MULTIPLIER = 2.0f        // Sensitivity multiplier for horizontal swipes

// Tap detection thresholds
private const val DOUBLE_TAP_MAX_HOLD_TIME = 200L     // Maximum hold time to be considered a tap (ms)
private const val DOUBLE_TAP_TIME_WINDOW = 200L       // Time window between taps for double tap (ms)
private const val DOUBLE_TAP_PAUSE_ZONE = 0.15f       // Center zone percentage for pause on double tap

// Brightness adjustment
private const val BRIGHTNESS_SCALE = 255f             // System brightness scale (0-255)

/**
 * Touch action types for gesture recognition
 */
enum class TouchAction {
  Brightness,
  Volume,
  Time,
}

/**
 * Comprehensive gesture handler for the player
 *
 * Features:
 * - Swipe gestures (brightness, volume, seeking)
 * - Double tap for seek forward/backward or pause
 * - Single tap for UI toggle (with delayed detection to avoid conflict with double tap)
 * - Touch validation and lock state handling
 *
 * Performance optimizations:
 * - Lazy initialization of audio manager
 * - Minimal object allocations during gesture handling
 * - Efficient state management
 */
class PlayerGestureHandler(
  private val context: Context,
  private var sWidth: Int,
  private var sHeight: Int,
  private val isLocked: () -> Boolean,
  private val isShowing: () -> Boolean,
  private val onBrightnessUpdate: (Float, Boolean) -> Unit,
  private val onVolumeUpdate: (Float, Boolean) -> Unit,
  private val onSeekUpdate: (Long, String, Boolean) -> Unit,
  private val onSeekCommit: (Long) -> Unit,
  private val onDoubleTapRewind: () -> Unit,
  private val onDoubleTapForward: () -> Unit,
  private val onSingleTap: () -> Unit,
  private val onTogglePlayPause: () -> Unit,
  private val hideUIForBrightness: () -> Unit
) {

  // ========== Touch State Management ==========

  private var isCurrentTouchValid = false
  private var currentTouchStart: Utils.Vector2? = null
  private var currentTouchLast: Utils.Vector2? = null
  private var currentTouchAction: TouchAction? = null
  private var currentLastTouchAction: TouchAction? = null
  private var currentTouchStartPlayerTime: Long? = null
  private var currentTouchStartTime: Long? = null
  private var currentLastTouchEndTime: Long = 0

  // ========== Tap Detection State ==========

  private var currentClickCount: Int = 0
  private var currentDoubleTapIndex = 0
  private val singleTapHandler = Handler(Looper.getMainLooper())
  private var pendingSingleTapRunnable: Runnable? = null

  // ========== Gesture Settings ==========

  private var currentRequestedVolume: Float = -1f  // -1 means uninitialized
  private var currentRequestedBrightness: Float = 1.0f
  private var swipeHorizontalEnabled = false
  private var swipeVerticalEnabled = false
  private var doubleTapEnabled = false
  private var doubleTapPauseEnabled = true
  private var useTrueSystemBrightness = true

  init {
    // Load gesture settings from SharedPreferences
    loadGestureSettings()
  }

  /**
   * Load gesture settings from SharedPreferences
   */
  private fun loadGestureSettings() {
    try {
      val prefs = PreferenceManager.getDefaultSharedPreferences(context)

      // Load swipe settings
      swipeHorizontalEnabled = prefs.getBoolean(
        context.getString(R.string.swipe_enabled_key),
        true  // Default: enabled
      )
      swipeVerticalEnabled = prefs.getBoolean(
        context.getString(R.string.swipe_vertical_enabled_key),
        true  // Default: enabled
      )

      // Load double tap settings
      doubleTapEnabled = prefs.getBoolean(
        context.getString(R.string.double_tap_enabled_key),
        true  // Default: enabled
      )
      doubleTapPauseEnabled = prefs.getBoolean(
        context.getString(R.string.double_tap_pause_enabled_key),
        true  // Default: enabled
      )

      // Note: brightness system mode is determined at runtime
      // useTrueSystemBrightness will auto-fallback if system settings not available

      Timber.d("Gesture settings loaded - H:$swipeHorizontalEnabled V:$swipeVerticalEnabled DoubleTap:$doubleTapEnabled DoubleTapPause:$doubleTapPauseEnabled")
    } catch (e: Exception) {
      Timber.e(e, "Failed to load gesture settings, using defaults")
      // Keep default values on error
    }
  }

  // ========== System Services (Lazy Init) ==========

  private val audioManager: AudioManager? by lazy {
    context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  }

  // ========== UI Resources (Lazy Init) ==========

  private val brightnessIcons by lazy {
    intArrayOf(
      R.drawable.sun_1,
      R.drawable.sun_2,
      R.drawable.sun_3,
      R.drawable.sun_4,
      R.drawable.sun_5,
      R.drawable.sun_6,
    )
  }

  private val volumeIcons by lazy {
    intArrayOf(
      R.drawable.ic_baseline_volume_mute_24,
      R.drawable.ic_baseline_volume_down_24,
      R.drawable.ic_baseline_volume_up_24,
    )
  }

  // ========== Screen Dimension Helpers (Dynamic) ==========

  private val halfScreenWidth: Int
    get() = sWidth / 2

  private val doubleTapPauseZoneStart: Float
    get() = halfScreenWidth - (DOUBLE_TAP_PAUSE_ZONE * sWidth)

  private val doubleTapPauseZoneEnd: Float
    get() = halfScreenWidth + (DOUBLE_TAP_PAUSE_ZONE * sWidth)


  // ========== Public API ==========

  /**
   * Update screen dimensions when orientation changes
   * @param newWidth New screen width
   * @param newHeight New screen height
   */
  fun updateDimensions(newWidth: Int, newHeight: Int) {
    sWidth = newWidth
    sHeight = newHeight
    Timber.d("Gesture dimensions updated: ${newWidth}x${newHeight}")
  }


  /**
   * Reload all gesture settings from SharedPreferences
   * Call this when preferences are changed in settings screen
   */
  fun reloadSettings() {
    loadGestureSettings()
  }

  /**
   * Main motion event handler
   * @return true if event was handled, false otherwise
   */
  @SuppressLint("SetTextI18n")
  fun handleMotionEvent(
    view: View?,
    event: MotionEvent?,
    currentPlayerTime: Long?,
    duration: Long?,
    isValidTouch: (Float, Float) -> Boolean
  ): Boolean {
    // Early return for invalid input
    if (event == null || view == null) return false

    val currentTouch = Utils.Vector2(event.x, event.y)

    when (event.action) {
      MotionEvent.ACTION_DOWN -> handleActionDown(currentTouch, isValidTouch, currentPlayerTime)
      MotionEvent.ACTION_UP -> handleActionUp(currentTouch, currentTouchStart, duration)
      MotionEvent.ACTION_MOVE -> handleActionMove(currentTouch, currentTouchStart, duration)
    }

    currentTouchLast = currentTouch
    return true
  }

  // ========== Motion Event Handlers ==========


  /**
   * Handle ACTION_DOWN event - initialize touch state
   */
  private fun handleActionDown(
    currentTouch: Utils.Vector2,
    isValidTouch: (Float, Float) -> Boolean,
    currentPlayerTime: Long?
  ) {
    // Validate touch location
    isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)

    if (!isCurrentTouchValid) return

    // Initialize touch state
    val currentTime = System.currentTimeMillis()
    currentTouchStartTime = currentTime
    currentTouchStart = currentTouch
    currentTouchLast = currentTouch
    currentTouchStartPlayerTime = currentPlayerTime

    // Cache current brightness for smooth adjustment
    currentRequestedBrightness = getBrightness() ?: 1.0f

    // Initialize volume only if not yet initialized (first use or after external change)
    if (currentRequestedVolume < 0f) {
      initializeVolumeFromSystem()
    }
  }

  /**
   * Initialize volume from system - only called on first use
   */
  private fun initializeVolumeFromSystem() {
    getVolume()?.let { volume ->
      getMaxVolume()?.let { maxVolume ->
        currentRequestedVolume = volume.toFloat() / maxVolume.toFloat()
        Timber.d("Volume initialized from system: $currentRequestedVolume")
      }
    }

    // Fallback if system volume unavailable
    if (currentRequestedVolume < 0f) {
      currentRequestedVolume = 0.5f  // Default to 50%
    }
  }


  /**
   * Handle ACTION_UP event - finalize gesture and detect taps
   */
  private fun handleActionUp(
    currentTouch: Utils.Vector2,
    startTouch: Utils.Vector2?,
    duration: Long?
  ) {
    // Commit seek if horizontal swipe was performed
    if (isCurrentTouchValid && !isLocked() && swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
      commitSeek(startTouch, currentTouch, duration)
    }

    // Detect tap gestures (single or double)
    detectTapGesture(currentTouch)

    // Cleanup touch state
    resetTouchState()
  }

  /**
   * Commit seek operation after horizontal swipe
   */
  private fun commitSeek(
    startTouch: Utils.Vector2?,
    currentTouch: Utils.Vector2,
    duration: Long?
  ) {
    val startTime = currentTouchStartPlayerTime ?: return
    calculateNewTime(startTime, startTouch, currentTouch, duration)?.let { seekTo ->
      if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
        onSeekCommit(seekTo)
      }
    }
  }

  /**
   * Detect single tap or double tap gesture
   */
  private fun detectTapGesture(currentTouch: Utils.Vector2) {
    // Check if this is a valid tap (short hold time, no swipe action)
    val holdTime = currentTouchStartTime?.let { System.currentTimeMillis() - it } ?: return

    if (!isCurrentTouchValid
        || currentTouchAction != null
        || currentLastTouchAction != null
        || holdTime >= DOUBLE_TAP_MAX_HOLD_TIME) {
      currentClickCount = 0
      return
    }

    if (isLocked()) {
      currentClickCount = 0
      return
    }

    val timeSinceLastTap = System.currentTimeMillis() - currentLastTouchEndTime

    if (timeSinceLastTap < DOUBLE_TAP_TIME_WINDOW) {
      // Double tap detected!
      cancelPendingSingleTap()
      currentClickCount++

      if (currentClickCount >= 1) {
        currentDoubleTapIndex++
        Timber.i("Double tap confirmed")
        handleDoubleTap(currentTouch)
      }
    } else {
      // Potential single tap - wait to confirm no second tap
      currentClickCount = 0
      scheduleSingleTap()
    }
  }

  /**
   * Reset touch state after ACTION_UP
   */
  private fun resetTouchState() {
    // Sync volume with actual system value when gesture ends
    if (currentLastTouchAction == TouchAction.Volume || currentTouchAction == TouchAction.Volume) {
      syncVolumeWithSystem()
    }

    isCurrentTouchValid = false
    currentTouchStart = null
    currentLastTouchAction = currentTouchAction
    currentTouchAction = null
    currentTouchStartPlayerTime = null
    currentTouchLast = null
    currentTouchStartTime = null

    // Reset UI overlays
    onSeekUpdate(0, "", false)
    onVolumeUpdate(0f, false)
    onBrightnessUpdate(0f, false)

    currentLastTouchEndTime = System.currentTimeMillis()
  }

  /**
   * Sync cached volume with actual system volume
   * Call this when you suspect external volume changes (e.g., hardware buttons)
   */
  private fun syncVolumeWithSystem() {
    val maxVolume = getMaxVolume() ?: return
    val actualVolume = getVolume() ?: return
    val newVolumeRatio = actualVolume.toFloat() / maxVolume.toFloat()

    if (currentRequestedVolume != newVolumeRatio) {
      Timber.d("Volume synced: $currentRequestedVolume -> $newVolumeRatio")
      currentRequestedVolume = newVolumeRatio
    }
  }

  /**
   * Force re-initialization of volume from system
   * Call this when app resumes or external volume changes detected
   */
  fun resetVolumeCache() {
    currentRequestedVolume = -1f
    Timber.d("Volume cache reset - will re-initialize on next gesture")
  }


  /**
   * Handle ACTION_MOVE event - detect and process swipe gestures
   */
  private fun handleActionMove(
    currentTouch: Utils.Vector2,
    startTouch: Utils.Vector2?,
    duration: Long?
  ) {
    if (startTouch == null || !isCurrentTouchValid || isLocked()) return

    // Detect gesture type if not yet determined
    if (currentTouchAction == null) {
      detectGestureType(startTouch, currentTouch)
    }

    // Process the detected gesture
    val lastTouch = currentTouchLast ?: return
    processGesture(startTouch, currentTouch, lastTouch, duration)
  }

  /**
   * Detect which gesture type based on swipe direction
   */
  private fun detectGestureType(startTouch: Utils.Vector2, currentTouch: Utils.Vector2) {
    val diffFromStart = startTouch - currentTouch
    val verticalSwipePercent = abs(diffFromStart.y * 100 / sHeight)
    val horizontalSwipePercent = abs(diffFromStart.x * 100 / sHeight)

    // Check vertical swipe (brightness/volume)
    if (swipeVerticalEnabled && verticalSwipePercent > MINIMUM_VERTICAL_SWIPE) {
      currentTouchAction = if (startTouch.x < halfScreenWidth) {
        // Left side = brightness, hide UI for better UX
        if (isShowing()) hideUIForBrightness()
        TouchAction.Brightness
      } else {
        // Right side = volume
        TouchAction.Volume
      }
    }
    // Check horizontal swipe (seeking)
    else if (swipeHorizontalEnabled && horizontalSwipePercent > MINIMUM_HORIZONTAL_SWIPE) {
      currentTouchAction = TouchAction.Time
    }
  }

  /**
   * Process the detected gesture
   */
  private fun processGesture(
    startTouch: Utils.Vector2,
    currentTouch: Utils.Vector2,
    lastTouch: Utils.Vector2,
    duration: Long?
  ) {
    when (currentTouchAction) {
      TouchAction.Time -> handleTimeSwipe(startTouch, currentTouch, duration)
      TouchAction.Brightness -> {
        val diffFromLast = lastTouch - currentTouch
        val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / sHeight.toFloat()
        handleBrightnessSwipe(verticalAddition)
      }
      TouchAction.Volume -> {
        val diffFromLast = lastTouch - currentTouch
        val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / sHeight.toFloat()
        handleVolumeSwipe(verticalAddition)
      }
      null -> Unit
    }
  }


  // ========== Gesture-Specific Handlers ==========

  /**
   * Handle horizontal swipe for time seeking
   */
  private fun handleTimeSwipe(
    startTouch: Utils.Vector2,
    currentTouch: Utils.Vector2,
    duration: Long?
  ) {
    val startTime = currentTouchStartPlayerTime ?: return

    calculateNewTime(startTime, startTouch, currentTouch, duration)?.let { newMs ->
      val skipMs = newMs - startTime
      val skipPrefix = when {
        abs(skipMs) < 0 -> ""
        skipMs > 0 -> "+"
        else -> "-"
      }
      val text = "${newMs.formatDuration()} [$skipPrefix${abs(skipMs).formatDuration()}]"
      onSeekUpdate(newMs, text, true)
    }
  }

  /**
   * Handle vertical swipe for brightness adjustment
   */
  private fun handleBrightnessSwipe(verticalAddition: Float) {
    currentRequestedBrightness = (currentRequestedBrightness + verticalAddition).coerceIn(0.0f, 1.0f)
    setBrightness(currentRequestedBrightness)
    onBrightnessUpdate(currentRequestedBrightness, true)
  }

  /**
   * Handle vertical swipe for volume adjustment
   * Uses direct volume setting (like handleBrightnessSwipe) for immediate, smooth response
   */
  private fun handleVolumeSwipe(verticalAddition: Float) {
    val maxVolume = getMaxVolume() ?: return

    // Update requested volume with clamping
    currentRequestedVolume = (currentRequestedVolume + verticalAddition).coerceIn(0.0f, 1.0f)

    // Calculate desired volume level
    val desiredVolume = round(currentRequestedVolume * maxVolume).toInt().coerceIn(0, maxVolume)

    // Set volume directly for immediate response (like brightness adjustment)
    // Using FLAG_SHOW_UI = 0 to not show system volume UI (we have our own overlay)
    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, desiredVolume, 0)

    // Update UI with requested volume for smooth feedback
    onVolumeUpdate(currentRequestedVolume, true)
  }


  /**
   * Handle double tap gesture
   * Maps screen position to action: rewind (left), pause (center), forward (right)
   */
  private fun handleDoubleTap(currentTouch: Utils.Vector2) {
    Timber.i("Double tap at x=${currentTouch.x}, y=${currentTouch.y}")

    if (doubleTapPauseEnabled) {
      when {
        currentTouch.x < doubleTapPauseZoneStart -> {
          if (doubleTapEnabled) onDoubleTapRewind()
        }
        currentTouch.x > doubleTapPauseZoneEnd -> {
          if (doubleTapEnabled) onDoubleTapForward()
        }
        else -> onTogglePlayPause()
      }
    } else if (doubleTapEnabled) {
      if (currentTouch.x < halfScreenWidth) {
        onDoubleTapRewind()
      } else {
        onDoubleTapForward()
      }
    }
  }

  /**
   * Schedule a delayed single tap confirmation
   * Allows time to detect if a second tap occurs (double tap)
   */
  private fun scheduleSingleTap() {
    cancelPendingSingleTap()

    pendingSingleTapRunnable = Runnable {
      onSingleTap()
      pendingSingleTapRunnable = null
    }

    // Delay execution by double tap window time
    singleTapHandler.postDelayed(pendingSingleTapRunnable!!, DOUBLE_TAP_TIME_WINDOW)
  }

  /**
   * Cancel any pending single tap
   * Called when a double tap is detected to prevent false single tap
   */
  private fun cancelPendingSingleTap() {
    pendingSingleTapRunnable?.let {
      singleTapHandler.removeCallbacks(it)
      pendingSingleTapRunnable = null
      Timber.i("Single tap cancelled - double tap detected")
    }
  }


  // ========== Helper Methods ==========

  /**
   * Calculate new seek time based on horizontal swipe distance
   * Uses quadratic function for natural seeking feel
   */
  private fun calculateNewTime(
    startTime: Long,
    touchStart: Utils.Vector2?,
    touchEnd: Utils.Vector2?,
    duration: Long?
  ): Long? {
    if (touchStart == null || touchEnd == null || duration == null) return null

    val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / sWidth.toFloat()
    val sign = if (diffX < 0.0f) -1 else 1
    val seekOffset = (duration * (diffX * diffX) * sign).toLong()

    return (startTime + seekOffset).coerceIn(0, duration)
  }

  // ========== System Integration Methods ==========

  /**
   * Get current screen brightness
   * Falls back to window brightness if system brightness unavailable
   */
  private fun getBrightness(): Float? {
    return if (useTrueSystemBrightness) {
      try {
        Settings.System.getInt(
          context.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS
        ) / BRIGHTNESS_SCALE
      } catch (_: Exception) {
        useTrueSystemBrightness = false
        getBrightness() // Retry with window brightness
      }
    } else {
      try {
        (context as? Activity)?.window?.attributes?.screenBrightness
      } catch (_: Exception) {
        null
      }
    }
  }

  /**
   * Set screen brightness
   * Attempts system brightness first, falls back to window brightness
   */
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
          (brightness * BRIGHTNESS_SCALE).toInt()
        )
      } catch (_: Exception) {
        useTrueSystemBrightness = false
        setBrightness(brightness) // Retry with window brightness
      }
    } else {
      try {
        val activity = context as? Activity ?: return
        val lp = activity.window?.attributes ?: return
        lp.screenBrightness = brightness
        activity.window?.attributes = lp
      } catch (_: Exception) {
        // Silently fail - brightness control not available
      }
    }
  }

  /**
   * Get current media volume
   */
  private fun getVolume(): Int? {
    return try {
      audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Get maximum media volume
   */
  private fun getMaxVolume(): Int? {
    return try {
      audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    } catch (_: Exception) {
      null
    }
  }

  // ========== Public Icon Helpers ==========

  /**
   * Get appropriate brightness icon for current level
   */
  fun getBrightnessIcon(brightness: Float): Int {
    val index = round(brightness * (brightnessIcons.size - 1)).toInt()
    return brightnessIcons[index.coerceIn(0, brightnessIcons.size - 1)]
  }

  /**
   * Get appropriate volume icon for current level
   */
  fun getVolumeIcon(volumeRatio: Float): Int {
    val index = round(volumeRatio * (volumeIcons.size - 1)).toInt()
    return volumeIcons[index.coerceIn(0, volumeIcons.size - 1)]
  }
}


