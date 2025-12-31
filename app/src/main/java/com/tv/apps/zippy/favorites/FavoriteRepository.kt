package com.tv.apps.zippy.favorites

import com.tv.apps.zippy.media.dao.FavoriteDao
import com.tv.apps.zippy.media.dao.FavoriteEntity
import com.tv.apps.zippy.media.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
  private val favoriteDao: FavoriteDao,
  private val mediaRepository: MediaRepository
) {

  fun observeAllFavorites(): Flow<List<FavoriteEntity>> {
    return favoriteDao.observeAllFavorites()
  }

  fun observeFavoritesByType(type: String): Flow<List<FavoriteEntity>> {
    return favoriteDao.observeFavoritesByType(type)
  }

  suspend fun getFavoriteById(id: String): FavoriteEntity? {
    return favoriteDao.getFavoriteById(id)
  }

  suspend fun isFavorite(id: String): Boolean {
    return favoriteDao.isFavorite(id)
  }

  fun observeIsFavorite(id: String): Flow<Boolean> {
    return favoriteDao.observeIsFavorite(id)
  }

  suspend fun addFavorite(favorite: FavoriteEntity) {
    favoriteDao.insertFavorite(favorite)
  }

  suspend fun removeFavorite(id: String) {
    favoriteDao.deleteFavoriteById(id)
  }

  suspend fun toggleFavorite(favorite: FavoriteEntity): Boolean {
    return if (isFavorite(favorite.id)) {
      removeFavorite(favorite.id)
      false
    } else {
      addFavorite(favorite)
      true
    }
  }

  suspend fun isHistory(uri: String): Boolean {
    return mediaRepository.isInHistory(uri)
  }

  suspend fun clearAllFavorites() {
    favoriteDao.deleteAllFavorites()
  }

  suspend fun getFavoriteCount(): Int {
    return favoriteDao.getFavoriteCount()
  }
}

