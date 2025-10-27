package cloud.app.csplayer.media.repository

import android.media.MediaMetadata
import cloud.app.csplayer.media.model.Folder
import cloud.app.csplayer.media.model.Media
import cloud.app.csplayer.media.model.SyncState
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
  fun observeMedia(): Flow<List<Media>>
  fun observeFolders(): Flow<List<Folder>>
  fun observeSyncState(): Flow<SyncState>
  suspend fun refreshMedia(path: String? = null): Boolean
  suspend fun getMediaByFolder(folderPath: String): List<Media>
  suspend fun updateMediaMetadata(uri: String, metadata: MediaMetadata)
}
