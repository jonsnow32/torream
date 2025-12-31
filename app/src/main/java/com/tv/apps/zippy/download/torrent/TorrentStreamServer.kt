package com.tv.apps.zippy.download.torrent

import android.content.Context
import android.content.SharedPreferences
import com.tv.apps.zippy.R
import com.tv.apps.zippy.ui.settings.SettingsDownload.Companion.DEFAULT_TORRENT_PORT
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.helper.RequestAuthenticator
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorrentStreamServer - HTTP server for streaming torrent files
 * Based on LibreTorrent's streaming implementation using NanoHTTPD
 *
 * This allows the player to stream video files from torrents that are being downloaded.
 */
@Singleton
class TorrentStreamServer @Inject constructor(
  private val sessionManager: TorrentSessionManager,
  private val sharedPreferences: SharedPreferences,
  @ApplicationContext private val context: Context,
) {

  private var server: StreamServer? = null
  private var currentPort: Int = 0

  companion object {
    private const val MAX_PORT_ATTEMPTS = 10
  }

  /**
   * Start the streaming server
   */
  suspend fun start(): String = withContext(Dispatchers.IO) {
    if (server?.isAlive == true) {
      Timber.d("Stream server already running on port $currentPort")
      return@withContext "http://127.0.0.1:$currentPort"
    }

    var port = sharedPreferences.getInt(context.getString(R.string.download_torrent_port_key), DEFAULT_TORRENT_PORT)
    var started = false
    var attempts = 0

    while (!started && attempts < MAX_PORT_ATTEMPTS) {
      try {
        server = StreamServer(port)
        server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        currentPort = port
        started = true
        Timber.i("✅ Stream server started on port $port")
      } catch (_: Exception) {
        Timber.w("Port $port unavailable, trying next...")
        port++
        attempts++
      }
    }

    if (!started) {
      throw Exception("Failed to start stream server after $MAX_PORT_ATTEMPTS attempts")
    }

    "http://127.0.0.1:$currentPort"
  }

  /**
   * Stop the streaming server
   */
  suspend fun stop() = withContext(Dispatchers.IO) {
    server?.let { s ->
      try {
        s.stop()
        server = null
        Timber.i("✅ Stream server stopped")
      } catch (e: Exception) {
        Timber.e(e, "Error stopping stream server")
      }
    }
  }

  /**
   * Get streaming URL for a torrent file
   * @param infoHash The torrent info hash
   * @param fileIndex The file index within the torrent (0 for single-file torrents)
   */
  @Suppress("unused")
  suspend fun getStreamUrl(infoHash: String, fileIndex: Int = 0): String {
    val baseUrl = if (server?.isAlive == true) {
      "http://127.0.0.1:$currentPort"
    } else {
      start()
    }

    return "$baseUrl/stream/$infoHash/$fileIndex"
  }

  /**
   * NanoHTTPD server implementation
   */
  private inner class StreamServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
      val uri = session.uri
      Timber.d("Stream request: $uri")

      // Parse request: /stream/{infoHash}/{fileIndex}
      val parts = uri.split("/").filter { it.isNotEmpty() }

      if (parts.size < 3 || parts[0] != "stream") {
        return newFixedLengthResponse(
          Response.Status.BAD_REQUEST,
          "text/plain",
          "Invalid request format. Use: /stream/{infoHash}/{fileIndex}"
        )
      }

      val infoHash = parts[1]
      val fileIndex = parts.getOrNull(2)?.toIntOrNull() ?: 0

      return try {
        streamTorrentFile(infoHash, fileIndex, session)
      } catch (e: Exception) {
        Timber.e(e, "Error streaming file")
        newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          "text/plain",
          "Error: ${e.message}"
        )
      }
    }

    private fun streamTorrentFile(
      infoHash: String,
      fileIndex: Int,
      session: IHTTPSession
    ): Response {
      // Find torrent handle - use runBlocking since NanoHTTPD is synchronous
      val handle = runBlocking {
        val hash = org.libtorrent4j.Sha1Hash.parseHex(infoHash)
        sessionManager.findTorrent(hash)
      } ?: return newFixedLengthResponse(
        Response.Status.NOT_FOUND,
        "text/plain",
        "Torrent not found: $infoHash"
      )

      if (!handle.isValid) {
        return newFixedLengthResponse(
          Response.Status.NOT_FOUND,
          "text/plain",
          "Torrent handle invalid"
        )
      }

      val torrentInfo = handle.torrentFile()
        ?: return newFixedLengthResponse(
          Response.Status.NOT_FOUND,
          "text/plain",
          "Torrent info not available"
        )

      // Get file info
      val fileStorage = torrentInfo.files()
      if (fileIndex < 0 || fileIndex >= fileStorage.numFiles()) {
        return newFixedLengthResponse(
          Response.Status.BAD_REQUEST,
          "text/plain",
          "Invalid file index: $fileIndex (total files: ${fileStorage.numFiles()})"
        )
      }

      val filePath = fileStorage.filePath(fileIndex)
      val fileSize = fileStorage.fileSize(fileIndex)

      // Get save path
      val savePath = handle.savePath()
      val fullPath = File(savePath, filePath)

      Timber.d("Streaming file: ${fullPath.absolutePath}, size: $fileSize")

      if (!fullPath.exists()) {
        return newFixedLengthResponse(
          Response.Status.NOT_FOUND,
          "text/plain",
          "File not yet available: ${fullPath.name}"
        )
      }

      // Determine MIME type
      val mimeType = getMimeType(fullPath.name)

      // Handle range requests (important for video seeking)
      val range = session.headers["range"]

      return if (range != null && range.startsWith("bytes=")) {
        handleRangeRequest(fullPath, fileSize, range, mimeType)
      } else {
        // Full file response
        val inputStream = FileInputStream(fullPath)
        newChunkedResponse(Response.Status.OK, mimeType, inputStream)
      }
    }

    private fun handleRangeRequest(
      file: File,
      fileSize: Long,
      rangeHeader: String,
      mimeType: String
    ): Response {
      // Parse range: bytes=start-end
      val rangeValue = rangeHeader.substring("bytes=".length)
      val parts = rangeValue.split("-")

      val start = parts[0].toLongOrNull() ?: 0L
      val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
        parts[1].toLongOrNull() ?: (fileSize - 1)
      } else {
        fileSize - 1
      }

      val contentLength = end - start + 1

      Timber.d("Range request: start=$start, end=$end, length=$contentLength")

      val inputStream = FileInputStream(file).apply {
        skip(start)
      }

      val response = newFixedLengthResponse(
        Response.Status.PARTIAL_CONTENT,
        mimeType,
        inputStream,
        contentLength
      )

      response.addHeader("Accept-Ranges", "bytes")
      response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
      response.addHeader("Content-Length", contentLength.toString())

      return response
    }

    private fun getMimeType(fileName: String): String {
      val extension = fileName.substringAfterLast('.', "").lowercase()
      return when (extension) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "webm" -> "video/webm"
        "m4v" -> "video/x-m4v"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "m3u8" -> "application/x-mpegURL"
        else -> "application/octet-stream"
      }
    }
  }
}
