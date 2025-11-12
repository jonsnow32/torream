package cloud.app.csplayer.download

enum class DownloadType {
  HTTP,
  TORRENT
}

data class DownloadTask(
  val id: String,
  val type: DownloadType, // "http" or "torrent"
  val source: String, // url or magnet/infoHash
  val targetPath: String,
  val totalBytes: Long = 0,
  val createdAt: Long = System.currentTimeMillis()
)
