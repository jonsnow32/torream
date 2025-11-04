package cloud.app.csplayer.ui.player.mpv

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.core.view.isGone
import androidx.core.view.isVisible
import cloud.app.csplayer.databinding.PlayerCustomLayoutBinding
import cloud.app.csplayer.utils.UIHelper.toPx

/**
 * Manages UI visibility and animations for the player
 */
class PlayerUIController(private val binding: PlayerCustomLayoutBinding) {

  var isShowing: Boolean = true
    private set

  var isLocked: Boolean = false
    private set

  /**
   * Toggle the visibility of player controls
   */
  fun toggleControls() {
    isShowing = !isShowing
    animateLayoutChanges()
  }

  /**
   * Show player controls
   */
  fun showControls() {
    if (!isShowing) {
      isShowing = true
      animateLayoutChanges()
    }
  }

  /**
   * Hide player controls
   */
  fun hideControls() {
    if (isShowing) {
      isShowing = false
      animateLayoutChanges()
    }
  }

  /**
   * Toggle lock state
   */
  fun toggleLock() {
    isLocked = !isLocked
    updateUIVisibility()

    val fadeTo = if (isLocked) 0f else 1f
    val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo).apply {
      duration = 100
      fillAfter = true
    }

    binding.apply {
      playerTopHolder.startAnimation(fadeAnimation)
      playerBottomHolder.startAnimation(fadeAnimation)
      playerCenterMenu.startAnimation(fadeAnimation)
    }
  }

  /**
   * Set lock state
   */
  fun setLocked(locked: Boolean) {
    if (isLocked != locked) {
      toggleLock()
    }
  }

  /**
   * Update UI visibility based on current state
   */
  fun updateUIVisibility() {
    val isGone = isLocked || !isShowing

    binding.apply {
      playerVideoBar.isGone = isGone
      playPauseToggle.isGone = isGone
      playerCenterMenu.isGone = isGone
      playerLock.isGone = !isShowing
      extraControls.isGone = isGone
      playerTopHolder.isGone = isGone
      playerGoBackHolder.isGone = isGone
      playerSourcesBtt.isGone = isGone
      playerSubtitleOffset.isGone = true

    }
  }

  /**
   * Animate layout changes when showing/hiding controls
   */

  fun View.moveUp(distance: Float, duration: Long = 200) {
    ObjectAnimator.ofFloat(this, "translationY", -distance).apply {
      this.duration = duration
      start()
    }
  }

  fun View.moveDown(distance: Float, duration: Long = 200) {
    ObjectAnimator.ofFloat(this, "translationY", distance).apply {
      this.duration = duration
      start()
    }
  }
  fun View.fadeIn(duration: Long = 200) {
    ObjectAnimator.ofFloat(this, "alpha", 1f).apply {
      this.duration = duration
      start()
    }
  }
  fun View.fadeOut(duration: Long = 200) {
    ObjectAnimator.ofFloat(this, "alpha", 0f).apply {
      this.duration = duration
      start()
    }
  }

  fun animateLayoutChanges() {
    if (isShowing) {
      updateUIVisibility()
    } else {
      binding.playerHolder.postDelayed({ updateUIVisibility() }, 200)
    }

    if (isShowing) {
      binding.playerTopHolder.moveDown(0.0f)
      binding.playerBottomHolder.moveUp(0.0f)
      binding.shadowOverlay.fadeIn()
      binding.playerCenterMenu.fadeIn()
    } else {
      binding.playerTopHolder.moveUp(70.toPx.toFloat())
      binding.playerBottomHolder.moveDown(70.toPx.toFloat())
      binding.playerCenterMenu.fadeOut()
      binding.shadowOverlay.fadeOut()
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
   * Update playback button state
   */
  fun updatePlayPauseButton(isPlaying: Boolean) {
    binding.playPauseToggle.isChecked = isPlaying
  }

  /**
   * Update progress bar
   */
  fun updateProgress(position: Int, duration: Int) {
    binding.exoProgress.apply {
      setDuration(duration.toLong())
      setPosition(position.toLong())
    }
  }

  /**
   * Set video title
   */
  fun setVideoTitle(title: String) {
    binding.playerVideoTitle.text = title
  }

  /**
   * Show gesture overlays
   */
  fun showBrightnessOverlay() {
    binding.playerProgressbarRightHolder.isVisible = true
  }

  fun showVolumeOverlay() {
    binding.playerProgressbarLeftHolder.isVisible = true
  }

  fun showSeekOverlay() {
    binding.playerTimeText.isVisible = true
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

  /**
   * Update brightness overlay with icon
   */
  fun updateBrightnessOverlay(brightness: Float, brightnessIcons: IntArray) {
    binding.apply {
      playerProgressbarRightHolder.isVisible = true
      playerProgressbarRight.max = 100_000
      playerProgressbarRight.progress = kotlin.math.max(2_000, (brightness * 100_000f).toInt())

      val iconIndex = kotlin.math.min(
        brightnessIcons.size - 1,
        kotlin.math.max(0, kotlin.math.round(brightness * (brightnessIcons.size - 1)).toInt())
      )
      playerProgressbarRightIcon.setImageResource(brightnessIcons[iconIndex])
    }
  }

  /**
   * Update volume overlay with icon
   */
  fun updateVolumeOverlay(volume: Int, maxVolume: Int, volumeIcons: IntArray) {
    binding.apply {
      val volumePercent = volume.toFloat() / maxVolume.toFloat()

      playerProgressbarLeftHolder.isVisible = true
      playerProgressbarLeft.max = 100_000
      playerProgressbarLeft.progress = kotlin.math.max(2_000, (volumePercent * 100_000f).toInt())

      val iconIndex = kotlin.math.min(
        volumeIcons.size - 1,
        kotlin.math.max(0, kotlin.math.round(volumePercent * (volumeIcons.size - 1)).toInt())
      )
      playerProgressbarLeftIcon.setImageResource(volumeIcons[iconIndex])
    }
  }

  /**
   * Update seek overlay
   */
  fun updateSeekOverlay(text: String) {
    binding.apply {
      playerTimeText.text = text
      playerTimeText.isVisible = true
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
    }
  }

  /**
   * Update brightness overlay progress
   */
  fun updateBrightnessProgress(progress: Int, max: Int = 100_000) {
    binding.apply {
      playerProgressbarRight.max = max
      playerProgressbarRight.progress = progress
    }
  }

  /**
   * Update volume overlay progress
   */
  fun updateVolumeProgress(progress: Int, max: Int = 100_000) {
    binding.apply {
      playerProgressbarLeft.max = max
      playerProgressbarLeft.progress = progress
    }
  }

  /**
   * Set seek overlay text
   */
  fun setSeekText(text: String) {
    binding.playerTimeText.text = text
  }

  /**
   * Enable/disable specific UI elements
   */
  fun setSpeedButtonVisible(visible: Boolean) {
    binding.playerSpeedBtt.isVisible = visible
  }

  fun setResizeButtonVisible(visible: Boolean) {
    binding.playerResizeBtt.isVisible = visible
  }

  fun setRotateButtonVisible(visible: Boolean) {
    binding.playerRotateBtt.isVisible = visible
  }

  /**
   * Update skip episode button visibility
   */
  fun setSkipEpisodeVisible(visible: Boolean) {
    binding.playerSkipEpisode.isVisible = visible
  }

  /**
   * Set video title visibility
   */
  fun setVideoTitleVisible(visible: Boolean) {
    binding.playerVideoTitle.isVisible = visible
  }

  /**
   * Set video title and resolution
   */
  fun setVideoTitleWithResolution(title: String, resolution: String) {
    binding.apply {
      playerVideoTitle.text = title
      playerVideoTitleRez.text = resolution
    }
  }

  /**
   * Update center menu visibility (for YouTube overlay integration)
   */
  fun setCenterMenuVisible(visible: Boolean) {
    binding.playerCenterMenu.isVisible = visible
  }

  /**
   * Update intro play button visibility
   */
  fun setIntroPlayVisible(visible: Boolean) {
    binding.playerIntroPlay.isVisible = visible
  }

  /**
   * Get the root view
   */
  fun getRootView(): View = binding.root

  // ========== MPV Event Handlers ==========

  /**
   * Handle MPV play event
   */
  fun onPlay() {
    updatePlayPauseButton(isPlaying = true)
    hideLoading()
  }

  /**
   * Handle MPV pause event
   */
  fun onPause() {
    updatePlayPauseButton(isPlaying = false)
  }

  /**
   * Handle MPV buffering event
   */
  fun onBuffering(isBuffering: Boolean) {
    if (isBuffering) {
      showLoading()
    } else {
      hideLoading()
    }
  }

  /**
   * Handle MPV seek event
   */
  fun onSeek(position: Long, duration: Long) {
    updateProgress(position.toInt(), duration.toInt())
  }

  /**
   * Handle MPV time position change
   */
  fun onTimePositionChange(position: Long, duration: Long) {
    updateProgress(position.toInt(), duration.toInt())
  }

  /**
   * Handle MPV error event
   */
  fun onError() {
    hideLoading()
  }

  /**
   * Handle MPV video loaded event
   */
  fun onVideoLoaded(title: String, resolution: String = "") {
    setVideoTitleWithResolution(title, resolution)
    hideLoading()
  }

  /**
   * Handle user interaction (tap/click)
   */
  fun onUserInteraction() {
    toggleControls()
  }

  /**
   * Handle double tap for seek
   */
  fun onDoubleTap(isForward: Boolean, seekTime: Int) {
    val text = if (isForward) "+$seekTime s" else "-$seekTime s"
    updateSeekOverlay(text)
  }

  /**
   * Reset all UI states
   */
  fun reset() {
    hideLoading()
    hideGestureOverlays()
    isShowing = true
    isLocked = false
    updateUIVisibility()
  }
}

