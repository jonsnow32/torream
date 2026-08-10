package cloud.streamless.torream.ui.browse.network

import cloud.streamless.torream.model.NetworkShare
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException

/** Lists an FTP directory. Connects fresh per call and disconnects afterwards - simple, avoids managing idle connections. */
class FtpBrowseClient {

  fun list(share: NetworkShare, relativePath: String): List<BrowseEntry> {
    val basePath = share.basePath.trim('/')
    val subPath = relativePath.trim('/')
    val fullPath = "/" + listOf(basePath, subPath).filter { it.isNotEmpty() }.joinToString("/")

    val client = FTPClient()
    try {
      client.connectTimeout = 10_000
      client.connect(share.host, share.port)
      if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(client.replyCode)) {
        throw IOException("FTP connect failed: ${client.replyCode}")
      }

      val username = share.username.takeUnless { it.isNullOrEmpty() } ?: "anonymous"
      if (!client.login(username, share.password.orEmpty())) {
        throw IOException("FTP login failed")
      }
      client.enterLocalPassiveMode()
      client.setFileType(FTP.BINARY_FILE_TYPE)

      val files = client.listFiles(fullPath)
      return files
        .filter { it.name != "." && it.name != ".." }
        .map { file ->
          BrowseEntry(
            name = file.name,
            isDirectory = file.isDirectory,
            size = file.size,
            lastModified = file.timestamp?.timeInMillis ?: 0L,
            relativePath = if (subPath.isEmpty()) file.name else "$subPath/${file.name}"
          )
        }
    } finally {
      runCatching {
        if (client.isConnected) {
          client.logout()
          client.disconnect()
        }
      }
    }
  }
}
