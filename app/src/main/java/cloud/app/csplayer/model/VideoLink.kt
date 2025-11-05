package cloud.app.csplayer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Simple data class representing a video link without dependencies on ExtractorLink/ExtractorUri
 */
@Parcelize
@Serializable
data class VideoLink(
    val url: String,
    val name: String,
    val headers: Map<String, String> = emptyMap(),
    val position: Long = 0L,
    val ratio: Float? = null
) : Parcelable

