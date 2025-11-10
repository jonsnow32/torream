package cloud.app.csplayer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogUrlInputBinding
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.library.LibrarySection
import cloud.app.csplayer.ui.library.download.TorrentViewModel
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UrlInputDialog : DockingDialog() {
  private var binding by autoCleared<DialogUrlInputBinding>()
  private val args by lazy { requireArguments() }
  val url: String by lazy { args.getString("url", null) }
  private val torrentViewModel: TorrentViewModel by activityViewModels()

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
        // Add magnet link as download
        torrentViewModel.addMagnet(inputUrl)
      } else if (inputUrl.endsWith(".torrent")) {
        // Add torrent file as download
        torrentViewModel.addTorrentFile(inputUrl)
      } else {
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
