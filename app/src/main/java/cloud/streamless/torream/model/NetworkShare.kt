package cloud.streamless.torream.model

enum class NetworkShareProtocol { SMB, FTP, WEBDAV }

data class NetworkShare(
  val id: Long = 0,
  val protocol: NetworkShareProtocol,
  val displayName: String,
  val host: String,
  val port: Int,
  val basePath: String,
  val username: String? = null,
  /** Plaintext, decrypted on read from storage - never held longer than needed to connect/build a URL. */
  val password: String? = null,
  val useTls: Boolean = false
)
