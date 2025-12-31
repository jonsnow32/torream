package com.tv.apps.zippy.ui.dialog

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.DialogUrlInputBinding
import com.tv.apps.zippy.download.DownloadCoordinator
import com.tv.apps.zippy.download.DownloadTask
import com.tv.apps.zippy.download.DownloadType
import com.tv.apps.zippy.model.PlaybackData
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.ui.library.LibrarySection
import com.tv.apps.zippy.utils.AppUtils.getDownloadPath
import com.tv.apps.zippy.utils.AutoClearedValue.Companion.autoCleared
import com.tv.apps.zippy.utils.PlaybackDataHelper
import com.tv.apps.zippy.utils.UIHelper.dismissSafe
import com.tv.apps.zippy.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UrlInputDialog : DockingDialog() {
  private var binding by autoCleared<DialogUrlInputBinding>()
  private val args by lazy { requireArguments() }
  val url: String by lazy { args.getString("url", null) }

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

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
        // Stream torrent using TorrentStreamProgressDialog (dialog now contains streaming logic)
        val progressDialog = TorrentStreamProgressDialog.newInstance(inputUrl)
        progressDialog.show(parentFragmentManager, "TorrentStreamProgress")
        dialog.dismissSafe(activity)
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
