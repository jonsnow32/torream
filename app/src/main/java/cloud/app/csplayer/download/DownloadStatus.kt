package cloud.app.csplayer.download

enum class DownloadStatus {
  QUEUED,
  DOWNLOADING,
  PAUSED,
  FINISHED,
  COMPLETED, // Alias for FINISHED, used by workers
  SEEDING,
  FAILED,
  CANCELED
}
