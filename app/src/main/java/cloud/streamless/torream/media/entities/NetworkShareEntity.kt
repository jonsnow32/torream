package cloud.streamless.torream.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_share")
data class NetworkShareEntity(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
  @ColumnInfo(name = "protocol") val protocol: String, // "SMB" | "FTP" | "WEBDAV"
  @ColumnInfo(name = "display_name") val displayName: String,
  @ColumnInfo(name = "host") val host: String,
  @ColumnInfo(name = "port") val port: Int,
  @ColumnInfo(name = "base_path") val basePath: String,
  @ColumnInfo(name = "username") val username: String? = null,
  @ColumnInfo(name = "encrypted_password") val encryptedPassword: String? = null,
  @ColumnInfo(name = "password_iv") val passwordIv: String? = null,
  @ColumnInfo(name = "use_tls") val useTls: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
