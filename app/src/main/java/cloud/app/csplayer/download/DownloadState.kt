package cloud.app.csplayer.download

data class DownloadState(
  val task: DownloadTask,
  val status: DownloadStatus,
  val downloadedBytes: Long = 0,
  val progress: Int = 0, // 0..100
  val downloadSpeedBytesPerSec: Long = 0,
  val error: String? = null
)
