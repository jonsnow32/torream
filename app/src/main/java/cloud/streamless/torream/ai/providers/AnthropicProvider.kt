package cloud.streamless.torream.ai.providers

import cloud.streamless.torream.ai.AiKeyStore
import cloud.streamless.torream.app
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anthropic Messages API — different request/response shape than the OpenAI-compatible providers. */
class AnthropicProvider(private val keyStore: AiKeyStore) : AiProvider {

    override val providerType = AiProvider.ProviderType.ANTHROPIC
    private val baseUrl = "https://api.anthropic.com/v1"
    private val apiKey get() = keyStore.getKey(providerType)

    override fun isConfigured() = !apiKey.isNullOrBlank()

    @Serializable
    private data class Msg(val role: String, val content: String)

    @Serializable
    private data class MessagesRequest(
        val model: String,
        val system: String,
        val messages: List<Msg>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double
    )

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String = "")

    @Serializable
    private data class MessagesResponse(val content: List<ContentBlock> = emptyList())

    override suspend fun chatCompletion(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        temperature: Double
    ): Result<String> = runCatching {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: error("Anthropic API key not set")
        val response = app.post(
            "$baseUrl/messages",
            headers = mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            ),
            json = MessagesRequest(
                model = model,
                system = systemPrompt,
                messages = listOf(Msg("user", userPrompt)),
                maxTokens = 4096,
                temperature = temperature
            )
        )
        parseJson<MessagesResponse>(response.text).content.firstOrNull { it.type == "text" }?.text
            ?.takeIf { it.isNotBlank() }
            ?: error("Empty response from Anthropic")
    }
}
