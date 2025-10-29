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
            bottomPlayerBar.startAnimation(fadeAnimation)
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
    fun updateUIVisibility(isSameEpisode: Boolean = false, allLinksSize: Int = 0, currentLinkIndex: Int = 0) {
        val isGone = isLocked || !isShowing

        binding.apply {
            playerLockHolder.isGone = isGone
            playerVideoBar.isGone = isGone
            playerPausePlay.isGone = isGone
            playerTopHolder.isGone = isGone
            playerCenterMenu.isGone = isGone
            playerLock.isGone = !isShowing
            playerGoBackHolder.isGone = isGone
            playerSourcesBtt.isGone = isGone
            moreOptions.isGone = true
            playerSubttileOffset.isGone = true
            playerSkipEpisode.isGone = isSameEpisode || (currentLinkIndex >= allLinksSize - 1)
        }
    }

    /**
     * Animate layout changes when showing/hiding controls
     */
    fun animateLayoutChanges() {
        if (isShowing) {
            updateUIVisibility()
        } else {
            binding.playerHolder.postDelayed({ updateUIVisibility() }, 200)
        }

        // Animate title
        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        binding.playerVideoTitle.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        binding.playerVideoTitleRez.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }

        // Animate bottom bar
        val playerBarMove = if (isShowing) 0f else 70.toPx.toFloat()
        binding.bottomPlayerBar.let {
            ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
                duration = 200
                start()
            }
        }

        // Fade animation
        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo).apply {
            duration = 100
            fillAfter = true
        }

        // Animate source button
        val playerSourceMove = if (isShowing) 0f else -50.toPx.toFloat()
        binding.playerOpenSource.let {
            ObjectAnimator.ofFloat(it, "translationY", playerSourceMove).apply {
                duration = 200
                start()
            }
        }

        binding.apply {
            if (!isLocked) {
                playerFfwdHolder.alpha = 1f
                playerRewHolder.alpha = 1f
                shadowOverlay.isVisible = true
                shadowOverlay.startAnimation(fadeAnimation)
                playerFfwdHolder.startAnimation(fadeAnimation)
                playerRewHolder.startAnimation(fadeAnimation)
                playerPausePlay.startAnimation(fadeAnimation)
            }

            bottomPlayerBar.startAnimation(fadeAnimation)
            playerOpenSource.startAnimation(fadeAnimation)
            playerTopHolder.startAnimation(fadeAnimation)
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
        binding.playerPausePlay.isSelected = isPlaying
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
     * Get the root view
     */
    fun getRootView(): View = binding.root
}

