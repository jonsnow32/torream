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

      // Check if it's a magnet link or torrent file
      if (inputUrl.startsWith("magnet:")) {
        // Add magnet link as download via TorrentDownloadManager
        val id = UUID.randomUUID().toString()
        val saveDir = requireContext().getExternalFilesDir("torrents")?.absolutePath
          ?: requireContext().filesDir.absolutePath
        val task = DownloadTask(
          id = id,
          type = DownloadType.TORRENT,
          source = inputUrl,
          targetPath = saveDir
        )
        lifecycleScope.launch {
          torrentDownloadManager.enqueue(task)
          torrentDownloadManager.start(id)
        }

      } else if (inputUrl.endsWith(".torrent")) {
        // Add torrent file as download via TorrentDownloadManager
        val id = UUID.randomUUID().toString()
        val saveDir = requireContext().getExternalFilesDir("torrents")?.absolutePath
          ?: requireContext().filesDir.absolutePath
        val task = DownloadTask(
          id = id,
          type = DownloadType.TORRENT,
          source = inputUrl,
          targetPath = saveDir
        )
        lifecycleScope.launch {
          torrentDownloadManager.enqueue(task)
          torrentDownloadManager.start(id)
        }

      } else if(inputUrl.startsWith("http")){
        val id = UUID.randomUUID().toString()
        val saveDir = requireContext().getExternalFilesDir("http")?.absolutePath
          ?: requireContext().filesDir.absolutePath
        val task = DownloadTask(
          id = id,
          type = DownloadType.HTTP,
          source = inputUrl,
          targetPath = saveDir
        )
        lifecycleScope.launch {
          httpDownloadManager.enqueue(task)
          httpDownloadManager.start(id)
        }
      }
      else {
        // For regular URLs, we could potentially add them as a download
        // For now, show a message that only torrents/magnets are supported
        binding.urlInput.error = "Only magnet links and .torrent files are supported for downloads"
        return@setOnClickListener
      }

      // Navigate to library with downloads tab selected
      val bundle = bundleOf("section" to LibrarySection.DOWNLOADS.ordinal)
      activity?.navigate(R.id.navigation_libraryFragment, bundle)

      dialog?.dismissSafe(activity)
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
