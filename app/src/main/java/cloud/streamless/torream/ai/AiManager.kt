package cloud.streamless.torream.ai

import android.content.Context
import cloud.streamless.torream.ai.providers.AiProvider
import cloud.streamless.torream.ai.providers.AnthropicProvider
import cloud.streamless.torream.ai.providers.GroqProvider
import cloud.streamless.torream.ai.providers.OpenAiProvider
import cloud.streamless.torream.ai.providers.OpenRouterProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiManager @Inject constructor(
    private val keyStore: AiKeyStore,
    @ApplicationContext private val context: Context
) {

    private val providers: Map<AiProvider.ProviderType, AiProvider> by lazy {
        listOf(
            OpenAiProvider(keyStore),
            AnthropicProvider(keyStore),
            GroqProvider(keyStore),
            OpenRouterProvider(keyStore)
        ).associateBy { it.providerType }
    }

    fun getProvider(type: AiProvider.ProviderType): AiProvider? = providers[type]

    fun getConfiguredProviders(): List<AiProvider> = providers.values.filter { it.isConfigured() }

    /**
     * Uses the user's saved default provider + its model id. Fails fast if no default is set or
     * unconfigured — deliberately no automatic fallback across providers (unlike the ad waterfall),
     * since translation/rename quality depends on the specific model the user picked.
     */
    suspend fun defaultChatCompletion(systemPrompt: String, userPrompt: String): Result<String> {
        val type = AiSettings.getDefaultProvider(context)
            ?: return Result.failure(IllegalStateException("No default AI provider configured"))
        val provider = providers[type]
            ?: return Result.failure(IllegalStateException("Provider unavailable: $type"))
        if (!provider.isConfigured()) {
            return Result.failure(IllegalStateException("$type API key not set"))
        }
        val model = AiSettings.getModel(context, type)
        return provider.chatCompletion(systemPrompt, userPrompt, model)
    }
}
