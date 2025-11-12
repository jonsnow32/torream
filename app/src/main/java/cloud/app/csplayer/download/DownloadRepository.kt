package cloud.app.csplayer.download

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
  suspend fun loadAllTask(): List<DownloadTask>
  suspend fun insertTask(task: DownloadTask, initialStatus: DownloadStatus)
  fun observeState(taskId: String): Flow<DownloadState?>
  fun observeAllStates(): Flow<List<DownloadState>>
  suspend fun updateState(state: DownloadState)
  suspend fun deleteTask(taskId: String)
}
