package cloud.streamless.torream.ai.translate

/**
 * Translates subtitle cues via an LLM, chunked into numbered-line batches so the model's reply
 * can be mapped back to cues by count. A chunk whose returned line count doesn't match its input
 * count aborts the whole translation (no partial apply, no retry) — a silent count drift would
 * otherwise desync subtitle timing from text.
 */
object SubtitleTranslator {

    const val MAX_CUES = 500
    private const val CHUNK_SIZE = 40
    private val numberedLineRegex = Regex("""^\s*(\d+)[.).]\s*(.*)$""")

    suspend fun translate(
        cues: List<SubtitleCue>,
        targetLanguage: String,
        chatCompletion: suspend (systemPrompt: String, userPrompt: String) -> Result<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<SubtitleCue>> {
        if (cues.isEmpty()) return Result.success(emptyList())
        if (cues.size > MAX_CUES) {
            return Result.failure(
                IllegalArgumentException("Too many cues (${cues.size}), max $MAX_CUES per translation")
            )
        }

        val systemPrompt = "You are a subtitle translator. Translate each numbered line to " +
            "$targetLanguage. Preserve the exact numbering, one translated line per input line. " +
            "Reply with ONLY the numbered translated lines, no extra commentary."

        val translated = mutableListOf<SubtitleCue>()
        for (chunk in cues.chunked(CHUNK_SIZE)) {
            val userPrompt = buildPrompt(chunk)
            val response = chatCompletion(systemPrompt, userPrompt).getOrElse { return Result.failure(it) }
            val lines = parseNumberedLines(response)
            if (lines.size != chunk.size) {
                return Result.failure(
                    IllegalStateException(
                        "Translation mismatch: expected ${chunk.size} lines, got ${lines.size}"
                    )
                )
            }
            chunk.forEachIndexed { i, cue -> translated.add(cue.copy(text = lines[i])) }
            onProgress(translated.size, cues.size)
        }
        return Result.success(translated)
    }

    private fun buildPrompt(chunk: List<SubtitleCue>): String =
        chunk.mapIndexed { i, cue -> "${i + 1}. ${flatten(cue.text)}" }.joinToString("\n")

    // Cue text can span multiple lines; flattened to one line per cue since the LLM reply maps
    // one numbered line back to exactly one cue. Original internal line breaks are not restored.
    private fun flatten(text: String): String = text.replace("\n", " ").trim()

    private fun parseNumberedLines(response: String): List<String> =
        response.lines()
            .mapNotNull { numberedLineRegex.find(it) }
            .map { it.groupValues[2].trim() }
            .filter { it.isNotEmpty() }
}
