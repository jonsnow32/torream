package com.tv.apps.zippy.ui.dialog

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.tv.apps.zippy.R
import com.tv.apps.zippy.ads.AdManager
import com.tv.apps.zippy.ads.AdPlacementHelper
import com.tv.apps.zippy.databinding.DialogUrlInputBinding
import com.tv.apps.zippy.datastore.Serializer.getSerialized
import com.tv.apps.zippy.datastore.Serializer.putSerialized
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
import javax.inject.Inject

@AndroidEntryPoint
class UrlInputDialog : DockingDialog() {
  private var binding by autoCleared<DialogUrlInputBinding>()
  private val args by lazy { requireArguments() }
  val url: String by lazy { args.getString("url", null) }
  val name: String? by lazy { args.getString("name", null) }
  val headers: Map<String, String> by lazy {
    args.getSerialized<Map<String, String>>("headers") ?: emptyMap()
  }
  val hasAds: Boolean by lazy { args.getBoolean("hasAds", true) }
  val simpleDownload: Boolean by lazy { args.getBoolean("simpleDownload", false) }

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

  @Inject
  lateinit var adManager: AdManager

  @Inject
  lateinit var adPlacementHelper: AdPlacementHelper

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

    if (name?.isNotBlank() == true) binding.text1.text = name
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

      // Show ad before download if hasAds is true
      if (hasAds) {
        adPlacementHelper.showInterstitialForPlacement(
          activity = requireActivity(),
          placement = AdPlacementHelper.AdPlacement.BEFORE_DOWNLOAD_START
        ) {
          // After ad is shown or skipped, proceed with download
          startDownload(inputUrl)
        }
      } else {
        // No ads, proceed directly
        startDownload(inputUrl)
      }
    }

    binding.streamingBtt.setOnClickListener {
      val inputUrl = binding.urlInput.text.toString().trim()

      if (inputUrl.isEmpty()) {
        binding.urlInput.error = "Please enter a URL"
        return@setOnClickListener
      }

      // Show ad before streaming if hasAds is true
      if (hasAds) {
        adPlacementHelper.showInterstitialForPlacement(
          activity = requireActivity(),
          placement = AdPlacementHelper.AdPlacement.BEFORE_VIDEO_PLAY
        ) {
          // After ad is shown or skipped, proceed with streaming
          startStreaming(inputUrl)
        }
      } else {
        // No ads, proceed directly
        startStreaming(inputUrl)
      }
    }

    binding.streamingBtt.isGone = simpleDownload
    binding.loadTorrentFileBtt.isGone = simpleDownload
  }

  /**
   * Start download process for the given URL
   */
  private fun startDownload(inputUrl: String) {
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

          inputUrl.endsWith(".m3u8") -> {
            DownloadType.HLS to context?.getDownloadPath()
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

        DownloadType.HTTP, DownloadType.HLS -> {
          // For HTTP/HLS, use URL as ID (matches database primary key)
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
        targetPath = targetPath,
        fileName = name, // Use provided name or null for auto-detection
        headers = if (headers.isNotEmpty()) headers else null // Use provided headers or null
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

  /**
   * Start streaming process for the given URL
   */
  private fun startStreaming(inputUrl: String) {
    // Check if it's a magnet/torrent link for streaming
    if (inputUrl.startsWith("magnet:", ignoreCase = true) ||
      inputUrl.endsWith(".torrent", ignoreCase = true)
    ) {
      // Stream torrent using TorrentStreamProgressDialog
      val progressDialog = TorrentStreamProgressDialog.newInstance(inputUrl)
      progressDialog.show(parentFragmentManager, "TorrentStreamProgress")
      dialog.dismissSafe(activity)
      return
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


  companion object {
    fun newInstance(
      url: String,
      name: String? = null,
      headers: Map<String, String>? = null,
      simpleDownload: Boolean = false,
      hasAds: Boolean = true
    ): UrlInputDialog {
      val args = Bundle()
      args.putString("url", url)
      if (name != null) {
        args.putString("name", name)
      }
      if (headers != null) {
        args.putSerialized("headers", headers)
      }
      args.putBoolean("simpleDownload", simpleDownload)
      args.putBoolean("hasAds", hasAds)

      val fragment = UrlInputDialog()
      fragment.arguments = args
      return fragment
    }
  }
}
