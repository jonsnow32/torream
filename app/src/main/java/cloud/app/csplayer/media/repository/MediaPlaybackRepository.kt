package cloud.app.csplayer.media.repository

import cloud.app.csplayer.media.dao.MediaPlaybackDao
import cloud.app.csplayer.media.entities.MediaPlaybackEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPlaybackRepository @Inject constructor(
  private val playbackDao: MediaPlaybackDao
) {

  /**
   * Save or update playback information
   */
  suspend fun savePlayback(playback: MediaPlaybackEntity) {
    playbackDao.insertOrUpdate(playback)
  }

  /**
   * Get playback information for a specific media
   */
  suspend fun getPlayback(mediaUri: String): MediaPlaybackEntity? {
    return playbackDao.getPlayback(mediaUri)
  }

  /**
   * Get playback information as Flow
   */
  fun getPlaybackFlow(mediaUri: String): Flow<MediaPlaybackEntity?> {
    return playbackDao.getPlaybackFlow(mediaUri)
  }

  /**
   * Update playback position
   */
  suspend fun updatePosition(mediaUri: String, position: Long) {
    playbackDao.updatePosition(mediaUri, position)
  }

  /**
   * Update playback speed
   */
  suspend fun updateSpeed(mediaUri: String, speed: Float) {
    playbackDao.updateSpeed(mediaUri, speed)
  }

  /**
   * Update aspect ratio
   */
  suspend fun updateAspectRatio(mediaUri: String, aspectRatio: String?) {
    playbackDao.updateAspectRatio(mediaUri, aspectRatio)
  }

  /**
   * Update audio track
   */
  suspend fun updateAudioTrack(mediaUri: String, trackIndex: Int) {
    playbackDao.updateAudioTrack(mediaUri, trackIndex)
  }

  /**
   * Update text/subtitle track
   */
  suspend fun updateTextTrack(mediaUri: String, trackIndex: Int) {
    playbackDao.updateTextTrack(mediaUri, trackIndex)
  }

  /**
   * Update zoom type
   */
  suspend fun updateZoomType(mediaUri: String, zoomType: String) {
    playbackDao.updateZoomType(mediaUri, zoomType)
  }

  /**
   * Update subtitle configuration
   */
  suspend fun updateSubtitleConfig(mediaUri: String, subtitleConfig: String?) {
    playbackDao.updateSubtitleConfig(mediaUri, subtitleConfig)
  }

  /**
   * Update volume
   */
  suspend fun updateVolume(mediaUri: String, volume: Float) {
    playbackDao.updateVolume(mediaUri, volume)
  }

  /**
   * Update brightness
   */
  suspend fun updateBrightness(mediaUri: String, brightness: Float) {
    playbackDao.updateBrightness(mediaUri, brightness)
  }

  /**
   * Mark media as finished
   */
  suspend fun markAsFinished(mediaUri: String, isFinished: Boolean = true) {
    playbackDao.markAsFinished(mediaUri, isFinished)
  }

  /**
   * Get all playback records
   */
  fun getAllPlaybacks(): Flow<List<MediaPlaybackEntity>> {
    return playbackDao.getAllPlaybacksFlow()
  }

  /**
   * Get recently played media
   */
  fun getRecentlyPlayed(limit: Int = 20): Flow<List<MediaPlaybackEntity>> {
    return playbackDao.getRecentlyPlayedFlow(limit)
  }

  /**
   * Delete playback information
   */
  suspend fun deletePlayback(mediaUri: String) {
    playbackDao.deletePlayback(mediaUri)
  }

  /**
   * Delete all playback information
   */
  suspend fun deleteAll() {
    playbackDao.deleteAll()
  }

  /**
   * Delete old playback records (older than specified days)
   */
  suspend fun deleteOlderThan(days: Int) {
    val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
    playbackDao.deleteOlderThan(timestamp)
  }

  /**
   * Create or update full playback state
   */
  suspend fun saveFullPlaybackState(
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
    val playback = MediaPlaybackEntity(
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
      lastPlayedAt = System.currentTimeMillis(),
      isFinished = isFinished
    )
    playbackDao.insertOrUpdate(playback)
  }
}

