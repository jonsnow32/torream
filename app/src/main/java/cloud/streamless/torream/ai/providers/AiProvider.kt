package cloud.streamless.torream.ai.providers

import java.io.File

/**
 * Interface chung cho tất cả AI providers (subtitle translation, batch rename, speech-to-subtitle).
 */
interface AiProvider {

    enum class ProviderType {
        OPENAI, ANTHROPIC, GROQ, OPENROUTER
    }

    val providerType: ProviderType

    /** True if an API key is configured for this provider. */
    fun isConfigured(): Boolean

    /** True if this provider exposes a Whisper-style `audio/transcriptions` endpoint. */
    val supportsTranscription: Boolean get() = false

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

    /**
     * Transcribes a WAV file and returns the raw SRT the provider produced. Timestamps are relative
     * to the start of [audio], so callers transcribing a chunk must shift them themselves.
     *
     * @param language ISO 639-1 hint, or null to let the model auto-detect.
     */
    suspend fun transcribe(audio: File, model: String, language: String?): Result<String> =
        Result.failure(UnsupportedOperationException("$providerType does not support transcription"))
}
