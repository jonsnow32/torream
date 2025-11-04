package cloud.app.csplayer.torrent

import android.net.Uri
import java.util.regex.Pattern

/**
 * Utility functions for torrent operations
 */
object TorrentUtils {

    private val MAGNET_PATTERN = Pattern.compile("magnet:\\?xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32}).*")
    private val INFOHASH_PATTERN = Pattern.compile("([a-fA-F0-9]{40})")

    /**
     * Check if a string is a valid magnet link
     */
    fun isValidMagnet(uri: String): Boolean {
        return MAGNET_PATTERN.matcher(uri).matches()
    }

    /**
     * Check if a string is a valid info hash
     */
    fun isValidInfoHash(hash: String): Boolean {
        return INFOHASH_PATTERN.matcher(hash).matches()
    }

    /**
     * Extract info hash from magnet link
     */
    fun extractInfoHash(magnetUri: String): String? {
        val matcher = MAGNET_PATTERN.matcher(magnetUri)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    /**
     * Get the name from a magnet link
     */
    fun getNameFromMagnet(magnetUri: String): String? {
        val uri = Uri.parse(magnetUri)
        return uri.getQueryParameter("dn")
    }

    /**
     * Format bytes to human readable format
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Format speed to human readable format
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "%.2f KB/s".format(bytesPerSecond / 1024.0)
            else -> "%.2f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
        }
    }

    /**
     * Format duration to human readable format
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "%dh %02dm %02ds".format(hours, minutes, secs)
            minutes > 0 -> "%dm %02ds".format(minutes, secs)
            else -> "%ds".format(secs)
        }
    }

    /**
     * Calculate ETA (estimated time of arrival)
     */
    fun calculateEta(remainingBytes: Long, downloadSpeed: Long): Long {
        return if (downloadSpeed > 0) {
            remainingBytes / downloadSpeed
        } else {
            -1 // Unknown ETA
        }
    }

    /**
     * Get file extension from path
     */
    fun getFileExtension(path: String): String {
        val lastDot = path.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < path.length - 1) {
            path.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * Check if file is a video file
     */
    fun isVideoFile(path: String): Boolean {
        val extension = getFileExtension(path)
        return extension in listOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
            "m4v", "3gp", "mpg", "mpeg", "m2ts", "ts"
        )
    }

    /**
     * Check if file is an audio file
     */
    fun isAudioFile(path: String): Boolean {
        val extension = getFileExtension(path)
        return extension in listOf(
            "mp3", "flac", "wav", "aac", "ogg", "m4a",
            "wma", "opus", "ape", "alac"
        )
    }

    /**
     * Create a magnet link from info hash and name
     */
    fun createMagnetLink(infoHash: String, name: String? = null, trackers: List<String> = emptyList()): String {
        val builder = StringBuilder("magnet:?xt=urn:btih:$infoHash")

        if (name != null) {
            builder.append("&dn=").append(Uri.encode(name))
        }

        for (tracker in trackers) {
            builder.append("&tr=").append(Uri.encode(tracker))
        }

        return builder.toString()
    }

    /**
     * Common public torrent trackers
     */
    val defaultTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://public.popcorn-tracker.org:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://exodus.desync.com:6969",
        "udp://open.demonii.com:1337/announce"
    )
}

