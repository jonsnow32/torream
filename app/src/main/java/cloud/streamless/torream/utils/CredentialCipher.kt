package cloud.streamless.torream.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts/decrypts short secrets (e.g. saved network-share passwords) with a device-bound AndroidKeyStore AES key. */
object CredentialCipher {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "torream_network_share_credentials"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH_BITS = 128

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  /** Returns (cipherTextBase64, ivBase64) - both must be stored to decrypt later. */
  fun encrypt(plainText: String): Pair<String, String> {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val cipherTextB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    return cipherTextB64 to ivB64
  }

  fun decrypt(cipherTextBase64: String, ivBase64: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivBase64, Base64.NO_WRAP))
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
    val plainBytes = cipher.doFinal(Base64.decode(cipherTextBase64, Base64.NO_WRAP))
    return String(plainBytes, Charsets.UTF_8)
  }
}
