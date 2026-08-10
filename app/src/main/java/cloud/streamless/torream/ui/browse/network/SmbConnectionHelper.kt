package cloud.streamless.torream.ui.browse.network

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare

/** Splits a saved share's basePath ("/ShareName/sub/dir") into (shareName, pathWithinShare). */
object SmbPathUtils {
  fun splitShareAndPath(basePath: String, extra: String = ""): Pair<String, String> {
    val segments = basePath.trim('/').split("/").filter { it.isNotEmpty() }
    val shareName = segments.firstOrNull() ?: ""
    val subPath = segments.drop(1).joinToString("/")
    val combined = listOf(subPath, extra.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
    return shareName to combined
  }
}

/** Owns one SMB connection/session; closing it releases the connection, session and underlying client. */
class SmbConnectionHandle(
  private val client: SMBClient,
  private val connection: Connection,
  private val session: Session
) : AutoCloseable {

  fun openShare(shareName: String): DiskShare = session.connectShare(shareName) as DiskShare

  override fun close() {
    runCatching { session.close() }
    runCatching { connection.close() }
    runCatching { client.close() }
  }

  companion object {
    fun open(host: String, port: Int, username: String?, password: String?): SmbConnectionHandle {
      val client = SMBClient()
      val connection = client.connect(host, port)
      val authContext = if (!username.isNullOrEmpty()) {
        AuthenticationContext(username, (password ?: "").toCharArray(), null)
      } else {
        AuthenticationContext.anonymous()
      }
      val session = connection.authenticate(authContext)
      return SmbConnectionHandle(client, connection, session)
    }
  }
}
