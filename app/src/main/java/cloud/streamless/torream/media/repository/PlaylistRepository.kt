package cloud.streamless.torream.media.repository

import android.content.Context
import cloud.streamless.torream.media.dao.PlaylistDao
import cloud.streamless.torream.media.entities.PlaylistEntity
import cloud.streamless.torream.media.entities.PlaylistItemEntity
import cloud.streamless.torream.ui.player.mpv.MPVUtils.MEDIA_EXTENSIONS
import cloud.streamless.torream.utils.UnifiedFile
import cloud.streamless.torream.utils.UnifiedFileFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
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

  suspend fun getPlayLists(limit: Int, offset: Int): List<PlaylistEntity> {
    return playlistDao.getPlaylists(limit, offset)
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

  suspend fun createPlaylistFromFolder(context: Context, name: String, folderPath: String): Long = withContext(Dispatchers.IO) {
    val uniFile: UnifiedFile? = try {
      UnifiedFileFactory.fromPath(context, folderPath)
    } catch (e: Exception) {
      Timber.w(e, "createPlaylistFromFolder: failed to create UnifiedFile for $folderPath")
      null
    }

    if (uniFile == null || !uniFile.exists()) {
      Timber.w("createPlaylistFromFolder: path not found - $folderPath")
      throw IllegalArgumentException("Path not found: $folderPath")
    }

    val mediaUris = mutableListOf<String>()

    fun collect(dir: UnifiedFile) {
      try {
        dir.listFiles()?.forEach { file ->
          when {
            file.isDirectory -> collect(file)
            file.isFile -> {
              val ext = file.name.substringAfterLast('.', "").lowercase()
              if (ext in MEDIA_EXTENSIONS) {
                mediaUris.add(file.uri.toString())
              }
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Error collecting media files from ${dir.name}")
      }
    }

    if (uniFile.isFile) {
      val ext = uniFile.name.substringAfterLast('.', "").lowercase()
      if (ext in MEDIA_EXTENSIONS) {
        mediaUris.add(uniFile.uri.toString())
      }
    } else {
      collect(uniFile)
    }

    if (mediaUris.isEmpty()) {
      Timber.w("createPlaylistFromFolder: no media files found in $folderPath")
      throw IllegalArgumentException("No media files found in path: $folderPath")
    }

    // Insert playlist
    val playlistEntity = PlaylistEntity(
      id = 0L,
      name = name,
      createdAt = System.currentTimeMillis(),
      itemCount = mediaUris.size
    )

    val playlistId = playlistDao.insertPlaylist(playlistEntity)

    // Insert items preserving collected order
    var position = 0
    mediaUris.forEach { uri ->
      try {
        val item = PlaylistItemEntity(
          id = 0L,
          playlistId = playlistId,
          mediaUri = uri,
          position = position++
        )
        playlistDao.insertPlaylistItem(item)
      } catch (e: Exception) {
        Timber.w(e, "Failed to insert playlist item: $uri")
      }
    }

    Timber.d("createPlaylistFromFolder: created playlist '$name' ($playlistId) with ${mediaUris.size} items")
    playlistId
  }
}

