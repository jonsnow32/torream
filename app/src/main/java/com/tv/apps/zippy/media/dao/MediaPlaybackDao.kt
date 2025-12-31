package com.tv.apps.zippy.media.dao

import androidx.room.*
import com.tv.apps.zippy.media.entities.MediaPlaybackEntity
import com.tv.apps.zippy.model.SubtitleData
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPlaybackDao {

  /**
   * Insert or update playback information
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(playback: MediaPlaybackEntity)

  /**
   * Get playback information for a specific media URI
   */
  @Query("SELECT * FROM media_playback WHERE media_uri = :mediaUri")
  suspend fun getPlayback(mediaUri: String): MediaPlaybackEntity?

  /**
   * Get playback information as Flow for a specific media URI
   */
  @Query("SELECT * FROM media_playback WHERE media_uri = :mediaUri")
  fun getPlaybackFlow(mediaUri: String): Flow<MediaPlaybackEntity?>

  /**
   * Update playback position
   */
  @Query("UPDATE media_playback SET position = :position, last_played_at = :timestamp WHERE media_uri = :mediaUri")
  suspend fun updatePosition(mediaUri: String, position: Long, timestamp: Long = System.currentTimeMillis())

  /**
   * Update playback speed
   */
  @Query("UPDATE media_playback SET speed = :speed WHERE media_uri = :mediaUri")
  suspend fun updateSpeed(mediaUri: String, speed: Float)

  /**
   * Update aspect ratio
   */
  @Query("UPDATE media_playback SET aspect_ratio = :aspectRatio WHERE media_uri = :mediaUri")
  suspend fun updateAspectRatio(mediaUri: String, aspectRatio: String?)

  /**
   * Update audio track index
   */
  @Query("UPDATE media_playback SET audio_track_index = :trackIndex WHERE media_uri = :mediaUri")
  suspend fun updateAudioTrack(mediaUri: String, trackIndex: Int)

  /**
   * Update text/subtitle track index
   */
  @Query("UPDATE media_playback SET text_track_index = :trackIndex WHERE media_uri = :mediaUri")
  suspend fun updateTextTrack(mediaUri: String, trackIndex: Int)

  /**
   * Update zoom type
   */
  @Query("UPDATE media_playback SET zoom_type = :zoomType WHERE media_uri = :mediaUri")
  suspend fun updateZoomType(mediaUri: String, zoomType: String)

  /**
   * Update subtitle configuration
   */
  @Query("UPDATE media_playback SET subtitles = :subtitles WHERE media_uri = :mediaUri")
  suspend fun updateSubtitles(mediaUri: String, subtitles: List<SubtitleData>?)

  /**
   * Mark media as finished
   */
  @Query("UPDATE media_playback SET is_finished = :isFinished, last_played_at = :timestamp WHERE media_uri = :mediaUri")
  suspend fun markAsFinished(mediaUri: String, isFinished: Boolean, timestamp: Long = System.currentTimeMillis())

  /**
   * Get all playback records ordered by last played
   */
  @Query("SELECT * FROM media_playback ORDER BY last_played_at DESC")
  fun getAllPlaybacksFlow(): Flow<List<MediaPlaybackEntity>>

  /**
   * Get recently played media (not finished)
   */
  @Query("SELECT * FROM media_playback WHERE is_finished = 0 ORDER BY last_played_at DESC LIMIT :limit")
  fun getRecentlyPlayedFlow(limit: Int = 20): Flow<List<MediaPlaybackEntity>>

  /**
   * Delete playback information for a specific media
   */
  @Query("DELETE FROM media_playback WHERE media_uri = :mediaUri")
  suspend fun deletePlayback(mediaUri: String)

  /**
   * Delete all playback information
   */
  @Query("DELETE FROM media_playback")
  suspend fun deleteAll()

  /**
   * Delete old playback records (older than specified days)
   */
  @Query("DELETE FROM media_playback WHERE last_played_at < :timestamp")
  suspend fun deleteOlderThan(timestamp: Long)
}

