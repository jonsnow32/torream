package cloud.streamless.torream.ai.providers

import cloud.streamless.torream.ai.AiKeyStore
import cloud.streamless.torream.app
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared implementation for OpenAI-compatible `chat/completions` APIs — covers OpenAI itself,
 * Groq and OpenRouter, which all use the identical request/response shape.
 */
open class OpenAiCompatibleProvider(
    final override val providerType: AiProvider.ProviderType,
    private val baseUrl: String,
    private val keyStore: AiKeyStore,
    final override val supportsTranscription: Boolean = false
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

    /**
     * Multipart upload built on raw OkHttp rather than NiceHttp: a chunk of speech takes far longer
     * than the shared client's default read timeout, so the call needs its own.
     */
    override suspend fun transcribe(
        audio: File,
        model: String,
        language: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(supportsTranscription) { "$providerType does not support transcription" }
            val key = apiKey?.takeIf { it.isNotBlank() } ?: error("$providerType API key not set")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", audio.name, audio.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "srt")
                .apply { if (!language.isNullOrBlank()) addFormDataPart("language", language) }
                .build()
            val request = Request.Builder()
                .url("$baseUrl/audio/transcriptions")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            val client = app.baseClient.newBuilder()
                .readTimeout(TRANSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TRANSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("$providerType transcription failed (${response.code}): ${text.take(200)}")
                }
                text
            }
        }
    }

    private companion object {
        const val TRANSCRIBE_TIMEOUT_SECONDS = 300L
    }
}

class OpenAiProvider(keyStore: AiKeyStore) : OpenAiCompatibleProvider(
    AiProvider.ProviderType.OPENAI, "https://api.openai.com/v1", keyStore, supportsTranscription = true
)

class GroqProvider(keyStore: AiKeyStore) : OpenAiCompatibleProvider(
    AiProvider.ProviderType.GROQ, "https://api.groq.com/openai/v1", keyStore, supportsTranscription = true
)

class OpenRouterProvider(keyStore: AiKeyStore) :
    OpenAiCompatibleProvider(AiProvider.ProviderType.OPENROUTER, "https://openrouter.ai/api/v1", keyStore)
