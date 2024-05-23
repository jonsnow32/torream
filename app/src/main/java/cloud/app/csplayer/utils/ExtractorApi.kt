package cloud.app.csplayer.utils

import android.net.Uri
import cloud.app.csplayer.ui.subtitles.SubtitleFile
import cloud.app.csplayer.utils.Utils.normalSafeApiCall
import com.fasterxml.jackson.annotation.JsonIgnore

import kotlinx.coroutines.delay
import java.net.URL
import java.util.UUID

/**
 * For use in the ConcatenatingMediaSource.
 * If features are missing (headers), please report and we can add it.
 * @param durationUs use Long.toUs() for easier input
 * */
data class PlayListItem(
    val url: String,
    val durationUs: Long,
)

/**
 * Converts Seconds to MicroSeconds, multiplication by 1_000_000
 * */
fun Long.toUs(): Long {
    return this * 1_000_000
}

/**
 * If your site has an unorthodox m3u8-like system where there are multiple smaller videos concatenated
 * use this.
 * */
data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override val referer: String,
    override val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override val extractorData: String? = null,
    override val type: ExtractorLinkType,
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    headers = headers,
    extractorData = extractorData,
    type = type
) {
    constructor(
        source: String,
        name: String,
        playlist: List<PlayListItem>,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        playlist = playlist,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
    )
}

/** Metadata about the file type used for downloads and exoplayer hint,
 * if you respond with the wrong one the file will fail to download or be played */
enum class ExtractorLinkType {
    /** Single stream of bytes no matter the actual file type */
    VIDEO,
    /** Split into several .ts files, has support for encrypted m3u8s */
    M3U8,
    /** Like m3u8 but uses xml, currently no download support */
    DASH,
    /** No support at the moment */
    TORRENT,
    /** No support at the moment */
    MAGNET;

    // See https://www.iana.org/assignments/media-types/media-types.xhtml
    fun getMimeType(): String {
        return when (this) {
            VIDEO -> "video/mp4"
            M3U8 -> "application/x-mpegURL"
            DASH -> "application/dash+xml"
            TORRENT -> "application/x-bittorrent"
            MAGNET -> "application/x-bittorrent"
        }
    }
}

private fun inferTypeFromUrl(url: String): ExtractorLinkType {
    val path = normalSafeApiCall { URL(url).path }
    return when {
        path?.endsWith(".m3u8") == true -> ExtractorLinkType.M3U8
        path?.endsWith(".mpd") == true -> ExtractorLinkType.DASH
        path?.endsWith(".torrent") == true -> ExtractorLinkType.TORRENT
        url.startsWith("magnet:") -> ExtractorLinkType.MAGNET
        else -> ExtractorLinkType.VIDEO
    }
}
val INFER_TYPE : ExtractorLinkType? = null

/**
 * UUID for the ClearKey DRM scheme.
 *
 *
 * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
 */
val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

/**
 * UUID for the Widevine DRM scheme.
 *
 *
 * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
 */
val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * UUID for the PlayReady DRM scheme.
 *
 *
 * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
 * provide PlayReady support.
 */
val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

open class DrmExtractorLink private constructor(
    override val source: String,
    override val name: String,
    override val url: String,
    override val referer: String,
    override val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override val extractorData: String? = null,
    override val type: ExtractorLinkType,
    open val kid : String,
    open val key : String,
    open val uuid : UUID,
    open val kty : String,

    open val keyRequestParameters : HashMap<String, String>
) : ExtractorLink(
    source, name, url, referer, quality, type, headers, extractorData
) {
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        kid : String,
        key : String,
        uuid : UUID = CLEARKEY_UUID,
        kty : String = "oct",
        keyRequestParameters : HashMap<String, String> = hashMapOf(),
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        keyRequestParameters = keyRequestParameters,
        kty = kty,
    )
}
interface IDownloadableMinimum {
  val url: String
  val referer: String
  val headers: Map<String, String>
}

open class ExtractorLink constructor(
    open val source: String,
    open val name: String,
    override val url: String,
    override val referer: String,
    open val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    open val extractorData: String? = null,
    open val type: ExtractorLinkType,
) : IDownloadableMinimum {
    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    val isDash: Boolean get() = type == ExtractorLinkType.DASH

    // Cached video size
    private var videoSize: Long? = null

    /**
     * Get video size in bytes with one head request. Only available for ExtractorLinkType.Video
     * @param timeoutSeconds timeout of the head request.
     */
    @JsonIgnore
    fun getAllHeaders() : Map<String, String> {
        if (referer.isBlank()) {
            return headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            return headers + mapOf("referer" to referer)
        }
        return headers
    }

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url)
    )

    /**
     * Old constructor without isDash, allows for backwards compatibility with extensions.
     * Should be removed after all extensions have updated their cloudstream.jar
     **/
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null
    ) : this(source, name, url, referer, quality, isM3u8, headers, extractorData, false)

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        isDash: Boolean,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = if (isDash) ExtractorLinkType.DASH else if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
    )

    override fun toString(): String {
        return "ExtractorLink(name=$name, url=$url, referer=$referer, type=$type)"
    }
}

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null
)

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    override val referer: String,
    override val headers: Map<String, String> = mapOf()
) : IDownloadableMinimum

/**
 * Removes https:// and www.
 * To match urls regardless of schema, perhaps Uri() can be used?
 */
val schemaStripRegex = Regex("""^(https:|)//(www\.|)""")

enum class Qualities(var value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0), // 144p
    P240(240, 2), // 240p
    P360(360, 3), // 360p
    P480(480, 4), // 480p
    P720(720, 5), // 720p
    P1080(1080, 6), // 1080p
    P1440(1440, 7), // 1440p
    P2160(2160, 8); // 4k or 2160p

    companion object {
        fun getStringByInt(qual: Int?): String {
            return when (qual) {
                0 -> "Auto"
                Unknown.value -> ""
                P2160.value -> "4K"
                null -> ""
                else -> "${qual}p"
            }
        }

        fun getStringByIntFull(quality: Int): String {
            return when (quality) {
                0 -> "Auto"
                Unknown.value -> "Unknown"
                P2160.value -> "4K"
                else -> "${quality}p"
            }
        }
    }
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null)
        return Qualities.Unknown.value

    val match = qualityName.lowercase().replace("p", "").trim()
    return when (match) {
        "4k" -> Qualities.P2160
        else -> null
    }?.value ?: match.toIntOrNull() ?: Qualities.Unknown.value
}

