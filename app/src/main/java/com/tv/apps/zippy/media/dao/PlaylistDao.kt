package com.tv.apps.zippy.media.dao

import androidx.room.*
import com.tv.apps.zippy.media.entities.PlaylistEntity
import com.tv.apps.zippy.media.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

  // Playlist operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Update
  suspend fun updatePlaylist(playlist: PlaylistEntity)

  @Delete
  suspend fun deletePlaylist(playlist: PlaylistEntity)

  @Query("SELECT * FROM playlists ORDER BY created_at DESC")
  fun getAllPlaylists(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists WHERE id = :playlistId")
  suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

  @Query("SELECT * FROM playlists WHERE id = :playlistId")
  fun getPlaylistByIdFlow(playlistId: Long): Flow<PlaylistEntity?>

  @Query("SELECT * FROM playlists ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
  suspend fun getPlaylists(limit: Int, offset: Int): List<PlaylistEntity>

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylistById(playlistId: Long)

  // Playlist item operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

  @Delete
  suspend fun deletePlaylistItem(item: PlaylistItemEntity)

  @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND media_uri = :mediaUri")
  suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String)

  @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position ASC")
  fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

  @Query("""
    SELECT COUNT(*) FROM playlist_items WHERE playlist_id = :playlistId
  """)
  suspend fun getPlaylistItemCount(playlistId: Long): Int

  @Query("""
    SELECT MAX(position) FROM playlist_items WHERE playlist_id = :playlistId
  """)
  suspend fun getMaxPosition(playlistId: Long): Int?

  @Transaction
  suspend fun addMediaToPlaylist(playlistId: Long, mediaUri: String) {
    val maxPosition = getMaxPosition(playlistId) ?: -1
    val newPosition = maxPosition + 1
    val item = PlaylistItemEntity(
      playlistId = playlistId,
      mediaUri = mediaUri,
      position = newPosition
    )
    insertPlaylistItem(item)

    // Update playlist item count and updated_at
    val count = getPlaylistItemCount(playlistId)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist.copy(itemCount = count, updatedAt = System.currentTimeMillis()))
    }
  }

  @Transaction
  suspend fun removeMediaFromPlaylistAndUpdateCount(playlistId: Long, mediaUri: String) {
    removeMediaFromPlaylist(playlistId, mediaUri)

    // Update positions
    val items = getPlaylistItemsSync(playlistId)
    items.forEachIndexed { index, item ->
      updatePlaylistItem(item.copy(position = index))
    }

    // Update playlist item count and updated_at
    val count = getPlaylistItemCount(playlistId)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist.copy(itemCount = count, updatedAt = System.currentTimeMillis()))
    }
  }

  @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position ASC")
  suspend fun getPlaylistItemsSync(playlistId: Long): List<PlaylistItemEntity>

  @Update
  suspend fun updatePlaylistItem(item: PlaylistItemEntity)

  @Query("""
    SELECT EXISTS(
      SELECT 1 FROM playlist_items
      WHERE playlist_id = :playlistId AND media_uri = :mediaUri
    )
  """)
  suspend fun isMediaInPlaylist(playlistId: Long, mediaUri: String): Boolean
}

