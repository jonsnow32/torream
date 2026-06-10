package cloud.streamless.torream.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileStreamServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var server: FileServer? = null
    private var currentPort: Int = 0

    companion object {
        private const val DEFAULT_PORT = 8766
        private const val MAX_PORT_ATTEMPTS = 10
    }

    fun start() {
        if (server?.isAlive == true) return
        var port = DEFAULT_PORT
        var started = false
        var attempts = 0
        while (!started && attempts < MAX_PORT_ATTEMPTS) {
            try {
                server = FileServer(port)
                server!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                currentPort = port
                started = true
                Timber.i("LocalFileStreamServer started on port $port")
            } catch (_: Exception) {
                port++
                attempts++
            }
        }
        if (!started) throw IOException("Failed to start LocalFileStreamServer")
    }

    fun stop() {
        server?.stop()
        server = null
        Timber.i("LocalFileStreamServer stopped")
    }

    fun getHttpUrl(localUri: String): String {
        start()
        val ip = getLocalIpAddress()
        if (ip == null) Timber.w("LocalFileStreamServer: could not determine LAN IP, Chromecast may not reach the server")
        val effectiveIp = ip ?: "127.0.0.1"
        val url = "http://$effectiveIp:$currentPort/file?uri=${Uri.encode(localUri)}"
        Timber.i("LocalFileStreamServer: cast URL = $url")
        return url
    }

    private fun getLocalIpAddress(): String? = try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        // Prefer wlan0 (WiFi) — same network as Chromecast
        val wlan = interfaces.firstOrNull { it.name.startsWith("wlan") && it.isUp && !it.isLoopback }
        val preferred = wlan ?: interfaces.firstOrNull { it.isUp && !it.isLoopback && !it.isVirtual }
        preferred?.inetAddresses?.toList()
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) { null }

    private inner class FileServer(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            Timber.i("LocalFileStreamServer: ${session.method} ${session.uri} range=${session.headers["range"]}")
            if (!session.uri.startsWith("/file")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
            val uriParam = session.parameters["uri"]?.firstOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing uri")

            return try {
                val androidUri = Uri.parse(uriParam)
                val mimeType = resolveMimeType(androidUri)
                val fileSize = getSize(androidUri)
                Timber.i("LocalFileStreamServer: serving mimeType=$mimeType fileSize=$fileSize")
                val rangeHeader = session.headers["range"]

                if (rangeHeader != null && rangeHeader.startsWith("bytes=") && fileSize > 0) {
                    handleRangeRequest(androidUri, fileSize, rangeHeader, mimeType)
                } else {
                    val stream = openStream(androidUri)
                        ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
                    val response = if (fileSize > 0) {
                        newFixedLengthResponse(Response.Status.OK, mimeType, stream, fileSize)
                    } else {
                        newChunkedResponse(Response.Status.OK, mimeType, stream)
                    }
                    response.addHeader("Accept-Ranges", "bytes")
                    response
                }
            } catch (e: Exception) {
                Timber.e(e, "LocalFileStreamServer error")
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
            }
        }

        private fun handleRangeRequest(uri: Uri, fileSize: Long, rangeHeader: String, mimeType: String): Response {
            val rangeValue = rangeHeader.removePrefix("bytes=")
            val parts = rangeValue.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toLongOrNull() ?: (fileSize - 1) else fileSize - 1
            val contentLength = end - start + 1

            val stream = openStream(uri) ?: throw IOException("Cannot open stream for range request")
            stream.skip(start)

            val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, contentLength)
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
            return response
        }

        private fun openStream(uri: Uri) = when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> FileInputStream(File(uri.path!!))
            else -> null
        }

        private fun getSize(uri: Uri): Long = try {
            when (uri.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                "file" -> File(uri.path!!).length()
                else -> -1L
            }
        } catch (_: Exception) { -1L }

        private fun resolveMimeType(uri: Uri): String {
            if (uri.scheme == "content") {
                val type = context.contentResolver.getType(uri)
                if (!type.isNullOrEmpty() && type != "application/octet-stream") return type
                // Fall back to display name extension
                val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                val name = cursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                if (!name.isNullOrEmpty()) return getMimeType(name)
            }
            return getMimeType(uri.lastPathSegment ?: "")
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
