package cloud.streamless.torream.utils

import android.net.Uri
import cloud.streamless.torream.media.dao.NetworkShareDao
import cloud.streamless.torream.ui.browse.network.SmbConnectionHandle
import cloud.streamless.torream.ui.browse.network.SmbPathUtils
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Local HTTP proxy that lets mpv (no native SMB support) play SMB files via the pure-Java smbj client. */
@Singleton
class SmbStreamServer @Inject constructor(
  private val networkShareDao: NetworkShareDao
) {
  private var server: SmbFileServer? = null
  private var currentPort: Int = 0

  companion object {
    private const val DEFAULT_PORT = 8767
    private const val MAX_PORT_ATTEMPTS = 10
  }

  fun start() {
    if (server?.isAlive == true) return
    var port = DEFAULT_PORT
    var started = false
    var attempts = 0
    while (!started && attempts < MAX_PORT_ATTEMPTS) {
      try {
        server = SmbFileServer(port)
        server!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        currentPort = port
        started = true
        Timber.i("SmbStreamServer started on port $port")
      } catch (_: Exception) {
        port++
        attempts++
      }
    }
    if (!started) throw IOException("Failed to start SmbStreamServer")
  }

  fun stop() {
    server?.stop()
    server = null
    Timber.i("SmbStreamServer stopped")
  }

  fun getStreamUrl(shareId: Long, relativePath: String): String {
    start()
    return "http://127.0.0.1:$currentPort/smb?shareId=$shareId&path=${Uri.encode(relativePath)}"
  }

  private inner class SmbFileServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
      if (!session.uri.startsWith("/smb")) {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
      }
      val shareId = session.parameters["shareId"]?.firstOrNull()?.toLongOrNull()
        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing shareId")
      val path = session.parameters["path"]?.firstOrNull()
        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing path")

      return try {
        val entity = runBlocking { networkShareDao.getById(shareId) }
          ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Share not found")
        val password = if (entity.encryptedPassword != null && entity.passwordIv != null) {
          CredentialCipher.decrypt(entity.encryptedPassword, entity.passwordIv)
        } else null

        val (shareName, fullPath) = SmbPathUtils.splitShareAndPath(entity.basePath, path)
        val handle = SmbConnectionHandle.open(entity.host, entity.port, entity.username, password)
        val diskShare = handle.openShare(shareName)
        val smbFile = diskShare.openFile(
          fullPath,
          setOf(AccessMask.GENERIC_READ),
          setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
          setOf(SMB2ShareAccess.FILE_SHARE_READ),
          SMB2CreateDisposition.FILE_OPEN,
          setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        val fileSize = smbFile.fileInformation.standardInformation.endOfFile
        val mimeType = getMimeType(path)
        val rangeHeader = session.headers["range"]

        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && fileSize > 0) {
          handleRangeRequest(handle, smbFile, fileSize, rangeHeader, mimeType)
        } else {
          val stream = closingInputStream(handle, smbFile)
          val response = if (fileSize > 0) {
            newFixedLengthResponse(Response.Status.OK, mimeType, stream, fileSize)
          } else {
            newChunkedResponse(Response.Status.OK, mimeType, stream)
          }
          response.addHeader("Accept-Ranges", "bytes")
          response
        }
      } catch (e: Exception) {
        Timber.e(e, "SmbStreamServer error")
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
      }
    }

    private fun handleRangeRequest(
      handle: SmbConnectionHandle,
      smbFile: com.hierynomus.smbj.share.File,
      fileSize: Long,
      rangeHeader: String,
      mimeType: String
    ): Response {
      val rangeValue = rangeHeader.removePrefix("bytes=")
      val parts = rangeValue.split("-")
      val start = parts[0].toLongOrNull() ?: 0L
      val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toLongOrNull() ?: (fileSize - 1) else fileSize - 1
      val contentLength = end - start + 1

      val stream = closingInputStream(handle, smbFile)
      stream.skip(start)

      val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, contentLength)
      response.addHeader("Accept-Ranges", "bytes")
      response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
      return response
    }

    /** Wraps the SMB read stream so closing it (done by NanoHTTPD once the response is sent) also releases the file/session/connection. */
    private fun closingInputStream(handle: SmbConnectionHandle, smbFile: com.hierynomus.smbj.share.File): InputStream {
      val raw = smbFile.inputStream
      return object : FilterInputStream(raw) {
        override fun close() {
          runCatching { super.close() }
          runCatching { smbFile.close() }
          handle.close()
        }
      }
    }

    private fun getMimeType(fileName: String) = when (fileName.substringAfterLast('.', "").lowercase()) {
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
      else -> "video/mp4"
    }
  }
}
