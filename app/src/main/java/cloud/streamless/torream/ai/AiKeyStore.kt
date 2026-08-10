package cloud.streamless.torream.ai

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cloud.streamless.torream.ai.providers.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypted storage for user-entered AI provider API keys. */
@Singleton
class AiKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ai_provider_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(provider: AiProvider.ProviderType): String? = prefs.getString(provider.name, null)

    fun setKey(provider: AiProvider.ProviderType, value: String) {
        prefs.edit { putString(provider.name, value) }
    }

    fun clearKey(provider: AiProvider.ProviderType) {
        prefs.edit { remove(provider.name) }
    }
}
