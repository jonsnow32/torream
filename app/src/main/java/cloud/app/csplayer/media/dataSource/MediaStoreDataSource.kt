package cloud.app.csplayer.media.dataSource

import cloud.app.csplayer.media.model.Media
import kotlinx.coroutines.flow.Flow

interface MediaStoreDataSource {
  fun observeMediaChanges(): Flow<Unit>
  suspend fun queryAllMedia(query: String? = null): List<Media>
  suspend fun queryMedia(query: String? = null, limit: Int, offset: Int): List<Media>
  suspend fun scanMedia(path: String?): Boolean
}
