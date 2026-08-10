package cloud.streamless.torream.ai.providers

import cloud.streamless.torream.ai.AiKeyStore
import cloud.streamless.torream.app
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.Serializable

/**
 * Shared implementation for OpenAI-compatible `chat/completions` APIs — covers OpenAI itself,
 * Groq and OpenRouter, which all use the identical request/response shape.
 */
open class OpenAiCompatibleProvider(
    final override val providerType: AiProvider.ProviderType,
    private val baseUrl: String,
    private val keyStore: AiKeyStore
) : AiProvider {

    private val apiKey get() = keyStore.getKey(providerType)

    override fun isConfigured() = !apiKey.isNullOrBlank()

    @Serializable
    private data class Msg(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double
    )

    @Serializable
    private data class Choice(val message: Msg = Msg("", ""))

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    override suspend fun chatCompletion(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        temperature: Double
    ): Result<String> = runCatching {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: error("$providerType API key not set")
        val response = app.post(
            "$baseUrl/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json"
            ),
            json = ChatRequest(
                model = model,
                messages = listOf(Msg("system", systemPrompt), Msg("user", userPrompt)),
                temperature = temperature
            )
        )
        parseJson<ChatResponse>(response.text).choices.firstOrNull()?.message?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("Empty response from $providerType")
    }
}

class OpenAiProvider(keyStore: AiKeyStore) :
    OpenAiCompatibleProvider(AiProvider.ProviderType.OPENAI, "https://api.openai.com/v1", keyStore)

class GroqProvider(keyStore: AiKeyStore) :
    OpenAiCompatibleProvider(AiProvider.ProviderType.GROQ, "https://api.groq.com/openai/v1", keyStore)

class OpenRouterProvider(keyStore: AiKeyStore) :
    OpenAiCompatibleProvider(AiProvider.ProviderType.OPENROUTER, "https://openrouter.ai/api/v1", keyStore)
