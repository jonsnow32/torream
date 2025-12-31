package com.tv.apps.zippy.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.tv.apps.zippy.databinding.DialogTorrentStreamProgressBinding
import com.tv.apps.zippy.utils.AutoClearedValue.Companion.autoCleared

// New/changed imports
import androidx.lifecycle.lifecycleScope
import com.tv.apps.zippy.R
import com.tv.apps.zippy.download.torrent.TorrentDownloadEngine
import com.tv.apps.zippy.download.torrent.TorrentStreamServer
import com.tv.apps.zippy.model.PlaybackData
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.utils.PlaybackDataHelper
import com.tv.apps.zippy.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Progress dialog for torrent streaming
 * Shows download progress, speed, peers, and seeds
 */
@AndroidEntryPoint
class TorrentStreamProgressDialog : DockingDialog() {
  private var binding by autoCleared<DialogTorrentStreamProgressBinding>()

  private var onCancelListener: (() -> Unit)? = null

  // Keep track of the current torrent info hash for cancellation
  private var infoHashToRemove: String? = null

  @Inject
  lateinit var torrentDownloadEngine: TorrentDownloadEngine

  @Inject
  lateinit var torrentStreamServer: TorrentStreamServer

  // NEW: inject repository to find worker-downloaded data
  @Inject
  lateinit var downloadRepository: com.tv.apps.zippy.download.DownloadRepository

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = DialogTorrentStreamProgressBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Make dialog cancelable
    isCancelable = false

    // Cancel button: run any external listener, and also remove torrent if started
    binding.cancelButton.setOnClickListener {
      onCancelListener?.invoke()

      // Try to remove torrent if we have an info hash
      lifecycleScope.launch(Dispatchers.IO) {
        infoHashToRemove?.let { hash ->
          try {
            torrentDownloadEngine.removeTorrent(hash, deleteFiles = true)
            Timber.i("Cancelled streaming torrent: $hash")
          } catch (e: Exception) {
            Timber.e(e, "Error cancelling stream")
          }
        }
      }

      dismiss()
    }

    setDialogTitle("Preparing Stream")
    setDialogMessage("Connecting to peers and downloading initial data...")
    updateProgress(0, 0, 0, 0, null)

    startStreaming(inputUrl)
  }

  /**
   * Update progress information
   */
  fun updateProgress(
    progress: Int,
    speedKBps: Long,
    peers: Int,
    seeds: Int,
    message: String? = null
  ) {
    binding.progressBar.progress = progress
    binding.progressText.text = "$progress%"
    binding.speedText.text = "$speedKBps KB/s"
    binding.peersText.text = "Peers: $peers"
    binding.seedsText.text = "Seeds: $seeds"

    if (message != null) {
      binding.messageText.text = message
      binding.messageText.visibility = View.VISIBLE
    } else {
      binding.messageText.visibility = View.GONE
    }
  }

  /**
   * Set title
   */
  fun setDialogTitle(title: String) {
    binding.titleText.text = title
  }

  /**
   * Set message
   */
  fun setDialogMessage(message: String) {
    binding.messageText.text = message
    binding.messageText.visibility = View.VISIBLE
  }

  /**
   * Set cancel listener
   */
  fun setOnCancelClickListener(listener: () -> Unit) {
    onCancelListener = listener
  }

  /**
   * Start streaming a magnet or torrent URI.
   * The dialog will manage download progress, start playback when ready, and handle errors.
   */
  fun startStreaming(magnetOrTorrentUri: String) {
    val ctx = requireContext()
    val activity = requireActivity()

    // Run on IO dispatcher to prevent blocking UI
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        // Start stream server
        val serverUrl = try {
          torrentStreamServer.start()
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            if (isVisible) {
              dismiss()
              android.widget.Toast.makeText(
                ctx,
                "Failed to start stream server: ${e.message}",
                android.widget.Toast.LENGTH_LONG
              ).show()
            }
          }
          Timber.e(e, "Failed to start stream server")
          return@launch
        }

        Timber.i("Stream server started: $serverUrl")

        // --- CHANGED: try to reuse worker/downloadRepository data first ---
        val existingDir = try {
          findExistingDownloadDirUsingRepo(magnetOrTorrentUri)
        } catch (e: Exception) {
          Timber.w(e, "Repo lookup failed")
          null
        }
        val streamDir = existingDir ?: File(ctx.cacheDir, "torrent_stream_${System.currentTimeMillis()}").also { it.mkdirs() }
        if (existingDir != null) {
          Timber.i("Reusing repository download directory: ${existingDir.absolutePath}")
        } else {
          Timber.i("No repo data found, using temp stream dir: ${streamDir.absolutePath}")
        }
        // --- end changed ---

        // Start download with sequential mode
        val downloadFlow = when {
          magnetOrTorrentUri.startsWith("magnet:", ignoreCase = true) -> {
            torrentDownloadEngine.downloadMagnet(magnetOrTorrentUri, streamDir, sequential = true)
          }
          magnetOrTorrentUri.endsWith(".torrent", ignoreCase = true) -> {
            // Handle content URI or file path
            val torrentFilePath = if (magnetOrTorrentUri.startsWith("content://")) {
              // Copy from content URI to temp file
              val tempFile = File(streamDir, "stream.torrent")
              ctx.contentResolver.openInputStream(magnetOrTorrentUri.toUri())?.use { input ->
                tempFile.outputStream().use { output ->
                  input.copyTo(output)
                }
              }
              tempFile.absolutePath
            } else {
              magnetOrTorrentUri
            }
            torrentDownloadEngine.downloadTorrentFile(torrentFilePath, streamDir, sequential = true)
          }
          else -> {
            withContext(Dispatchers.Main) {
              if (isVisible) {
                dismiss()
                android.widget.Toast.makeText(
                  ctx,
                  "Invalid torrent URI",
                  android.widget.Toast.LENGTH_LONG
                ).show()
              }
            }
            return@launch
          }
        }

        var streamReady = false
        var torrentName = ""

        try {
          // Add timeout: if not ready within 60 seconds, fail
          withTimeoutOrNull(60_000L) {
            downloadFlow.collect { info ->
              // Store info hash for cancellation
              infoHashToRemove = info.infoHash
              torrentName = info.name

              // Update progress
              withContext(Dispatchers.Main) {
                if (isVisible) {
                  val speedKB = info.downloadRate / 1024
                  updateProgress(
                    progress = info.progress,
                    speedKBps = speedKB,
                    peers = info.numPeers,
                    seeds = info.numSeeds,
                    message = "Downloading: ${info.name}"
                  )
                }
              }

              // Check if we have enough data to start streaming (5% or 10MB, whichever is smaller)
              val minBytesNeeded = minOf((info.totalSize * 0.05).toLong(), 10 * 1024 * 1024L)
              if (!streamReady && info.downloadedBytes >= minBytesNeeded) {
                // Generate stream URL using the largest video file
                val url = torrentStreamServer.getStreamUrl(info.infoHash, fileIndex = info.largestVideoFileIndex)
                streamReady = true

                Timber.i("✅ Stream ready: $url")
                Timber.i("Downloaded: ${info.downloadedBytes / (1024 * 1024)} MB (${info.progress}%)")

                // Start playback
                withContext(Dispatchers.Main) {
                  if (isVisible) dismiss()
                  startPlayback(activity, url, torrentName, info.totalSize)
                }

                // Exit collect loop after starting playback
                return@collect
              }

              // Check if download finished before reaching streaming threshold
              if (info.isFinished && !streamReady) {
                // Download finished, generate stream URL using the largest video file
                val url = torrentStreamServer.getStreamUrl(info.infoHash, fileIndex = info.largestVideoFileIndex)
                streamReady = true

                Timber.i("✅ Download complete, starting playback: $url")

                withContext(Dispatchers.Main) {
                  if (isVisible) dismiss()
                  startPlayback(activity, url, torrentName, info.totalSize)
                }

                // Exit collect loop after starting playback
                return@collect
              }

              // Check for errors and log them
              if (info.error != null) {
                Timber.e("Streaming error: ${info.error}")
              }
            }
          } ?: run {
            // Timeout reached
            throw TimeoutException("Stream preparation timeout after 60s - no peers found")
          }
        } catch (e: TimeoutException) {
          Timber.e("Streaming timeout: ${e.message}")
          withContext(Dispatchers.Main) {
            if (isVisible) {
              dismiss()
              android.widget.Toast.makeText(
                ctx,
                "Connection timeout - no peers found",
                android.widget.Toast.LENGTH_LONG
              ).show()
            }
          }
        }

        // If we get here without starting stream, show error
        if (!streamReady) {
          withContext(Dispatchers.Main) {
            if (isVisible) {
              dismiss()
              android.widget.Toast.makeText(
                ctx,
                "Failed to start streaming",
                android.widget.Toast.LENGTH_LONG
              ).show()
            }
          }
        }

      } catch (e: Exception) {
        Timber.e(e, "Streaming exception")
        withContext(Dispatchers.Main) {
          if (isVisible) {
            dismiss()
            android.widget.Toast.makeText(
              ctx,
              "Streaming error: ${e.message}",
              android.widget.Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }
  }

  /**
   * Tries to find an existing download directory from the repository for reuse.
   * This avoids re-downloading files that are already being downloaded by a worker.
   */
  private suspend fun findExistingDownloadDirUsingRepo(magnetOrTorrentUri: String): File? {
    try {
      // 1) If magnet link, extract infoHash and query repository by that task id
      if (magnetOrTorrentUri.startsWith("magnet:", ignoreCase = true)) {
        val uri = magnetOrTorrentUri.toUri()
        val xt = uri.getQueryParameters("xt").firstOrNull()
        val infoHash = xt?.removePrefix("urn:btih:")?.uppercase() ?: return null

        val state = downloadRepository.observeState(infoHash).first()
        if (state != null && state.task.targetPath.isNotBlank()) {
          val dir = File(state.task.targetPath)
          if (dir.exists() && dir.isDirectory) {
            Timber.i("Found existing download dir for magnet: ${dir.absolutePath}")
            return dir
          }
        }
      }

      // 2) If .torrent file, try to match by filename against existing torrent states
      if (magnetOrTorrentUri.endsWith(".torrent", ignoreCase = true)) {
        val filename = File(magnetOrTorrentUri).nameWithoutExtension
        val all = downloadRepository.observeAllStates().first()

        val match = all.firstOrNull { state ->
          state.task.type == com.tv.apps.zippy.download.DownloadType.TORRENT &&
            state.task.targetPath.isNotBlank() &&
            (
              state.task.fileName?.equals(filename, ignoreCase = true) == true ||
              state.task.fileName?.contains(filename, ignoreCase = true) == true ||
              state.task.source?.contains(filename, ignoreCase = true) == true
            )
        }

        if (match != null) {
          val dir = File(match.task.targetPath)
          if (dir.exists() && dir.isDirectory) {
            Timber.i("Found existing download dir for torrent: ${dir.absolutePath}")
            return dir
          }
        }
      }

    } catch (e: Exception) {
      Timber.w(e, "Error while looking up existing download dir in repo")
    }
    return null
  }

  /**
   * Helper function to start MPV playback with streaming URL
   */
  private fun startPlayback(
    activity: android.app.Activity,
    url: String,
    torrentName: String,
    totalSize: Long
  ) {
    val playbackData = PlaybackData(
      title = "Streaming: $torrentName",
      videoLinks = listOf(
        VideoLink(
          url = url,
          name = "Torrent Stream (${totalSize / (1024 * 1024)}MB)",
          headers = emptyMap(),
          position = 0L,
          subtitles = emptyList(),
        )
      ),
      subtitles = emptyList(),
      videoStartIndex = 0,
      subtitleStartIndex = 0,
      isSameEpisode = true,
      hasAd = false
    )
    val bundle = PlaybackDataHelper.createBundle(playbackData)
    activity.navigate(R.id.global_to_navigation_mpv_player, bundle)
  }

  private val inputUrl: String by lazy {
    arguments?.getString("inputUrl") ?: ""
  }
  companion object {
    fun newInstance(url: String): TorrentStreamProgressDialog {
      val dialog = TorrentStreamProgressDialog()
      val args = Bundle()
      args.putString("inputUrl", url)
      dialog.arguments = args
      return dialog
    }
  }
}
