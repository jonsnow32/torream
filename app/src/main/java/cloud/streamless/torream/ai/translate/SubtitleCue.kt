package cloud.streamless.torream.ai.translate

/** A single subtitle cue. [startTime]/[endTime] are canonical "HH:MM:SS.mmm" (dot separator). */
data class SubtitleCue(
    val index: Int,
    val startTime: String,
    val endTime: String,
    val text: String
)
