package cloud.streamless.torream.ai.transcribe

import android.content.Context
import cloud.streamless.torream.ai.AiManager
import cloud.streamless.torream.ai.providers.AiProvider
import cloud.streamless.torream.ai.translate.SrtVttParser
import cloud.streamless.torream.ai.translate.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Speech-to-subtitle: decodes the media's audio into chunks ([AudioChunkExtractor]), sends each to
 * a Whisper endpoint asking for SRT, and stitches the replies back onto the full timeline.
 *
 * Progress is reported in seconds of audio processed rather than chunk counts, since the total
 * chunk count is only known once decoding finishes.
 */
class AudioTranscriber(
    private val context: Context,
    private val aiManager: AiManager
) {

    suspend fun transcribe(
        source: String,
        headers: Map<String, String>,
        provider: AiProvider.ProviderType,
        language: String?,
        durationSeconds: Double,
        onProgress: (doneSeconds: Double, totalSeconds: Double) -> Unit
    ): Result<List<SubtitleCue>> = runCatching {
        val workDir = File(context.cacheDir, "stt").apply { mkdirs() }
        val cues = mutableListOf<SubtitleCue>()
        try {
            AudioChunkExtractor.extract(context, source, headers, workDir) { chunk, offset ->
                val srt = aiManager.transcribe(provider, chunk, language).getOrThrow()
                val parsed = SrtVttParser.parse(srt)
                Timber.d("Transcribed chunk at ${offset}s -> ${parsed.size} cues")
                cues += SrtVttParser.shift(parsed, offset)
                onProgress(offset + chunkDurationOf(chunk), durationSeconds)
            }
        } finally {
            withContext(Dispatchers.IO) { workDir.listFiles()?.forEach { it.delete() } }
        }

        if (cues.isEmpty()) error("No speech recognised in this audio track")
        cues.mapIndexed { i, cue -> cue.copy(index = i + 1) }
    }

    /** WAV payload bytes / (16 kHz * 2 bytes) — exact, since the extractor writes 16-bit mono. */
    private fun chunkDurationOf(chunk: File): Double =
        (chunk.length() - WAV_HEADER_BYTES).coerceAtLeast(0) /
            (AudioChunkExtractor.TARGET_SAMPLE_RATE * 2.0)

    private companion object {
        const val WAV_HEADER_BYTES = 44L
    }
}
