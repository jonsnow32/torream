package cloud.app.csplayer.download

enum class DownloadStatus {
  QUEUED,
  DOWNLOADING,
  PAUSED,
  FINISHED,
  SEEDING,
  FAILED,
  CANCELED
}
