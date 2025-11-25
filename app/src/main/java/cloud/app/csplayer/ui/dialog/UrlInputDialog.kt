package cloud.app.csplayer.ui.dialog

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogUrlInputBinding
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadTask
import cloud.app.csplayer.download.DownloadType
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.library.LibrarySection
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.navigate
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

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

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
          // Get user-configured download path from settings
          val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
          val downloadPathUri = prefs.getString(ctx.getString(R.string.download_path_key), null)

          // Function to get the appropriate download directory
          fun getDownloadDir(subfolder: String): String {
            return if (!downloadPathUri.isNullOrEmpty()) {
              try {
                // User has configured a custom path via SAF
                val uri = downloadPathUri.toUri()
                val docFile = DocumentFile.fromTreeUri(ctx, uri)
                if (docFile != null && docFile.exists() && docFile.canWrite()) {
                  // Create subfolder if needed
                  val subDir = docFile.findFile(subfolder) ?: docFile.createDirectory(subfolder)
                  if (subDir != null) {
                    // For SAF URIs, check if we can convert to file path
                    val uniFile = cloud.app.csplayer.utils.KUniFile.fromUri(ctx, subDir.uri)
                    val filePath = uniFile?.filePath

                    // Only use file path if we can actually write to it (app-specific storage)
                    // For shared storage (e.g., /storage/emulated/0/Movies), keep SAF URI
                    if (filePath != null && filePath.startsWith(ctx.getExternalFilesDir(null)?.absolutePath ?: "/data")) {
                      // App-specific storage - safe to use file path
                      filePath
                    } else {
                      // Shared storage or unknown - keep SAF URI for proper permission handling
                      subDir.uri.toString()
                    }
                  } else {
                    downloadPathUri
                  }
                } else {
                  // Fallback if can't access custom path - use public Download folder
                  val publicDownload = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                  )
                  val downloadSubfolder = java.io.File(publicDownload, subfolder)
                  if (!downloadSubfolder.exists()) {
                    downloadSubfolder.mkdirs()
                  }
                  downloadSubfolder.absolutePath
                }
              } catch (e: Exception) {
                // Fallback on error - use public Download folder
                try {
                  val publicDownload = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                  )
                  val downloadSubfolder = java.io.File(publicDownload, subfolder)
                  if (!downloadSubfolder.exists()) {
                    downloadSubfolder.mkdirs()
                  }
                  downloadSubfolder.absolutePath
                } catch (_: Exception) {
                  // Last resort: app-specific storage
                  ctx.getExternalFilesDir(subfolder)?.absolutePath ?: ctx.filesDir.absolutePath
                }
              }
            } else {
              // No custom path configured, use public Download folder as default
              val publicDownload = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
              )
              val downloadSubfolder = java.io.File(publicDownload, subfolder)

              // Create subfolder if it doesn't exist
              if (!downloadSubfolder.exists()) {
                downloadSubfolder.mkdirs()
              }

              downloadSubfolder.absolutePath
            }
          }
          val appName = ctx.getString(R.string.app_name).replace(" ", "_")
          when {
            inputUrl.startsWith("magnet:") -> {
              DownloadType.TORRENT to getDownloadDir(appName)
            }
            inputUrl.endsWith(".torrent") -> {
              DownloadType.TORRENT to getDownloadDir(appName)
            }
            inputUrl.startsWith("http") -> {
              DownloadType.HTTP to getDownloadDir(appName)
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

        val task = DownloadTask(
          id = taskId,
          type = taskType,
          source = inputUrl, // Always use full URL/magnet as source
          targetPath = saveDir
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
      // Create PlaybackData for URL playback
      val playbackData = PlaybackData(
        title = url,
        videoLinks = listOf(
          VideoLink(
            url = url,
            name = url,
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
