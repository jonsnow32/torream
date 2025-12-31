package com.tv.apps.zippy.download

enum class DownloadType {
  HTTP,
  TORRENT
}

data class DownloadTask(
  val id: String,
  val type: DownloadType, // "http" or "torrent"
  val source: String, // url or magnet/infoHash
  val targetPath: String, // Directory or file path where download is saved
  val fileName: String? = null, // Complete file path when downloaded, or display name for the download
  val totalBytes: Long = 0,
  val createdAt: Long = System.currentTimeMillis()
)
