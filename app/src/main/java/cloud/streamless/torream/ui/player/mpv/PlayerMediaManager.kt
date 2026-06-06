package cloud.streamless.torream.ui.player.mpv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import cloud.streamless.torream.media.dao.MediaPlaybackDao
import cloud.streamless.torream.media.entities.MediaPlaybackEntity
import cloud.streamless.torream.media.repository.MediaPlaybackRepository
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.VideoLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages media loading and playback state for the MPV player
 */
class PlayerMediaManager(
  private val context: Context,
  private val mediaPlaybackRepository: MediaPlaybackRepository,
  private val onCommandQueued: (Array<String>) -> Unit
) {

  private var player: MPVView? = null
  private val onloadCommands = mutableListOf<Array<String>>()
  private var playbackHasStarted = false
  private val coroutineScope = CoroutineScope(Dispatchers.Main)
  private var currentMediaUri: String? = null

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
    link: VideoLink,
    startPosition: Long = 0,
    title: String? = null
  ) {
    val url = link.url
    val headers = link.headers
    currentMediaUri = url

    // Use provided startPosition directly (should be pre-loaded by caller)
    val finalStartPosition = if (startPosition > 0) startPosition else (link.position)

    if (finalStartPosition > 0) {
      Timber.tag(TAG).d("Starting playback at position: ${finalStartPosition}ms")
    }
    configureMpvForCleanSeek()
    pushOption("force-media-title", title ?: link.name)
    pushOption("start", "${finalStartPosition / 1000}")

    val uri = url.toUri()
    player?.setOriginalMediaPath(url)
    val resolvedPath = resolveUri(uri)
    player?.playFile(resolvedPath, headers)
  }

  private fun configureMpvForCleanSeek() {
    // Seek chính xác và tránh render frame lỗi khi seek
    pushOption("hr-seek", "yes")
    pushOption("hr-seek-framedrop", "no")

    // Hữu ích với HLS/DASH
    pushOption("cache", "yes")
    pushOption("demuxer-seekable-cache", "yes")

    // Làm mượt đồng bộ khung hình (tùy thiết bị)
    pushOption("video-sync", "display-resample")

    // Scaler sắc nét hơn khi có scale (tùy GPU/thiết bị)
    pushOption("scale", "spline36")
    pushOption("cscale", "spline36")
    pushOption("dscale", "spline36")
  }

  fun loadPlaylist(
    links: List<VideoLink>,
    startPosition: Long = 0,
    startIndex: Int = 0
  ) {
    if (links.isEmpty()) return

    val startingLink = links.getOrNull(startIndex)
    if (startingLink != null) {
      currentMediaUri = startingLink.url
    }

    // Use provided startPosition or link's saved position
    val finalStartPosition = if (startPosition > 0) {
      startPosition
    } else {
      startingLink?.position ?: 0L
    }

    if (finalStartPosition > 0) {
      Timber.tag(TAG).d("Starting playlist at position: ${finalStartPosition}ms")
    }

    pushOption("start", "${finalStartPosition / 1000}")

    val urls = links.map { resolveUri(it.url.toUri()) }
    val headers = links.firstOrNull()?.headers

    player?.playPlayList(urls, headers, startIndex)
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
    currentMediaUri = null
  }

  /**
   * Save current playback state
   * Call this periodically or when pausing/stopping
   */
  fun savePlaybackState(
    position: Long,
    speed: Float = 1.0f,
    aspectRatio: String? = null,
    audioTrackIndex: Int = -1,
    textTrackIndex: Int = -1,
    zoomType: String = "fit",
    subtitles: List<SubtitleData>? = null,
    isFinished: Boolean = false
  ) {
    val uri = currentMediaUri ?: return

    coroutineScope.launch(Dispatchers.IO) {
      try {
        val playbackEntity = MediaPlaybackEntity(
          mediaUri = uri,
          position = position,
          speed = speed,
          aspectRatio = aspectRatio,
          audioTrackIndex = audioTrackIndex,
          textTrackIndex = textTrackIndex,
          zoomType = zoomType,
          subtitles = subtitles,
          lastPlayedAt = System.currentTimeMillis(),
          isFinished = isFinished
        )
        mediaPlaybackRepository.savePlayback(playbackEntity)
        Timber.tag(TAG).v("Saved playback state for $uri at position $position")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to save playback state")
      }
    }
  }

  /**
   * Quick update position only (for frequent updates during playback)
   */
  fun updatePosition(position: Long) {
    val uri = currentMediaUri ?: return

    coroutineScope.launch(Dispatchers.IO) {
      try {
        mediaPlaybackRepository.updatePosition(uri, position)
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to update position")
      }
    }
  }

  /**
   * Mark current media as finished
   */
  fun markFinished() {
    val uri = currentMediaUri ?: return

    coroutineScope.launch(Dispatchers.IO) {
      try {
        mediaPlaybackRepository.markAsFinished(uri, true)
        Timber.tag(TAG).d("Marked $uri as finished")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to mark as finished")
      }
    }
  }

  /**
   * Reset finished status (e.g., when user manually restarts)
   */
  fun resetFinished() {
    val uri = currentMediaUri ?: return

    coroutineScope.launch(Dispatchers.IO) {
      try {
        mediaPlaybackRepository.markAsFinished(uri, false)
        Timber.tag(TAG).d("Reset finished status for $uri")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to reset finished status")
      }
    }
  }

  /**
   * Clear playback state for current media
   */
  fun clearPlaybackState() {
    val uri = currentMediaUri ?: return

    coroutineScope.launch(Dispatchers.IO) {
      try {
        // Reset to default state by inserting a fresh entity
        val defaultEntity = MediaPlaybackEntity(
          mediaUri = uri,
          position = 0L,
          speed = 1.0f,
          aspectRatio = null,
          audioTrackIndex = -1,
          textTrackIndex = -1,
          zoomType = "fit",
          subtitles = null,
          lastPlayedAt = System.currentTimeMillis(),
          isFinished = false
        )
        mediaPlaybackRepository.savePlayback(defaultEntity)
        Timber.tag(TAG).d("Cleared playback state for $uri")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to clear playback state")
      }
    }
  }

  /**
   * Update current media URI when playlist changes track
   */
  fun updateCurrentMediaUri(uri: String) {
    currentMediaUri = uri
    Timber.tag(TAG).v("Updated current media URI: $uri")
  }

  /**
   * Get current media URI
   */
  fun getCurrentMediaUri(): String? = currentMediaUri

  private fun resolveUri(data: Uri): String {
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
    return filepath ?: data.toString()
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

