package cloud.streamless.torream.ai.providers

/**
 * Interface chung cho tất cả AI chat-completion providers (subtitle translation, batch rename).
 */
interface AiProvider {

    enum class ProviderType {
        OPENAI, ANTHROPIC, GROQ, OPENROUTER
    }

    val providerType: ProviderType

    /** True if an API key is configured for this provider. */
    fun isConfigured(): Boolean

    /**
     * Single-shot chat completion.
     * @param systemPrompt instructions (e.g. "You are a subtitle translator...")
     * @param userPrompt the actual payload (cue batch / filename list)
     * @param model provider-specific model id, e.g. "gpt-4o-mini", "claude-3-5-haiku-20241022"
     */
    suspend fun chatCompletion(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        temperature: Double = 0.3
    ): Result<String>
}
