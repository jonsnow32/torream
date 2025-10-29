package cloud.app.csplayer.media.playback

import cloud.app.csplayer.media.entities.MediaPlaybackEntity
import cloud.app.csplayer.media.repository.MediaPlaybackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to manage media playback state
 * Example usage in your player (MPV, ExoPlayer, etc.)
 */
@Singleton
class PlaybackStateManager @Inject constructor(
  private val playbackRepository: MediaPlaybackRepository
) {

  /**
   * Load saved playback state for a media file
   * Call this when initializing the player
   *
   * Example:
   * ```
   * val playbackState = playbackStateManager.loadPlaybackState(mediaUri)
   * if (playbackState != null) {
   *   player.seekTo(playbackState.position)
   *   player.setPlaybackSpeed(playbackState.speed)
   *   player.selectAudioTrack(playbackState.audioTrackIndex)
   *   player.selectTextTrack(playbackState.textTrackIndex)
   *   // ... apply other settings
   * }
   * ```
   */
  suspend fun loadPlaybackState(mediaUri: String): MediaPlaybackEntity? {
    return playbackRepository.getPlayback(mediaUri)
  }

  /**
   * Save current playback state
   * Call this periodically (e.g., every 5 seconds) or when pausing/stopping
   *
   * Example:
   * ```
   * playbackStateManager.savePlaybackState(
   *   mediaUri = currentMediaUri,
   *   position = player.currentPosition,
   *   speed = player.playbackSpeed,
   *   audioTrackIndex = player.currentAudioTrackIndex,
   *   textTrackIndex = player.currentTextTrackIndex,
   *   zoomType = player.zoomType,
   *   subtitleConfig = player.subtitleConfigAsJson()
   * )
   * ```
   */
  suspend fun savePlaybackState(
    mediaUri: String,
    position: Long,
    speed: Float = 1.0f,
    aspectRatio: String? = null,
    audioTrackIndex: Int = -1,
    textTrackIndex: Int = -1,
    zoomType: String = "fit",
    subtitleConfig: String? = null,
    volume: Float = 1.0f,
    brightness: Float = 0f,
    isFinished: Boolean = false
  ) {
    playbackRepository.saveFullPlaybackState(
      mediaUri = mediaUri,
      position = position,
      speed = speed,
      aspectRatio = aspectRatio,
      audioTrackIndex = audioTrackIndex,
      textTrackIndex = textTrackIndex,
      zoomType = zoomType,
      subtitleConfig = subtitleConfig,
      volume = volume,
      brightness = brightness,
      isFinished = isFinished
    )
  }

  /**
   * Quick update position only (for frequent updates)
   * Call this during playback to update position
   *
   * Example:
   * ```
   * // In a timer or player callback
   * playbackStateManager.updatePosition(currentMediaUri, player.currentPosition)
   * ```
   */
  suspend fun updatePosition(mediaUri: String, position: Long) {
    playbackRepository.updatePosition(mediaUri, position)
  }

  /**
   * Mark media as finished when playback completes
   *
   * Example:
   * ```
   * // When player reaches end
   * playbackStateManager.markFinished(mediaUri)
   * ```
   */
  suspend fun markFinished(mediaUri: String) {
    playbackRepository.markAsFinished(mediaUri, true)
  }

  /**
   * Reset finished status (e.g., when user manually restarts video)
   *
   * Example:
   * ```
   * // When user clicks "Play from beginning"
   * playbackStateManager.resetFinished(mediaUri)
   * ```
   */
  suspend fun resetFinished(mediaUri: String) {
    playbackRepository.markAsFinished(mediaUri, false)
  }

  /**
   * Check if media should resume from saved position
   * Returns true if there's a saved position > 0 and media is not finished
   *
   * Example:
   * ```
   * if (playbackStateManager.shouldResume(mediaUri)) {
   *   showResumeDialog()
   * }
   * ```
   */
  suspend fun shouldResume(mediaUri: String): Boolean {
    val playback = playbackRepository.getPlayback(mediaUri)
    return playback != null && playback.position > 0 && !playback.isFinished
  }

  /**
   * Get resume position in milliseconds
   * Returns 0 if no saved position
   */
  suspend fun getResumePosition(mediaUri: String): Long {
    return playbackRepository.getPlayback(mediaUri)?.position ?: 0L
  }

  /**
   * Clear playback history for a media file
   *
   * Example:
   * ```
   * // When user wants to reset playback state
   * playbackStateManager.clearPlaybackState(mediaUri)
   * ```
   */
  suspend fun clearPlaybackState(mediaUri: String) {
    playbackRepository.deletePlayback(mediaUri)
  }
}

