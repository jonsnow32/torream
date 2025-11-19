package cloud.app.csplayer.ui.feed

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.R
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import kotlinx.coroutines.launch
import timber.log.Timber
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.http.HttpDownloadManager
import cloud.app.csplayer.download.torrent.TorrentDownloadManager

class FeedAction(
  val fragment: Fragment,
  private val downloadRepository: DownloadRepository,
  private val httpManager: HttpDownloadManager,
  private val torrentManager: TorrentDownloadManager
) {
  fun onItemClick(item: FeedData) {
    when (item) {
      is FeedData.FolderItem -> {
        // Navigate into folder using Navigation Component
        val bundle = Bundle().apply {
          putString("root_folder_path", item.folder.path)
        }
        // Use Navigation Component to navigate with automatic backstack management
        // R.id will be generated after build, fallback to dynamic navigation
        try {
          fragment.findNavController().navigate(R.id.action_feedFragment_self, bundle)
        } catch (_: Exception) {
          // Fallback if action ID not generated yet
          fragment.findNavController().navigate(R.id.feedFragment, bundle)
        }
      }

      is FeedData.MediaItem -> {
        playMediaItem(item)
        // Play video with auto-next enabled for continuous playback through feed
        //playVideosWithAutoNext(item)
      }

      is FeedData.AdItem -> {
        // TODO: Handle ad click
        showToast("Ad clicked: ${item.title}")
      }

      is FeedData.HorizontalList -> {
        // Horizontal lists don't have individual click action
        // Items inside the list have their own click handlers
      }

      is FeedData.HttpDownloadItem,
      is FeedData.TorrentDownloadItem -> {
        //show torrent option dialog play/pause/cancel
      }
    }
  }

  fun onItemLongClick(item: FeedData) {
    when (item) {
      is FeedData.HttpDownloadItem,
      is FeedData.TorrentDownloadItem -> {
        val listOption = listOf("play", "cancel", "resume", "pause", "delete")
        SelectionDialog.single(listOption, -1, "Select action", false).show(
          fragment.parentFragmentManager
        ) { bundle ->
          bundle?.let {
            it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
              Timber.d("Download item clicked: ${listOption[this]}")
              when (listOption[this]) {
                "play" -> {
                  if (item is FeedData.MediaItem) {
                    playMediaItem(item)
                  }
                }

                "resume" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      if (item is FeedData.HttpDownloadItem) {
                        httpManager.resume(item.id)
                      } else if (item is FeedData.TorrentDownloadItem) {
                        torrentManager.resume(item.id)
                      }
                      showToast(fragment.getString(R.string.download_resumed))
                    } catch (e: Exception) {
                      Timber.e(e, "Error resuming download %s", item.id)
                      showToast(fragment.getString(R.string.error_loading))
                    }
                  }
                }

                "cancel" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      if (item is FeedData.HttpDownloadItem) {
                        httpManager.cancel(item.id)
                      } else if (item is FeedData.TorrentDownloadItem) {
                        torrentManager.cancel(item.id)
                      }
                      showToast(fragment.getString(R.string.download_canceled))
                    } catch (e: Exception) {
                      Timber.e(e, "Error canceling download %s", item.id)
                      showToast(fragment.getString(R.string.error_loading))
                    }
                  }
                }

                "pause" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      if (item is FeedData.HttpDownloadItem) {
                        httpManager.pause(item.id)
                      } else if (item is FeedData.TorrentDownloadItem) {
                        torrentManager.pause(item.id)
                      }
                      showToast(fragment.getString(R.string.download_paused))
                    } catch (e: Exception) {
                      Timber.e(e, "Error pausing download %s", item.id)
                      showToast(fragment.getString(R.string.error_loading))
                    }
                  }
                }

                "delete" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      // first cancel any running download resources
                      if (item is FeedData.HttpDownloadItem) {
                        httpManager.cancel(item.id)
                      } else if (item is FeedData.TorrentDownloadItem) {
                        torrentManager.cancel(item.id)
                      }

                      // remove from repository (will remove DB entry / in-memory state)
                      downloadRepository.deleteTask(item.id)

                      // Inform user
                      showToast("Deleted")

                      // Trigger UI refresh on hosting fragment (Library/Feed) so PagingSource is recreated
                      try {
                        (fragment as? cloud.app.csplayer.ui.library.LibraryFragment)?.let { frag ->
                          // prefer invalidating the ViewModel paging so Pager recreates its PagingSource
                          frag.invalidatePaging()
                          frag.refreshAdapter()
                        }
                      } catch (_: Exception) {
                      }
                      try {
                        (fragment as? cloud.app.csplayer.ui.home.FeedFragment)?.refreshAdapter()
                      } catch (_: Exception) {
                      }

                    } catch (e: Exception) {
                      Timber.e(e, "Error deleting download %s", item.id)
                      showToast(fragment.getString(R.string.error_loading))
                    }
                  }
                }
              }
            }
          }
        }
      }

      else -> {}
    }
  }

  private fun playMediaItem(item: FeedData.MediaItem) {
    fragment.viewLifecycleOwner.lifecycleScope.launch {
      val videoLink = VideoLink(
        url = item.media.uri,
        name = item.title,
        headers = emptyMap(),
        subtitles = emptyList(),
        position = item.media.position,
        width = item.media.width,
        height = item.media.height
      )

      // Create PlaybackData for single file
      val playbackData = PlaybackData(
        title = item.title,
        videoLinks = listOf(videoLink),
        subtitles = emptyList(),
        videoStartIndex = 0,
        subtitleStartIndex = 0,
        isSameEpisode = true,
        hasAd = false
      )

      // Navigate to MPV player with PlaybackData
      val bundle = PlaybackDataHelper.createBundle(playbackData)
      fragment.requireActivity().navigate(R.id.global_to_navigation_mpv_player, bundle)
    }
  }
}
