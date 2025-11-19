package cloud.app.csplayer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogUrlInputBinding
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.library.LibrarySection
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import cloud.app.csplayer.download.DownloadTask
import cloud.app.csplayer.download.DownloadType
import cloud.app.csplayer.download.http.HttpDownloadManager
import cloud.app.csplayer.download.torrent.TorrentDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class UrlInputDialog : DockingDialog() {
  private var binding by autoCleared<DialogUrlInputBinding>()
  private val args by lazy { requireArguments() }
  val url: String by lazy { args.getString("url", null) }

  @Inject
  lateinit var torrentDownloadManager: TorrentDownloadManager

  @Inject
  lateinit var httpDownloadManager: HttpDownloadManager

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

      // generate id and capture context on main thread
      val id = UUID.randomUUID().toString()
      val ctx = requireContext()

      lifecycleScope.launch {
        // perform filesystem access and download operations on IO dispatcher
        val (taskType, saveDir) = withContext(Dispatchers.IO) {
          when {
            inputUrl.startsWith("magnet:") -> {
              DownloadType.TORRENT to (ctx.getExternalFilesDir("torrents")?.absolutePath
                ?: ctx.filesDir.absolutePath)
            }
            inputUrl.endsWith(".torrent") -> {
              DownloadType.TORRENT to (ctx.getExternalFilesDir("torrents")?.absolutePath
                ?: ctx.filesDir.absolutePath)
            }
            inputUrl.startsWith("http") -> {
              DownloadType.HTTP to (ctx.getExternalFilesDir("http")?.absolutePath
                ?: ctx.filesDir.absolutePath)
            }
            else -> null to null
          }
        }

        if (taskType == null || saveDir == null) {
          // unsupported type - update UI on main thread
          withContext(Dispatchers.Main) {
            binding.urlInput.error = "Only magnet links and .torrent files are supported for downloads"
          }
          return@launch
        }

        val task = DownloadTask(
          id = id,
          type = taskType,
          source = inputUrl,
          targetPath = saveDir
        )

        // enqueue and start on IO (manager implementations may perform IO)
        withContext(Dispatchers.IO) {
          if (taskType == DownloadType.TORRENT) {
            torrentDownloadManager.enqueue(task)
            torrentDownloadManager.start(id)
          } else {
            httpDownloadManager.enqueue(task)
            httpDownloadManager.start(id)
          }
        }

        // navigate and dismiss on Main
        withContext(Dispatchers.Main) {
          val bundle = bundleOf("section" to LibrarySection.DOWNLOADS.ordinal)
          activity?.navigate(R.id.navigation_libraryFragment, bundle)
          dialog?.dismissSafe(activity)
        }
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
