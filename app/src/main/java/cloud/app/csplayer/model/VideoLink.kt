package cloud.app.csplayer.model

/**
 * Simple data class representing a video link without dependencies on ExtractorLink/ExtractorUri
 */
data class VideoLink(
    val url: String,
    val name: String,
    val headers: Map<String, String> = emptyMap(),
    val position: Long = 0L
)

