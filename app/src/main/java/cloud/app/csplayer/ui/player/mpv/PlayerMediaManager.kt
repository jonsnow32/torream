package cloud.app.csplayer.ui.player.mpv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.SubtitleData
import timber.log.Timber

/**
 * Manages media loading and playback state for the MPV player
 */
@UnstableApi
class PlayerMediaManager(
    private val context: Context,
    private val onCommandQueued: (Array<String>) -> Unit
) {
    private var player: MPVView? = null
    private val onloadCommands = mutableListOf<Array<String>>()
    private var playbackHasStarted = false

    companion object {
        private const val TAG = "PlayerMediaManager"
    }

    fun setPlayer(mpvPlayer: MPVView?) {
        this.player = mpvPlayer
    }

    fun queueCommand(command: Array<String>) {
        if (playbackHasStarted) {
            MPVLib.command(command)
        } else {
            onloadCommands.add(command)
            onCommandQueued(command)
        }
    }

    fun pushOption(key: String, value: String) {
        queueCommand(arrayOf("set", "file-local-options/$key", value))
    }

    fun loadFile(
        link: Pair<ExtractorLink?, ExtractorUri?>,
        startPosition: Long = 0,
        title: String? = null
    ) {
        val url = link.first?.url ?: return
        val headers = link.first?.headers

        pushOption("force-media-title", title ?: link.first?.name ?: url)
        pushOption("start", "${startPosition / 1000}")

        val uri = url.toUri()
        val resolvedPath = resolveUri(uri)

        if (resolvedPath != null) {
            player?.playFile(resolvedPath, headers)
        } else {
            Timber.tag(TAG).e("Failed to resolve URI: $uri")
        }
    }

    fun loadPlaylist(
        links: Set<Pair<ExtractorLink?, ExtractorUri?>>,
        startPosition: Long = 0
    ) {
        if (links.isEmpty()) return

        val urls = links.mapNotNull { it.first?.url }
        val headers = links.firstOrNull()?.first?.headers

        pushOption("start", "${startPosition / 1000}")
        player?.playPlayList(urls, headers)
    }

    fun addSubtitle(subtitle: SubtitleData, select: Boolean = false) {
        val url = resolveUri(subtitle.url.toUri()) ?: return
        val flag = if (select) "select" else "auto"

        Timber.tag(TAG).v("Adding subtitle: $url (flag: $flag)")
        queueCommand(arrayOf("sub-add", url, flag))
    }

    fun onPlaybackStarted() {
        // Execute all queued commands when playback starts
        for (cmd in onloadCommands) {
            MPVLib.command(cmd)
        }
        onloadCommands.clear()
        playbackHasStarted = true
    }

    fun reset() {
        onloadCommands.clear()
        playbackHasStarted = false
    }

    private fun resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(data)
            "data" -> "data://${data.schemeSpecificPart}"
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh",
            "tcp", "udp", "lavf" -> data.toString()
            else -> data.path
        }

        if (filepath == null) {
            Timber.tag(TAG).e("Unknown scheme: ${data.scheme}")
        }
        return filepath
    }

    private fun openContentFd(uri: Uri): String? {
        val resolver = context.contentResolver
        Timber.tag(TAG).v("Resolving content URI: $uri")

        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc?.detachFd() ?: return null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open content fd")
            return null
        }

        // Try to get the real file path
        val path = MPVUtils.findRealPath(fd)
        if (path != null) {
            Timber.tag(TAG).v("Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close()
            return path
        }

        // Pass the fd to mpv
        return "fd://$fd"
    }
}

