package cloud.app.csplayer.media.repository

import android.media.MediaMetadata
import cloud.app.csplayer.media.model.Folder
import cloud.app.csplayer.media.model.Media
import cloud.app.csplayer.media.model.MediaTypeFilter
import cloud.app.csplayer.media.model.SyncState
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
  fun observeMedia(): Flow<List<Media>>
  fun observeFolders(): Flow<List<Folder>>
  fun observeSyncState(): Flow<SyncState>
  suspend fun refreshMedia(path: String? = null): Boolean
  suspend fun getMediaByFolder(folderPath: String): List<Media>
  suspend fun getMediaByFolderPaged(folderPath: String, limit: Int, offset: Int): List<Media>
  suspend fun getMediaByFolderPagedFiltered(folderPath: String, limit: Int, offset: Int, mediaTypeFilter: MediaTypeFilter): List<Media>
  suspend fun getAllMediaPaged(limit: Int, offset: Int): List<Media>
  suspend fun getAllMediaPagedFiltered(limit: Int, offset: Int, mediaTypeFilter: MediaTypeFilter): List<Media>
  suspend fun getFoldersPaged(limit: Int, offset: Int): List<Folder>
  suspend fun getSubfoldersPaged(parentPath: String, limit: Int, offset: Int): List<Folder>
  suspend fun countMediaInFolder(folderPath: String): Int
  suspend fun countAllFolders(): Int
  suspend fun countAllMedia(): Int
  suspend fun updateMediaMetadata(uri: String, metadata: MediaMetadata)

  suspend fun search(query: String, limit: Int, offset: Int) :  List<Media>
}
