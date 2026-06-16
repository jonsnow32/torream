package cloud.streamless.torream.download

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
  suspend fun loadAllTask(): List<DownloadTask>
  suspend fun insertTask(task: DownloadTask, initialStatus: DownloadStatus)
  fun observeState(taskId: String): Flow<DownloadState?>
  fun observeAllStates(): Flow<List<DownloadState>>
  suspend fun updateState(state: DownloadState)
  suspend fun deleteTask(taskId: String)
  suspend fun setDownloadPrivate(id: String, type: DownloadType, isPrivate: Boolean, newPath: String)
  suspend fun getPrivateDownloads(): List<DownloadTask>
}
