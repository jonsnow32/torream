package cloud.app.csplayer.ui.feed

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.R
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.dialog.FeedActionDialog
import cloud.app.csplayer.ui.dialog.ActionItem
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.media.repository.MediaRepository

class FeedAction(
  val fragment: Fragment,
  private val downloadRepository: DownloadRepository,
  private val downloadCoordinator: DownloadCoordinator,
  private val favoriteRepository: cloud.app.csplayer.favorites.FavoriteRepository,
  private val repository: MediaRepository,
  private val playlistRepository: cloud.app.csplayer.media.repository.PlaylistRepository
) {

  companion object {
    // Video file extensions for fallback search
    private val VIDEO_EXTENSIONS = setOf(
      "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
      "mpg", "mpeg", "3gp", "ts", "m2ts", "vob", "ogv", "rmvb"
    )

    private fun isVideoFile(file: java.io.File): Boolean {
      return file.extension.lowercase() in VIDEO_EXTENSIONS
    }
  }

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

      is FeedData.PlaylistItem -> {
        // TODO: Navigate to playlist details screen
        // For now, show a toast
        showToast("Playlist: ${item.title} (${item.itemCount} items)")
      }

      is FeedData.HttpDownloadItem,
      is FeedData.TorrentDownloadItem -> {
        // Handle download item click based on status
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()

            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            when (state.status) {
              DownloadStatus.COMPLETED -> {
                // Play the downloaded file
                playDownloadedFile(item, state)
              }

              DownloadStatus.DOWNLOADING -> {
                // Pause download
                try {
                  downloadCoordinator.pauseDownload(item.id)
                  showToast("Download paused")
                } catch (e: Exception) {
                  showToast("Failed to pause: ${e.message}")
                }
              }

              DownloadStatus.PAUSED -> {
                // Resume download
                try {
                  downloadCoordinator.startDownload(state.task)
                  showToast("Download resumed")
                } catch (e: Exception) {
                  showToast("Failed to resume: ${e.message}")
                }
              }

              DownloadStatus.QUEUED -> {
                // Show status
                showToast("Download queued (${state.progress}%)")
              }

              DownloadStatus.FAILED -> {
                // Show error and offer retry
                val errorMsg = state.error ?: "Unknown error"
                //showToast("Failed: $errorMsg")

                // Show retry option
                androidx.appcompat.app.AlertDialog.Builder(
                  fragment.requireContext(),
                  R.style.BaseMaterialDialogTheme
                )
                  .setTitle(fragment.getString(R.string.download_failed_title))
                  .setMessage(errorMsg)
                  .setPositiveButton(fragment.getString(R.string.retry)) { _, _ ->
                    fragment.lifecycleScope.launch {
                      try {
                        downloadCoordinator.startDownload(state.task)
                        showToast("Retrying download...")
                      } catch (e: Exception) {
                        showToast("Failed to retry: ${e.message}")
                      }
                    }
                  }
                  .setNegativeButton(fragment.getString(R.string.cancel), null)
                  .show()
              }

              else -> {
                // Unknown status - show long click menu
                showToast("Long press for options")
              }
            }
          } catch (e: Exception) {
            Timber.e(e, "Error handling download item click")
            showToast("Error: ${e.message}")
          }
        }
      }
    }
  }

  fun onItemLongClick(item: FeedData) {
    when (item) {
      is FeedData.MediaItem -> {
        fragment.lifecycleScope.launch {
          // Check if already favorited
          val isFav = favoriteRepository.isFavorite(item.media.uri)
          val isHistory = favoriteRepository.isHistory(item.media.uri)

          val actionItems = buildMediaItemActions(isFav, isHistory)

          withContext(Dispatchers.Main) {
            FeedActionDialog.newInstance(actionItems).show(
              fragment.parentFragmentManager
            ) { bundle ->
              bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
                val favoriteText =
                  if (isFav) fragment.getString(R.string.action_remove_from_favorites) else fragment.getString(
                    R.string.action_add_to_favorites
                  )
                handleMediaItemAction(item, actionItems[index], favoriteText)
              }
            }
          }
        }
      }

      is FeedData.FolderItem -> {
        val actionItems = buildFolderItemActions()

        FeedActionDialog.newInstance(actionItems).show(
          fragment.parentFragmentManager
        ) { bundle ->
          bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            handleFolderItemAction(item, actionItems[index].title)
          }
        }
      }

      is FeedData.PlaylistItem -> {
        val actionItems = buildPlaylistItemActions()

        FeedActionDialog.newInstance(actionItems).show(
          fragment.parentFragmentManager
        ) { bundle ->
          bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            handlePlaylistItemAction(item, actionItems[index].title)
          }
        }
      }

      is FeedData.AdItem -> {
        // No long click action for ads
        showToast("Long press not available for ads")
      }

      is FeedData.HorizontalList -> {
        // No long click action for horizontal lists
        showToast("Long press items within the list instead")
      }

      is FeedData.HttpDownloadItem,
      is FeedData.TorrentDownloadItem -> {
        // Build dynamic options based on download status
        fragment.lifecycleScope.launch {
          val state = downloadRepository.observeState(item.id).firstOrNull()
          val status = state?.status

          // Check if favorited
          val isFav = favoriteRepository.isFavorite(item.id)
          val favoriteText = if (isFav) "⭐ Remove from favorites" else "☆ Add to favorites"

          val actionItems = buildDownloadItemActions(status, favoriteText)

          if (actionItems.isEmpty()) {
            showToast("No actions available")
            return@launch
          }

          withContext(Dispatchers.Main) {
            FeedActionDialog.newInstance(actionItems).show(
              fragment.parentFragmentManager
            ) { bundle ->
              bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
                handleDownloadItemAction(item, actionItems[index].id, favoriteText, state)
              }
            }
          }
        }
      }
    }
  }

  // ============ Helper functions to build ActionItem lists ============

  private fun buildMediaItemActions(isFav: Boolean, isHistory: Boolean): List<ActionItem> {
    val favoriteText =
      if (isFav) fragment.getString(R.string.action_remove_from_favorites) else fragment.getString(R.string.action_add_to_favorites)

    val list = mutableListOf<ActionItem>(
      ActionItem(
        id = "play",
        title = "Play",
        iconRes = R.drawable.outline_play_circle_24,
        isDestructive = false
      ),
      ActionItem(
        id = "favorite",
        title = favoriteText,
        iconRes = if (isFav) R.drawable.favorite_24dp else R.drawable.favorite_border_24dp,
        isDestructive = false
      ),
      ActionItem(
        id = "details",
        title = "Details",
        iconRes = R.drawable.outline_format_list_bulleted_24,
        isDestructive = false
      ),
      ActionItem(
        id = "add_to_playlist",
        title = "Add to playlist",
        iconRes = R.drawable.media3_icon_playlist_add,
        isDestructive = false
      ),

      )
    if (isHistory)
      list.add(
        ActionItem(
          id = "delete_history",
          title = fragment.getString(R.string.delete_from_history),
          iconRes = R.drawable.ic_baseline_delete_outline_24,
          isDestructive = true
        )
      )
    return list
  }

  private fun buildFolderItemActions(): List<ActionItem> {
    return listOf(
      ActionItem(
        id = "open",
        title = "Open folder",
        iconRes = R.drawable.outline_arrow_outward_24,
        isDestructive = false
      ),
      ActionItem(
        id = "path",
        title = "Show path",
        iconRes = R.drawable.outline_automation_24,
        isDestructive = false
      ),
      ActionItem(
        id = "rescan",
        title = "Rescan",
        iconRes = R.drawable.outline_cached_24,
        isDestructive = false
      )
    )
  }

  private fun buildPlaylistItemActions(): List<ActionItem> {
    return listOf(
      ActionItem(
        id = "play_all",
        title = "Play all",
        iconRes = null,
        isDestructive = false
      ),
      ActionItem(
        id = "edit",
        title = "Edit playlist",
        iconRes = null,
        isDestructive = false
      ),
      ActionItem(
        id = "share",
        title = "Share",
        iconRes = null,
        isDestructive = false
      ),
      ActionItem(
        id = "delete",
        title = "Delete",
        iconRes = null,
        isDestructive = true
      )
    )
  }

  private fun buildDownloadItemActions(
    status: DownloadStatus?,
    favoriteText: String
  ): List<ActionItem> {
    return buildList {
      // "play" - only if completed
      if (status == DownloadStatus.COMPLETED) {
        add(
          ActionItem(
            id = "play",
            title = fragment.getString(R.string.play),
            iconRes = R.drawable.outline_play_circle_24,
            isDestructive = false
          )
        )
      }

      // "stream" - play partially downloaded file
      if (status == DownloadStatus.PAUSED) {
        add(
          ActionItem(
            id = "stream",
            title = fragment.getString(R.string.stream),
            iconRes = R.drawable.play_to_pause,
            isDestructive = false
          )
        )
      }

      // "pause" - only if downloading
      if (status == DownloadStatus.DOWNLOADING) {
        add(
          ActionItem(
            id = "pause",
            title = fragment.getString(R.string.pause),
            iconRes = R.drawable.pause_to_play,
            isDestructive = false
          )
        )
      }

      // "resume" - only if paused or failed
      if (status == DownloadStatus.PAUSED) {
        add(
          ActionItem(
            id = "resume",
            title = fragment.getString(R.string.resume),
            iconRes = R.drawable.baseline_sync_24,
            isDestructive = false
          )
        )
      }
      if (status == DownloadStatus.FAILED) {
        add(
          ActionItem(
            id = "retry",
            title = fragment.getString(R.string.retry),
            iconRes = R.drawable.baseline_sync_24,
            isDestructive = false
          )
        )
      }
      // "cancel" - only if downloading or queued
      if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED) {
        add(
          ActionItem(
            id = "cancel",
            title = fragment.getString(R.string.cancel),
            iconRes = R.drawable.pause_to_play,
            isDestructive = true
          )
        )
      }

      // "show files" - only if completed
      if (status == DownloadStatus.COMPLETED) {
        add(
          ActionItem(
            id = "show_files",
            title = fragment.getString(R.string.show_files),
            iconRes = R.drawable.outline_arrow_outward_24,
            isDestructive = false
          )
        )
      }

//      // "favorite" - always available
//      add(
//        ActionItem(
//          id = "favorite",
//          title = favoriteText,
//          iconRes = if(isfav) R.drawable.favorite_24dp else R.drawable.favorite_border_24dp,
//          isDestructive = false
//        )
//      )

      // "delete" - always available
      add(
        ActionItem(
          id = "delete",
          title = fragment.getString(R.string.delete),
          iconRes = R.drawable.ic_baseline_delete_outline_24,
          isDestructive = true
        )
      )
    }
  }

  // ============ Helper functions to handle actions ============

  private fun handleMediaItemAction(
    item: FeedData.MediaItem,
    actionItem: ActionItem,
    favoriteText: String
  ) {
    when (actionItem.id) {
      "play" -> {
        playMediaItem(item)
      }

      favoriteText -> {
        fragment.lifecycleScope.launch {
          try {
            val favorite = cloud.app.csplayer.media.dao.FavoriteEntity(
              id = item.media.uri,
              type = "media",
              title = item.title,
              uri = item.media.uri,
              thumbnailPath = null
            )
            val added = favoriteRepository.toggleFavorite(favorite)
            showToast(if (added) "Added to favorites ⭐" else "Removed from favorites")
          } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite")
            showToast("Failed to update favorite")
          }
        }
      }

      "details" -> {
        showToast("File: ${item.media.uri}\nDuration: ${item.media.duration}ms")
      }

      "add_to_playlist" -> {
        showAddToPlaylistDialog(item)
      }

      "delete_history" -> {
        androidx.appcompat.app.AlertDialog.Builder(
          fragment.requireContext(),
          R.style.BaseMaterialDialogTheme
        )
          .setTitle(fragment.getString(R.string.delete_from_library_title))
          .setMessage(fragment.getString(R.string.delete_from_library_message))
          .setPositiveButton(fragment.getString(R.string.delete)) { _, _ ->
            deleteFromPlaybackHistory(item)
          }
          .setNegativeButton(fragment.getString(R.string.cancel), null)
          .show()
      }
    }
  }

  private fun handleFolderItemAction(item: FeedData.FolderItem, actionTitle: String) {
    when (actionTitle) {
      "Open folder" -> {
        onItemClick(item) // Reuse click action
      }

      "Show path" -> {
        showToast("Path: ${item.folder.path}")
      }

      "Rescan" -> {
        showToast("Rescanning folder...")
        // TODO: Implement folder rescan
      }
    }
  }

  private fun handlePlaylistItemAction(item: FeedData.PlaylistItem, actionTitle: String) {
    when (actionTitle) {
      "Play all" -> {
        onItemClick(item) // Reuse click action
      }

      "Edit playlist" -> {
        showToast("Edit playlist functionality coming soon")
      }

      "Share" -> {
        showToast("Share playlist functionality coming soon")
      }

      "Delete" -> {
        androidx.appcompat.app.AlertDialog.Builder(
          fragment.requireContext(),
          R.style.BaseMaterialDialogTheme
        )
          .setTitle(fragment.getString(R.string.delete_playlist_title))
          .setMessage(fragment.getString(R.string.delete_playlist_message, item.title))
          .setPositiveButton(fragment.getString(R.string.delete)) { _, _ ->
            showToast("Delete functionality coming soon")
          }
          .setNegativeButton(fragment.getString(R.string.cancel), null)
          .show()
      }
    }
  }

  private fun handleDownloadItemAction(
    item: FeedData,
    actionId: String,
    favoriteText: String,
    state: cloud.app.csplayer.download.DownloadState?
  ) {
    when (actionId) {
      "play" -> {
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()
            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            if (state.status != DownloadStatus.COMPLETED) {
              showToast("Download not completed yet")
              return@launch
            }

            playDownloadedFile(item, state)
          } catch (e: Exception) {
            Timber.e(e, "Error playing download %s", item.id)
            showToast("Failed to play: ${e.message}")
          }
        }
      }

      "stream" -> {
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()
            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            val downloadPath = state.task.fileName ?: state.task.targetPath
            val downloadDir = java.io.File(downloadPath)

            var fileToPlay: java.io.File? = null

            when {
              downloadDir.isFile && downloadDir.exists() -> {
                fileToPlay = downloadDir
              }

              downloadDir.isDirectory && downloadDir.exists() -> {
                val videoFiles = mutableListOf<java.io.File>()
                fun findVideoFiles(dir: java.io.File) {
                  dir.listFiles()?.forEach { file ->
                    when {
                      file.isDirectory -> findVideoFiles(file)
                      file.isFile && isVideoFile(file) && file.length() > 1024 * 1024 -> {
                        videoFiles.add(file)
                      }
                    }
                  }
                }
                findVideoFiles(downloadDir)

                if (videoFiles.isNotEmpty()) {
                  fileToPlay = videoFiles.maxByOrNull { it.length() }
                }
              }
            }

            if (fileToPlay == null || !fileToPlay.exists()) {
              showToast("No video file found to stream. Wait for more data to download.")
              return@launch
            }

            if (fileToPlay.length() < 1024 * 1024) {
              showToast("File too small to stream (${fileToPlay.length()} bytes). Wait for more data.")
              return@launch
            }

            playFile(fileToPlay, item)
          } catch (e: Exception) {
            Timber.e(e, "Error streaming download %s", item.id)
            showToast("Failed to stream: ${e.message}")
          }
        }
      }

      "pause" -> {
        fragment.lifecycleScope.launch {
          try {
            downloadCoordinator.pauseDownload(item.id)
            showToast(fragment.getString(R.string.download_paused))
          } catch (e: Exception) {
            Timber.e(e, "Error pausing download %s", item.id)
            showToast(fragment.getString(R.string.error_loading))
          }
        }
      }

      "resume" -> {
        fragment.lifecycleScope.launch {
          try {
            downloadCoordinator.resumeDownload(item.id)
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
            downloadCoordinator.pauseDownload(item.id)
            showToast(fragment.getString(R.string.download_canceled))
          } catch (e: Exception) {
            Timber.e(e, "Error canceling download %s", item.id)
            showToast(fragment.getString(R.string.error_loading))
          }
        }
      }

      "delete" -> {
        fragment.lifecycleScope.launch {
          try {
            downloadCoordinator.deleteDownload(item.id)
            showToast("Deleted")

            try {
              (fragment as? cloud.app.csplayer.ui.library.LibraryFragment)?.let { frag ->
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

      favoriteText -> {
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()
            val downloadType = when (item) {
              is FeedData.HttpDownloadItem -> "download_http"
              is FeedData.TorrentDownloadItem -> "download_torrent"
              else -> "download"
            }

            val favorite = cloud.app.csplayer.media.dao.FavoriteEntity(
              id = item.id,
              type = downloadType,
              title = item.title,
              uri = state?.task?.fileName ?: state?.task?.targetPath,
              thumbnailPath = null
            )

            val added = favoriteRepository.toggleFavorite(favorite)
            showToast(if (added) "Added to favorites ⭐" else "Removed from favorites")
          } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite")
            showToast("Failed to update favorite")
          }
        }
      }

      "show_files" -> {
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()
            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            val downloadPath = state.task.fileName ?: state.task.targetPath
            val dir = java.io.File(downloadPath)

            if (!dir.exists()) {
              showToast("Download folder not found")
              return@launch
            }

            val allFiles = mutableListOf<java.io.File>()
            fun collectFiles(directory: java.io.File) {
              directory.listFiles()?.forEach { file ->
                when {
                  file.isDirectory -> collectFiles(file)
                  file.isFile -> allFiles.add(file)
                }
              }
            }

            if (dir.isDirectory) {
              collectFiles(dir)
            } else if (dir.isFile) {
              allFiles.add(dir)
            }

            if (allFiles.isEmpty()) {
              showToast("No files found")
              return@launch
            }

            val sortedFiles = allFiles.sortedByDescending { it.length() }
            val fileActionItems = sortedFiles.mapIndexed { index, file ->
              val size = formatFileSize(file.length())
              val type = if (isVideoFile(file)) "🎬" else "📄"
              ActionItem(
                id = index.toString(),
                title = "$type ${file.name} ($size)",
                iconRes = null,
                isDestructive = false
              )
            }

            FeedActionDialog.newInstance(fileActionItems).show(
              fragment.parentFragmentManager
            ) { bundle ->
              bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)
                ?.let { selectedIndex ->
                  playFile(sortedFiles[selectedIndex], item)
                }
            }
          } catch (e: Exception) {
            Timber.e(e, "Error showing files for %s", item.id)
            showToast("Error showing files: ${e.message}")
          }
        }
      }
    }
  }

  // Helper function to format file size
  fun formatFileSize(bytes: Long): String {
    return when {
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "${bytes / 1024} KB"
      bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
      else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
  }

  // Helper function to play a specific file
  fun playFile(file: java.io.File, item: FeedData) {
    fragment.lifecycleScope.launch {
      try {
        if (!file.exists()) {
          showToast("File not found: ${file.name}")
          return@launch
        }

        Timber.d("Playing selected file: ${file.absolutePath}")

        // Scan file into MediaStore to ensure it can be tracked in history
        // This is done asynchronously and won't block playback
        withContext(Dispatchers.IO) {
          try {
            val intent =
              android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = android.net.Uri.fromFile(file)
            fragment.requireContext().sendBroadcast(intent)
            Timber.d("Requested MediaStore scan for: ${file.absolutePath}")
          } catch (e: Exception) {
            Timber.w(e, "Failed to scan file into MediaStore")
          }
        }

        // Create VideoLink from file
        val videoLink = VideoLink(
          url = file.absolutePath,
          name = file.nameWithoutExtension,
          headers = emptyMap(),
          subtitles = emptyList(),
          position = 0,
          width = 0,
          height = 0
        )

        // Create PlaybackData
        val playbackData = PlaybackData(
          title = when (item) {
            is FeedData.HttpDownloadItem -> item.title
            is FeedData.TorrentDownloadItem -> item.title
            else -> file.nameWithoutExtension
          },
          videoLinks = listOf(videoLink),
          subtitles = emptyList(),
          videoStartIndex = 0,
          subtitleStartIndex = 0,
          isSameEpisode = true,
          hasAd = false
        )

        // Navigate to MPV player
        val bundle = PlaybackDataHelper.createBundle(playbackData)
        fragment.requireActivity().navigate(R.id.global_to_navigation_mpv_player, bundle)

      } catch (e: Exception) {
        Timber.e(e, "Error playing file ${file.name}")
        showToast("Failed to play: ${e.message}")
      }
    }
  }

  // Helper function to play completed download
  fun playDownloadedFile(item: FeedData, state: cloud.app.csplayer.download.DownloadState) {
    try {
      val downloadedFilePath = state.task.fileName

      if (downloadedFilePath.isNullOrBlank()) {
        // Fallback to targetPath
        Timber.w("downloadedFilePath is null, using targetPath")
        val targetPath = state.task.targetPath
        if (targetPath.isBlank()) {
          showToast("Download path not found")
          return
        }
      }

      val path = downloadedFilePath ?: state.task.targetPath
      val savedPath = java.io.File(path)

      if (!savedPath.exists()) {
        showToast("Downloaded file not found")
        Timber.e("File not found: $path")
        return
      }

      // Check if it's a file or directory
      if (savedPath.isFile) {
        // Single file - play directly
        playFile(savedPath, item)
      } else if (savedPath.isDirectory) {
        // Directory (torrent) - find and play largest video file
        val videoFiles = mutableListOf<java.io.File>()

        fun findVideoFiles(dir: java.io.File) {
          dir.listFiles()?.forEach { file ->
            when {
              file.isDirectory -> findVideoFiles(file)
              file.isFile && isVideoFile(file) -> videoFiles.add(file)
            }
          }
        }

        findVideoFiles(savedPath)

        if (videoFiles.isEmpty()) {
          showToast("No video files found in download")
          return
        }

        if (videoFiles.size == 1) {
          // Only one video file - play it
          playFile(videoFiles[0], item)
        } else {
          // Multiple video files - play largest one
          val largestFile = videoFiles.maxByOrNull { it.length() }
          if (largestFile != null) {
            Timber.i("Playing largest video file: ${largestFile.name} (${largestFile.length() / (1024 * 1024)} MB)")
            playFile(largestFile, item)
          }
        }
      } else {
        showToast("Invalid download path")
      }

    } catch (e: Exception) {
      Timber.e(e, "Error playing downloaded file")
      showToast("Failed to play: ${e.message}")
    }
  }

  fun playMediaItem(item: FeedData.MediaItem) {
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

  private fun deleteFromPlaybackHistory(item: FeedData.MediaItem) {
    fragment.lifecycleScope.launch {
      try {
        val mediaUri = item.media.uri
        Timber.d("Deleting from playback history: $mediaUri")

        val success = withContext(Dispatchers.IO) {
          repository.deletePlaybackHistory(mediaUri)
        }

        if (success) {
          showToast("Removed from history")
          Timber.d("Successfully deleted from playback history: $mediaUri")
        } else {
          showToast("Failed to remove from history")
          Timber.w("Failed to delete from playback history: $mediaUri")
        }

      } catch (e: Exception) {
        Timber.e(e, "Error deleting from playback history")
        showToast("Error: ${e.message}")
      }
    }
  }

  private fun showAddToPlaylistDialog(item: FeedData.MediaItem) {
    fragment.lifecycleScope.launch {
      try {
        Timber.d("Loading playlists for add to playlist dialog")

        // Get all playlists - collect from Flow
        val playlists = withContext(Dispatchers.IO) {
          try {
            playlistRepository.getAllPlaylists().first()
          } catch (_: NoSuchElementException) {
            Timber.w("No playlists emitted from Flow")
            emptyList()
          } catch (e: Exception) {
            Timber.e(e, "Error fetching playlists from repository")
            emptyList()
          }
        }

        Timber.d("Loaded ${playlists.size} playlists")

        if (playlists.isEmpty()) {
          showToast("No playlists found. Create one first.")
          Timber.w("No playlists available")
          return@launch
        }

        // Build action items for playlist selection
        val playlistItems = playlists.map { playlist ->
          Timber.d("Adding playlist to selection: ${playlist.name} (id: ${playlist.id})")
          ActionItem(
            id = playlist.id.toString(),
            title = playlist.name,
            iconRes = R.drawable.media3_icon_playlist_add,
            isDestructive = false
          )
        }

        Timber.d("Built ${playlistItems.size} playlist items for dialog")

        // Show playlist selection dialog
        withContext(Dispatchers.Main) {
          FeedActionDialog.newInstance(playlistItems).show(
            fragment.parentFragmentManager
          ) { bundle ->
            bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { selectedIndex ->
              Timber.d("User selected playlist at index: $selectedIndex")
              val selectedPlaylist = playlists[selectedIndex]
              Timber.d("Selected playlist: ${selectedPlaylist.name}")
              addMediaToPlaylist(item, selectedPlaylist.id)
            }
          }
        }

      } catch (e: Exception) {
        Timber.e(e, "Error showing playlist dialog")
        showToast("Error: ${e.message}")
      }
    }
  }

  private fun addMediaToPlaylist(item: FeedData.MediaItem, playlistId: Long) {
    fragment.lifecycleScope.launch {
      try {
        val mediaUri = item.media.uri
        Timber.d("Adding media to playlist - playlistId: $playlistId, mediaUri: $mediaUri")

        withContext(Dispatchers.IO) {
          playlistRepository.addMediaToPlaylist(playlistId, mediaUri)
        }

        showToast("Added to playlist")
        Timber.d("Successfully added media to playlist")

      } catch (e: Exception) {
        Timber.e(e, "Error adding media to playlist")
        showToast("Failed to add to playlist: ${e.message}")
      }
    }
  }
}
