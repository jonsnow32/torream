package cloud.streamless.torream.ai.translate

/** Minimal SRT/VTT cue parser + serializer — no external library needed. */
object SrtVttParser {

    private val timingRegex = Regex(
        """(\d{2}:\d{2}:\d{2})[.,](\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2})[.,](\d{3})"""
    )

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split(Regex("\n{2,}"))

        var index = 0
        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val timingLineIdx = lines.indexOfFirst { timingRegex.containsMatchIn(it) }
            if (timingLineIdx == -1) continue // header (WEBVTT), NOTE/STYLE blocks, etc.

            val match = timingRegex.find(lines[timingLineIdx]) ?: continue
            val startTime = "${match.groupValues[1]}.${match.groupValues[2]}"
            val endTime = "${match.groupValues[3]}.${match.groupValues[4]}"
            val text = lines.drop(timingLineIdx + 1).joinToString("\n")
            if (text.isBlank()) continue

            index++
            cues.add(SubtitleCue(index, startTime, endTime, text))
        }
        return cues
    }

    fun serialize(cues: List<SubtitleCue>, isVtt: Boolean): String = buildString {
        if (isVtt) append("WEBVTT\n\n")
        cues.forEachIndexed { i, cue ->
            if (!isVtt) {
                append(i + 1).append('\n')
            }
            append(toFormatTime(cue.startTime, isVtt))
            append(" --> ")
            append(toFormatTime(cue.endTime, isVtt))
            append('\n')
            append(cue.text)
            append("\n\n")
        }
    }

    private fun toFormatTime(canonical: String, isVtt: Boolean): String =
        if (isVtt) canonical else canonical.replace('.', ',')
}
