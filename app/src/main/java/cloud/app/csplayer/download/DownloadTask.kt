package cloud.app.csplayer.download

enum class DownloadType {
  HTTP,
  TORRENT
}

data class DownloadTask(
  val id: String,
  val type: DownloadType, // "http" or "torrent"
  val source: String, // url or magnet/infoHash
  val targetPath: String, // Directory or file path where download is saved
  val title: String? = null, // Display name for the download (filename for HTTP, torrent name for TORRENT)
  val totalBytes: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val downloadedFilePath: String? = null // Actual file path of downloaded video (set when complete)
)
