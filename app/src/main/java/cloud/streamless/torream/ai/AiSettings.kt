package cloud.streamless.torream.ai

import android.content.Context
import cloud.streamless.torream.ai.providers.AiProvider
import cloud.streamless.torream.utils.DataStore.getKey
import cloud.streamless.torream.utils.DataStore.setKey

/** Non-secret AI settings (default provider, per-provider model id) — stored via the existing DataStore pattern. */
object AiSettings {

    private const val KEY_DEFAULT_PROVIDER = "ai_default_provider"
    private fun modelKey(type: AiProvider.ProviderType) = "ai_model_${type.name}"

    fun getDefaultProvider(context: Context): AiProvider.ProviderType? =
        context.getKey<String>(KEY_DEFAULT_PROVIDER)
            ?.let { runCatching { AiProvider.ProviderType.valueOf(it) }.getOrNull() }

    fun setDefaultProvider(context: Context, type: AiProvider.ProviderType) {
        context.setKey(KEY_DEFAULT_PROVIDER, type.name)
    }

    fun getModel(context: Context, type: AiProvider.ProviderType): String =
        context.getKey<String>(modelKey(type))?.takeIf { it.isNotBlank() } ?: defaultModelFor(type)

    fun setModel(context: Context, type: AiProvider.ProviderType, model: String) {
        context.setKey(modelKey(type), model)
    }

    private fun defaultModelFor(type: AiProvider.ProviderType) = when (type) {
        AiProvider.ProviderType.OPENAI -> "gpt-4o-mini"
        AiProvider.ProviderType.ANTHROPIC -> "claude-3-5-haiku-20241022"
        AiProvider.ProviderType.GROQ -> "llama-3.3-70b-versatile"
        AiProvider.ProviderType.OPENROUTER -> "" // free-text field, no sane default — multi-model gateway
    }
}
