package com.tv.apps.zippy.media.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data class representing a favorite item
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
  @PrimaryKey
  val id: String, // Can be file path, download ID, or any unique identifier
  val type: String, // "media", "download_http", "download_torrent"
  val title: String,
  val uri: String?, // File path or URL
  val thumbnailPath: String? = null,
  val addedAt: Long = System.currentTimeMillis()
)

/**
 * DAO for managing favorite items
 */
@Dao
interface FavoriteDao {

  @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
  fun observeAllFavorites(): Flow<List<FavoriteEntity>>

  @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
  suspend fun getFavoriteById(id: String): FavoriteEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
  suspend fun isFavorite(id: String): Boolean

  @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
  fun observeIsFavorite(id: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFavorite(favorite: FavoriteEntity)

  @Delete
  suspend fun deleteFavorite(favorite: FavoriteEntity)

  @Query("DELETE FROM favorites WHERE id = :id")
  suspend fun deleteFavoriteById(id: String)

  @Query("DELETE FROM favorites")
  suspend fun deleteAllFavorites()

  @Query("SELECT COUNT(*) FROM favorites")
  suspend fun getFavoriteCount(): Int

  @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
  fun observeFavoritesByType(type: String): Flow<List<FavoriteEntity>>
}

