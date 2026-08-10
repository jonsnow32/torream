package cloud.streamless.torream.media.repository

import android.util.Base64
import cloud.streamless.torream.media.dao.NetworkShareDao
import cloud.streamless.torream.media.entities.NetworkShareEntity
import cloud.streamless.torream.model.NetworkShare
import cloud.streamless.torream.model.NetworkShareProtocol
import cloud.streamless.torream.model.VideoLink
import cloud.streamless.torream.utils.CredentialCipher
import cloud.streamless.torream.utils.SmbStreamServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import javax.inject.Inject

class NetworkShareRepositoryImpl @Inject constructor(
  private val dao: NetworkShareDao,
  private val smbStreamServer: SmbStreamServer
) : NetworkShareRepository {

  override fun observeShares(): Flow<List<NetworkShare>> =
    dao.observeAll().map { list -> list.map { it.toDomain() } }

  override suspend fun getShare(id: Long): NetworkShare? =
    dao.getById(id)?.toDomain()

  override suspend fun saveShare(share: NetworkShare): Long {
    val entity = share.toEntity()
    return if (share.id == 0L) {
      dao.insert(entity)
    } else {
      dao.update(entity)
      share.id
    }
  }

  override suspend fun deleteShare(id: Long) {
    dao.deleteById(id)
  }

  override fun buildVideoLink(share: NetworkShare, relativePath: String, fileName: String): VideoLink {
    val encodedPath = relativePath.trim('/').split("/").joinToString("/") {
      URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    return when (share.protocol) {
      NetworkShareProtocol.FTP -> {
        val userInfo = if (!share.username.isNullOrEmpty()) {
          val user = URLEncoder.encode(share.username, "UTF-8")
          val pass = URLEncoder.encode(share.password.orEmpty(), "UTF-8")
          "$user:$pass@"
        } else ""
        VideoLink(
          url = "ftp://$userInfo${share.host}:${share.port}/$encodedPath",
          name = fileName,
          subtitles = emptyList()
        )
      }

      NetworkShareProtocol.WEBDAV -> {
        val scheme = if (share.useTls) "https" else "http"
        val headers = if (!share.username.isNullOrEmpty()) {
          val token = Base64.encodeToString(
            "${share.username}:${share.password.orEmpty()}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
          )
          mapOf("Authorization" to "Basic $token")
        } else emptyMap()
        VideoLink(
          url = "$scheme://${share.host}:${share.port}/$encodedPath",
          name = fileName,
          headers = headers,
          subtitles = emptyList()
        )
      }

      NetworkShareProtocol.SMB -> {
        val url = smbStreamServer.getStreamUrl(share.id, relativePath)
        VideoLink(url = url, name = fileName, subtitles = emptyList())
      }
    }
  }

  private fun NetworkShareEntity.toDomain(): NetworkShare {
    val cipherText = encryptedPassword
    val iv = passwordIv
    val decryptedPassword = if (cipherText != null && iv != null) {
      runCatching { CredentialCipher.decrypt(cipherText, iv) }.getOrNull()
    } else null
    return NetworkShare(
      id = id,
      protocol = NetworkShareProtocol.valueOf(protocol),
      displayName = displayName,
      host = host,
      port = port,
      basePath = basePath,
      username = username,
      password = decryptedPassword,
      useTls = useTls
    )
  }

  private fun NetworkShare.toEntity(): NetworkShareEntity {
    val (cipherText, iv) = password?.takeIf { it.isNotEmpty() }
      ?.let { CredentialCipher.encrypt(it) } ?: (null to null)
    return NetworkShareEntity(
      id = id,
      protocol = protocol.name,
      displayName = displayName,
      host = host,
      port = port,
      basePath = basePath,
      username = username,
      encryptedPassword = cipherText,
      passwordIv = iv,
      useTls = useTls
    )
  }
}
