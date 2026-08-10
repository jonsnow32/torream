package cloud.streamless.torream.ai.rename

/**
 * Suggests clean file names for a batch of media file names via a single LLM call — one batched
 * call (not N calls) lets the model use cross-file context (e.g. detecting a shared show name).
 * A count mismatch between input and output aborts with an error (no partial apply).
 */
object BatchRenamer {

    const val MAX_FILES = 200
    private val numberedLineRegex = Regex("""^\s*(\d+)[.).]\s*(.*)$""")

    private const val SYSTEM_PROMPT =
        "You are a media file renaming assistant. Given a numbered list of messy movie/TV " +
            "episode filenames, infer the show or movie name, season/episode when present, and " +
            "produce a clean, consistent filename for each, PRESERVING the original file " +
            "extension. Reply with ONLY the numbered list of new filenames, one per line, same " +
            "count and order as the input, no extra commentary."

    suspend fun suggestNames(
        fileNames: List<String>,
        chatCompletion: suspend (systemPrompt: String, userPrompt: String) -> Result<String>
    ): Result<List<Pair<String, String>>> {
        if (fileNames.isEmpty()) return Result.success(emptyList())
        if (fileNames.size > MAX_FILES) {
            return Result.failure(
                IllegalArgumentException("Too many files (${fileNames.size}), max $MAX_FILES per batch")
            )
        }

        val userPrompt = fileNames.mapIndexed { i, name -> "${i + 1}. $name" }.joinToString("\n")
        val response = chatCompletion(SYSTEM_PROMPT, userPrompt).getOrElse { return Result.failure(it) }
        val newNames = parseNumberedLines(response)

        if (newNames.size != fileNames.size) {
            return Result.failure(
                IllegalStateException(
                    "Rename suggestion mismatch: expected ${fileNames.size} names, got ${newNames.size}"
                )
            )
        }
        return Result.success(fileNames.zip(newNames))
    }

    private fun parseNumberedLines(response: String): List<String> =
        response.lines()
            .mapNotNull { numberedLineRegex.find(it) }
            .map { it.groupValues[2].trim() }
            .filter { it.isNotEmpty() }
}
