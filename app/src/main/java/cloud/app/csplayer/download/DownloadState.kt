package cloud.app.csplayer.download

data class DownloadState(
  val task: DownloadTask,
  val status: DownloadStatus,
  val downloadedBytes: Long = 0,
  val totalBytes: Long = 0,
  val progress: Int = 0, // 0..100
  val speed: Long = 0, // Download speed in bytes per second
  val uploadSpeed: Long = 0, // Upload speed for torrents
  val downloadSpeedBytesPerSec: Long = 0, // Deprecated, use 'speed' instead
  val completedAt: Long? = null,
  val error: String? = null
)
