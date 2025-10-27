package cloud.app.csplayer.media.dataSource

import cloud.app.csplayer.media.model.Media
import kotlinx.coroutines.flow.Flow

interface MediaStoreDataSource {
  fun observeMediaChanges(): Flow<Unit>
  suspend fun queryAllMedia(): List<Media>
  suspend fun scanMedia(path: String?): Boolean
}
