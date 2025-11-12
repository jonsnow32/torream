package cloud.app.csplayer.download

import kotlinx.coroutines.flow.Flow

interface DownloadManager {
  fun observe(taskId: String): Flow<DownloadState?>
  fun observeAll(): Flow<List<DownloadState>>
  suspend fun enqueue(task: DownloadTask)
  suspend fun start(taskId: String)
  suspend fun pause(taskId: String)
  suspend fun resume(taskId: String)
  suspend fun cancel(taskId: String)
}
