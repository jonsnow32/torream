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

    /** Shifts every cue's timing by [seconds] — used to place a transcribed chunk on the full timeline. */
    fun shift(cues: List<SubtitleCue>, seconds: Double): List<SubtitleCue> =
        if (seconds == 0.0) cues else cues.map {
            it.copy(
                startTime = formatCanonical(parseCanonical(it.startTime) + seconds),
                endTime = formatCanonical(parseCanonical(it.endTime) + seconds)
            )
        }

    private fun parseCanonical(time: String): Double {
        val (h, m, rest) = time.split(':')
        return h.toInt() * 3600.0 + m.toInt() * 60.0 + rest.toDouble()
    }

    private fun formatCanonical(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0)
        val millis = (total * 1000).toLong()
        return "%02d:%02d:%02d.%03d".format(
            millis / 3_600_000, (millis / 60_000) % 60, (millis / 1000) % 60, millis % 1000
        )
    }

    private fun toFormatTime(canonical: String, isVtt: Boolean): String =
        if (isVtt) canonical else canonical.replace('.', ',')
}
