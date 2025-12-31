package com.tv.apps.zippy.media.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tv.apps.zippy.media.entities.MediaEntity
import com.tv.apps.zippy.media.entities.MediaWithPlayback
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
  @Query("SELECT * FROM media")
  fun observeAll(): Flow<List<MediaEntity>>

  @Query("SELECT * FROM media")
  suspend fun getAll(): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media")
  suspend fun getAllWithPlayback(): List<MediaWithPlayback>

  @Query("SELECT * FROM media LIMIT :limit OFFSET :offset")
  suspend fun getAllPaged(limit: Int, offset: Int): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media LIMIT :limit OFFSET :offset")
  suspend fun getAllPagedWithPlayback(limit: Int, offset: Int): List<MediaWithPlayback>

  @Query("SELECT * FROM media WHERE mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getAllPagedFiltered(
    mimeTypePattern: String,
    limit: Int,
    offset: Int
  ): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media WHERE mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getAllPagedFilteredWithPlayback(
    mimeTypePattern: String,
    limit: Int,
    offset: Int
  ): List<MediaWithPlayback>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath")
  suspend fun getByFolder(folderPath: String): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media WHERE parent_path = :folderPath")
  suspend fun getByFolderWithPlayback(folderPath: String): List<MediaWithPlayback>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPaged(folderPath: String, limit: Int, offset: Int): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media WHERE parent_path = :folderPath LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPagedWithPlayback(
    folderPath: String,
    limit: Int,
    offset: Int
  ): List<MediaWithPlayback>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath AND mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPagedFiltered(
    folderPath: String,
    mimeTypePattern: String,
    limit: Int,
    offset: Int
  ): List<MediaEntity>

  @Transaction
  @Query("SELECT * FROM media WHERE parent_path = :folderPath AND mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPagedFilteredWithPlayback(
    folderPath: String,
    mimeTypePattern: String,
    limit: Int,
    offset: Int
  ): List<MediaWithPlayback>

  @Query("SELECT COUNT(*) FROM media WHERE parent_path = :folderPath")
  suspend fun countMediaInFolder(folderPath: String): Int

  @Query("SELECT COUNT(*) FROM media")
  suspend fun countAllMedia(): Int

  @Upsert
  suspend fun upsertAll(media: List<MediaEntity>)

  @Query("DELETE FROM media WHERE uri IN (:uris)")
  suspend fun deleteByUris(uris: List<String>)

  @Query("SELECT * FROM media WHERE uri = :uri LIMIT 1")
  suspend fun getByUri(uri: String): MediaEntity?

  @Transaction
  @Query("SELECT * FROM media WHERE uri = :uri LIMIT 1")
  suspend fun getByUriWithPlayback(uri: String): MediaWithPlayback?

  @Transaction
  @Query(
    """
    SELECT m.* FROM media m
    INNER JOIN media_playback mp ON m.uri = mp.media_uri
    WHERE mp.is_finished = 0
    ORDER BY mp.last_played_at DESC
    LIMIT :limit OFFSET :offset
  """
  )
  suspend fun getRecentlyPlayedWithPlayback(limit: Int, offset: Int): List<MediaWithPlayback>

  @Query("DELETE FROM media_playback")
  suspend fun clearPlaybackHistory()

  @Query("DELETE FROM media_playback WHERE media_uri = :mediaUri")
  suspend fun deletePlaybackHistory(mediaUri: String)

  @Query(
    """
  SELECT EXISTS(
    SELECT 1 FROM media_playback mp
    WHERE mp.media_uri = :uri AND mp.is_finished = 0
    LIMIT 1
  )
"""
  )
  suspend fun isInHistory(uri: String): Boolean

  @Transaction
  suspend fun transaction(block: suspend () -> Unit) {
    block()
  }
}
