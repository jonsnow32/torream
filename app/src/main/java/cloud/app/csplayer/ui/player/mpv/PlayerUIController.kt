package cloud.app.csplayer.ui.player.mpv

import android.animation.ObjectAnimator
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.PlayerCustomLayoutBinding
import cloud.app.csplayer.utils.UIHelper.toPx
import com.ironsource.adqualitysdk.sdk.i.fa
import timber.log.Timber

/**
 * PlayerUIController - Clean and simple UI state management
 *
 * Responsibilities:
 * - Manage UI visibility (show/hide controls)
 * - Manage lock state
 * - Handle animations
 * - Update UI elements (title, progress, etc.)
 *
 * Does NOT handle:
 * - MPV events (handled by MPVFragment)
 * - Gesture detection (handled by PlayerGestureHandler)
 * - Dialogs (handled by PlayerDialogManager)
 */
class PlayerUIController(private val binding: PlayerCustomLayoutBinding) {

  // ========== UI State ==========

  /**
   * Controls visibility state
   * When true: All controls are visible
   * When false: Controls are hidden (unless locked)
   */
  var isShowing: Boolean = true
    private set

  /**
   * Lock state
   * When true: Only lock button visible, prevents accidental touches
   * When false: Normal control behavior
   */
  var isLocked: Boolean = false
    private set

  init {
    // Set initial lock icon
    updateLockIcon()
  }

  // ========== Public API ==========

  /**
   * Toggle controls visibility
   */
  fun toggleControls() {
    isShowing = !isShowing
    applyUIState()
  }

  /**
   * Show controls
   */
  fun show() {
    isShowing = true
    applyUIState()
  }

  /**
   * Hide controls
   */
  fun hide() {
    if (isLocked || !isShowing) return

    isShowing = false
    applyUIState()
  }

  /**
   * Toggle lock state
   */
  fun toggleLock() {
    isLocked = !isLocked
    updateLockIcon()
    applyUIState()
  }

  /**
   * Lock the player (show only lock button)
   */
  fun lock() {
    if (isLocked) return

    isLocked = true
    updateLockIcon()
    applyUIState()
  }

  /**
   * Unlock the player
   */
  fun unlock() {
    if (!isLocked) return

    isLocked = false
    isShowing = true // Show controls when unlocked
    updateLockIcon()
    applyUIState()
  }

  /**
   * Update lock icon based on current state
   */
  private fun updateLockIcon() {
    val iconRes = if (isLocked) {
      R.drawable.lock_close_icon  // Locked
    } else {
      R.drawable.lock_open_icon   // Unlocked
    }
    binding.playerLock.setImageResource(iconRes)
  }

  // ========== UI Updates ==========

  /**
   * Set video title
   */
  fun setTitle(title: String) {
    binding.playerVideoTitle.text = title
  }

  /**
   * Set video title with resolution
   */
  fun setTitleWithResolution(title: String, resolution: String) {
    binding.playerVideoTitle.text = if (resolution.isNotEmpty()) {
      "$title - $resolution"
    } else {
      title
    }
  }

  /**
   * Update play/pause button state
   */
  fun setPlaying(isPlaying: Boolean) {
    binding.playPauseToggle.isChecked = isPlaying
  }

  /**
   * Update progress bar
   */
  fun setProgress(position: Long, duration: Long) {
    binding.exoProgress.apply {
      setDuration(duration)
      setPosition(position)
    }
  }

  /**
   * Show loading indicator
   */
  fun showLoading() {
    binding.playerBuffering.isVisible = true
  }

  /**
   * Hide loading indicator
   */
  fun hideLoading() {
    binding.playerBuffering.isVisible = false
  }

  /**
   * Show intro play button
   */
  fun showIntroPlay() {
    binding.playerIntroPlay.isVisible = true
  }

  /**
   * Hide intro play button
   */
  fun hideIntroPlay() {
    binding.playerIntroPlay.isVisible = false
  }

  // ========== Gesture Overlays ==========

  /**
   * Show brightness overlay with progress
   */
  fun showBrightnessOverlay(brightness: Float, icons: IntArray) {
    binding.apply {
      playerProgressbarRightHolder.isVisible = true
      playerProgressbarRight.max = 100_000
      playerProgressbarRight.progress = (brightness * 100_000f).toInt().coerceAtLeast(2_000)

      val iconIndex = (brightness * (icons.size - 1)).toInt().coerceIn(0, icons.size - 1)
      playerProgressbarRightIcon.setImageResource(icons[iconIndex])
    }
  }

  /**
   * Show volume overlay with progress
   */
  fun showVolumeOverlay(volume: Int, maxVolume: Int, icons: IntArray) {
    val percent = volume.toFloat() / maxVolume.toFloat()

    binding.apply {
      playerProgressbarLeftHolder.isVisible = true
      playerProgressbarLeft.max = 100_000
      playerProgressbarLeft.progress = (percent * 100_000f).toInt().coerceAtLeast(2_000)

      val iconIndex = (percent * (icons.size - 1)).toInt().coerceIn(0, icons.size - 1)
      playerProgressbarLeftIcon.setImageResource(icons[iconIndex])
    }
  }

  /**
   * Show seek overlay with time text
   */
  fun showSeekOverlay(timeText: String) {
    binding.apply {
      playerTimeText.text = timeText
      playerTimeText.isVisible = true
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
    }
  }

  /**
   * Hide all gesture overlays
   */
  fun hideGestureOverlays() {
    binding.apply {
      playerTimeText.isVisible = false
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
    }
  }

  // ========== Special UI States ==========

  /**
   * Set center menu visibility (for YouTube overlay)
   */
  fun setCenterMenuVisible(visible: Boolean) {
    if (isLocked) return // Don't change when locked
    binding.playerCenterMenu.isVisible = visible
  }

  /**
   * Reset to initial state
   */
  fun reset() {
    isShowing = true
    isLocked = false
    hideLoading()
    hideGestureOverlays()
    applyUIState()
  }

  // ========== Internal Logic ==========

  /**
   * Apply current UI state with animations
   * Core logic: 2 states only
   * - LOCKED: Only lock button visible
   * - UNLOCKED: All controls visible/hidden based on isShowing
   */
  private fun applyUIState() {

    Timber.i("applyUIState %s", Exception().stackTrace.toString())
    if (isLocked) {
      // LOCKED STATE: Only lock button visible
      animateLocked()
    } else {
      // UNLOCKED STATE: Show/hide based on isShowing
      if (isShowing) {
        animateShow()
      } else {
        animateHide()
      }
    }
  }

  /**
   * Animate to locked state (only lock button visible)
   */
  private fun animateLocked() {
    binding.apply {
      playerLock.animateToVisible()
      playerTopHolder.animateSlideUp()
      playerVideoBar.animateSlideDown()
      extraControls.animateSlideDown()
      shadowOverlay.animateFadeOut()
      playerCenterMenu.animateFadeOut()
    }

    // Update visibility after animation
    binding.root.postDelayed({
      binding.apply {
        playerLock.isGone = false
        playerLock.isVisible = true
        playerTopHolder.isGone = true
        shadowOverlay.isGone = true
        playerCenterMenu.isGone = true
        playerVideoBar.isGone = true
        extraControls.isGone = true
      }
    }, ANIMATION_DURATION)
  }

  /**
   * Animate to show all controls
   */
  private fun animateShow() {
    binding.apply {
      playerLock.isGone = false
      playerTopHolder.isGone = false
      extraControls.isGone = false
      shadowOverlay.isGone = false
      playerCenterMenu.isGone = false
      playerVideoBar.isGone = false

      playerLock.animateToVisible()
      playerTopHolder.animateToVisible()
      extraControls.animateToVisible()
      shadowOverlay.animateFadeIn()
      playerCenterMenu.animateFadeIn()
      playerVideoBar.animateToVisible()
    }

  }

  /**
   * Animate to hide all controls
   */
  private fun animateHide() {
    binding.apply {
      playerLock.animateSlideDown()
      playerTopHolder.animateSlideUp()
      extraControls.animateSlideDown()
      shadowOverlay.animateFadeOut()
      playerCenterMenu.animateFadeOut()
      playerVideoBar.animateSlideDown()
    }

    // Update visibility after animation
    binding.root.postDelayed({
      binding.apply {
        playerLock.isGone = true
        playerTopHolder.isGone = true
        extraControls.isGone = true
        shadowOverlay.isGone = true
        playerCenterMenu.isGone = true
        playerVideoBar.isGone = true
      }
    }, ANIMATION_DURATION)
  }

  // ========== Animation Extensions ==========

  private fun View.animateToVisible() {
    isVisible = true
    isGone = false
    animateFadeIn()
    ObjectAnimator.ofFloat(this, "translationY", 0f).apply {
      this.duration = ANIMATION_DURATION
      start()
    }
  }

  private fun View.animateSlideUp() {
    ObjectAnimator.ofFloat(this, "translationY", -100.toPx.toFloat()).apply {
      this.duration = ANIMATION_DURATION
      start()
    }
  }

  private fun View.animateSlideDown() {
    ObjectAnimator.ofFloat(this, "translationY", 100.toPx.toFloat()).apply {
      this.duration = ANIMATION_DURATION
      start()
    }
  }

  private fun View.animateFadeIn() {
    ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
      this.duration = ANIMATION_DURATION
      start()
    }
  }

  private fun View.animateFadeOut() {
    ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
      this.duration = ANIMATION_DURATION
      start()
    }
  }

  companion object {
    private const val ANIMATION_DURATION = 200L
  }
}

