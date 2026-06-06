package cloud.streamless.torream.download

enum class DownloadType {
  HTTP,
  TORRENT,
  HLS  // HLS/M3U8 stream download
}

data class DownloadTask(
  val id: String,
  val type: DownloadType, // "http" or "torrent"
  val source: String, // url or magnet/infoHash
  val targetPath: String, // Directory or file path where download is saved
  val fileName: String? = null, // Complete file path when downloaded, or display name for the download
  val headers: Map<String, String>? = null, // Custom HTTP headers for download (key-value pairs)
  val totalBytes: Long = 0,
  val createdAt: Long = System.currentTimeMillis()
)
