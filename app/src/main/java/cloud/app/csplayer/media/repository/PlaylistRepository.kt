package cloud.app.csplayer.media.repository

import cloud.app.csplayer.media.dao.PlaylistDao
import cloud.app.csplayer.media.entities.PlaylistEntity
import cloud.app.csplayer.media.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
  private val playlistDao: PlaylistDao
) {

  fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
    return playlistDao.getAllPlaylists()
  }

  suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? {
    return playlistDao.getPlaylistById(playlistId)
  }

  fun getPlaylistByIdFlow(playlistId: Long): Flow<PlaylistEntity?> {
    return playlistDao.getPlaylistByIdFlow(playlistId)
  }

  suspend fun createPlaylist(name: String, description: String? = null): Long {
    val playlist = PlaylistEntity(
      name = name,
      description = description
    )
    return playlistDao.insertPlaylist(playlist)
  }

  suspend fun updatePlaylist(playlist: PlaylistEntity) {
    playlistDao.updatePlaylist(playlist)
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    playlistDao.deletePlaylist(playlist)
  }

  suspend fun deletePlaylistById(playlistId: Long) {
    playlistDao.deletePlaylistById(playlistId)
  }

  suspend fun addMediaToPlaylist(playlistId: Long, mediaUri: String) {
    playlistDao.addMediaToPlaylist(playlistId, mediaUri)
  }

  suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) {
    playlistDao.removeMediaFromPlaylistAndUpdateCount(playlistId, mediaUri)
  }

  fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> {
    return playlistDao.getPlaylistItems(playlistId)
  }

  suspend fun isMediaInPlaylist(playlistId: Long, mediaUri: String): Boolean {
    return playlistDao.isMediaInPlaylist(playlistId, mediaUri)
  }

  suspend fun getPlaylistItemCount(playlistId: Long): Int {
    return playlistDao.getPlaylistItemCount(playlistId)
  }
}

