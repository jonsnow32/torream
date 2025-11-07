package cloud.app.csplayer.model


data class TorrentState(
  val infoHash: String,
  val name: String,
  val status: TorrentDownloadStatus,
  val progress: Float = 0f,
  val downloadSpeed: Long = 0,
  val uploadSpeed: Long = 0,
  val totalSize: Long = 0,
  val downloadedSize: Long = 0,
  val numPeers: Int = 0,
  val numSeeds: Int = 0,
  val error: String? = null
)

enum class TorrentDownloadStatus {
  DOWNLOADING,
  PAUSED,
  SEEDING,
  FINISHED,
  ERROR
}
