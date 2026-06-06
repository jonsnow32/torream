package cloud.streamless.torream.media.repository

import cloud.streamless.torream.media.dao.MediaPlaybackDao
import cloud.streamless.torream.media.entities.MediaPlaybackEntity
import cloud.streamless.torream.model.SubtitleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    _playbackUpdateTrigger.emit(1)
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
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update playback speed
   */
  suspend fun updateSpeed(mediaUri: String, speed: Float) {
    playbackDao.updateSpeed(mediaUri, speed)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update aspect ratio
   */
  suspend fun updateAspectRatio(mediaUri: String, aspectRatio: String?) {
    playbackDao.updateAspectRatio(mediaUri, aspectRatio)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update audio track
   */
  suspend fun updateAudioTrack(mediaUri: String, trackIndex: Int) {
    playbackDao.updateAudioTrack(mediaUri, trackIndex)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update text/subtitle track
   */
  suspend fun updateTextTrack(mediaUri: String, trackIndex: Int) {
    playbackDao.updateTextTrack(mediaUri, trackIndex)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update zoom type
   */
  suspend fun updateZoomType(mediaUri: String, zoomType: String) {
    playbackDao.updateZoomType(mediaUri, zoomType)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Update subtitle configuration
   */
  suspend fun updateSubtitles(mediaUri: String, subtitles: List<SubtitleData>?) {
    playbackDao.updateSubtitles(mediaUri, subtitles)
    _playbackUpdateTrigger.emit(currentValue++)
  }

  /**
   * Mark media as finished
   */
  suspend fun markAsFinished(mediaUri: String, isFinished: Boolean = true) {
    playbackDao.markAsFinished(mediaUri, isFinished)
    _playbackUpdateTrigger.emit(currentValue++)
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
    subtitles: List<SubtitleData>?,
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
      subtitles = subtitles,
      lastPlayedAt = System.currentTimeMillis(),
      isFinished = isFinished
    )
    playbackDao.insertOrUpdate(playback)
  }

  var currentValue = 0;
  private val _playbackUpdateTrigger = MutableStateFlow<Int>(0)
  val playbackUpdateTrigger: MutableStateFlow<Int> = _playbackUpdateTrigger

}

