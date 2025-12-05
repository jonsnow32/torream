package cloud.app.csplayer.ui.dialog

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogUrlInputBinding
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadTask
import cloud.app.csplayer.download.DownloadType
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.library.LibrarySection
import cloud.app.csplayer.utils.AppUtils.getDownloadPath
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class UrlInputDialog : DockingDialog() {
  private var binding by autoCleared<DialogUrlInputBinding>()
  private val args by lazy { requireArguments() }
  val url: String by lazy { args.getString("url", null) }

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

  @Inject
  lateinit var torrentStreamingService: cloud.app.csplayer.download.TorrentStreamingService

  // File picker for .torrent files
  private val torrentFilePicker = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let { selectedUri ->
      // Set the selected file URI to the input field
      binding.urlInput.setText(selectedUri.toString())
      binding.urlInput.error = null
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = DialogUrlInputBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.urlInput.setText(url, android.widget.TextView.BufferType.EDITABLE)

    // Load .torrent file button
    binding.loadTorrentFileBtt.setOnClickListener {
      try {
        torrentFilePicker.launch("application/x-bittorrent")
      } catch (e: Exception) {
        binding.urlInput.error = "Failed to open file picker: ${e.message}"
      }
    }

    binding.downloadBtt.setOnClickListener {
      val inputUrl = binding.urlInput.text.toString().trim()

      if (inputUrl.isEmpty()) {
        binding.urlInput.error = "Please enter a URL"
        return@setOnClickListener
      }

      val ctx = requireContext()

      lifecycleScope.launch {
        // Determine type and save dir on IO (quick)
        val (taskType, saveDir) = withContext(Dispatchers.IO) {
          when {
            inputUrl.startsWith("magnet:") -> {
              DownloadType.TORRENT to context?.getDownloadPath()
            }

            inputUrl.endsWith(".torrent") -> {
              DownloadType.TORRENT to context?.getDownloadPath()
            }

            inputUrl.startsWith("http") -> {
              DownloadType.HTTP to context?.getDownloadPath()
            }

            else -> null to null
          }
        }

        if (taskType == null || saveDir == null) {
          binding.urlInput.error = "Unsupported URL format"
          return@launch
        }

        // Use a stable task ID based on the URL
        // For magnet links, we could extract info hash, but using a hash of the full URL is simpler and avoids edge cases
        val taskId = when (taskType) {
          DownloadType.TORRENT -> {
            if (inputUrl.startsWith("magnet:", ignoreCase = true)) {
              // For magnet links, try to extract info hash for better readability
              try {
                val uri = inputUrl.toUri()
                val xtParam = uri.getQueryParameters("xt").firstOrNull()
                if (xtParam != null && xtParam.startsWith("urn:btih:", ignoreCase = true)) {
                  // Use the info hash as task ID (normalized to uppercase)
                  xtParam.substring(9).uppercase()
                } else {
                  // Fallback to a hash of the magnet URL
                  "magnet_${inputUrl.hashCode().toString().replace("-", "n")}"
                }
              } catch (_: Exception) {
                "magnet_${inputUrl.hashCode().toString().replace("-", "n")}"
              }
            } else {
              // For .torrent files, use the filename or URL
              inputUrl.substringAfterLast("/").removeSuffix(".torrent").ifBlank {
                "torrent_${inputUrl.hashCode().toString().replace("-", "n")}"
              }
            }
          }

          DownloadType.HTTP -> {
            // For HTTP, use URL as ID (matches database primary key)
            // This ensures task.id matches the URL used as primary key in HttpEntity table
            inputUrl
          }
        }

        val targetPath = saveDir.uri.toString()

        if (targetPath.isBlank()) {
          binding.urlInput.error = "Invalid download directory"
          return@launch
        }

        val task = DownloadTask(
          id = taskId,
          type = taskType,
          source = inputUrl, // Always use full URL/magnet as source
          targetPath = targetPath
        )

        // Start download using coordinator (WorkManager will persist it)
        withContext(Dispatchers.IO) {
          try {
            downloadCoordinator.startDownload(task)
          } catch (e: Exception) {
            withContext(Dispatchers.Main) {
              binding.urlInput.error = "Failed to start download: ${e.message}"
            }
            return@withContext
          }
        }

        // Navigate to downloads section
        val bundle = bundleOf("section" to LibrarySection.DOWNLOADS.ordinal)
        activity?.navigate(R.id.navigation_libraryFragment, bundle)
        dialog?.dismissSafe(activity)
      }
    }

    binding.streamingBtt.setOnClickListener {
      val inputUrl = binding.urlInput.text.toString().trim()

      if (inputUrl.isEmpty()) {
        binding.urlInput.error = "Please enter a URL"
        return@setOnClickListener
      }

      // Check if it's a magnet/torrent link for streaming
      if (inputUrl.startsWith("magnet:", ignoreCase = true) ||
        inputUrl.endsWith(".torrent", ignoreCase = true)
      ) {
        // Stream torrent using TorrentStreamingService
        streamTorrent(inputUrl)
        return@setOnClickListener
      }

      // Create PlaybackData for regular URL playback
      val playbackData = PlaybackData(
        title = inputUrl,
        videoLinks = listOf(
          VideoLink(
            url = inputUrl,
            name = inputUrl,
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
      activity?.navigate(R.id.global_to_navigation_mpv_player, bundle)
      dialog?.dismissSafe(activity)
    }
  }

  private fun streamTorrent(magnetOrTorrentUri: String) {
    val ctx = requireContext()
    val activity = requireActivity()

    // Show loading dialog
    val progressDialog = android.app.ProgressDialog(ctx).apply {
      setTitle("Preparing Stream")
      setMessage("Connecting to peers and downloading initial data...\n0% - 0 KB/s")
      setCancelable(true)
      setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
      max = 100
      setButton(android.app.ProgressDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ ->
        torrentStreamingService.stopStreaming()
        dialog.dismiss()
      }
      show()
    }

    lifecycleScope.launch {
      try {
        val result = torrentStreamingService.startStreaming(magnetOrTorrentUri) { state ->
          // Update progress dialog
          activity.runOnUiThread {
            if (progressDialog.isShowing) {
              progressDialog.progress = state.progress.toInt()
              val speedText = if (state.downloadRate > 0) {
                "${state.downloadRate / 1024} KB/s"
              } else {
                "0 KB/s"
              }
              progressDialog.setMessage(
                "Downloading initial data...\n${state.progress.toInt()}% - $speedText"
              )

              if (state.error != null) {
                progressDialog.dismiss()
                android.widget.Toast.makeText(
                  ctx,
                  "Streaming error: ${state.error}",
                  android.widget.Toast.LENGTH_LONG
                ).show()
              }
            }
          }
        }

        activity.runOnUiThread {
          progressDialog.dismiss()
        }

        result.onSuccess { filePath ->
          Timber.i("Streaming ready, playing file: $filePath")

          // Validate file before playing
          val file = java.io.File(filePath)
          if (!file.exists()) {
            Timber.e("Streaming file does not exist: $filePath")
            android.widget.Toast.makeText(
              ctx,
              "File not found. Download may have been interrupted.",
              android.widget.Toast.LENGTH_LONG
            ).show()
            return@onSuccess
          }

          if (!file.canRead()) {
            Timber.e("Cannot read streaming file: $filePath")
            android.widget.Toast.makeText(
              ctx,
              "Cannot read file. Permission denied.",
              android.widget.Toast.LENGTH_LONG
            ).show()
            return@onSuccess
          }

          val fileSize = file.length()
          if (fileSize < 1024 * 1024) { // Less than 1MB
            Timber.e("Streaming file too small: $fileSize bytes")
            android.widget.Toast.makeText(
              ctx,
              "File too small to play ($fileSize bytes). Wait for more data.",
              android.widget.Toast.LENGTH_LONG
            ).show()
            return@onSuccess
          }

          Timber.i("File validated: ${fileSize / (1024 * 1024)} MB, readable: ${file.canRead()}")

          // Create PlaybackData for streaming file
          val playbackData = PlaybackData(
            title = "Streaming: ${magnetOrTorrentUri.substringAfterLast("/").take(50)}",
            videoLinks = listOf(
              VideoLink(
                url = filePath,
                name = "Torrent Stream (${fileSize / (1024 * 1024)} MB)",
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
          dialog?.dismissSafe(activity)
        }.onFailure { error ->
          Timber.e(error, "Failed to start streaming")
          android.widget.Toast.makeText(
            ctx,
            "Failed to stream: ${error.message}",
            android.widget.Toast.LENGTH_LONG
          ).show()
        }

      } catch (e: Exception) {
        activity.runOnUiThread {
          progressDialog.dismiss()
          android.widget.Toast.makeText(
            ctx,
            "Streaming error: ${e.message}",
            android.widget.Toast.LENGTH_LONG
          ).show()
        }
        Timber.e(e, "Streaming exception")
      }
    }
  }

  companion object {
    fun newInstance(url: String): UrlInputDialog {
      val args = Bundle()
      args.putString("url", url)
      val fragment = UrlInputDialog()
      fragment.arguments = args
      return fragment
    }
  }
}
