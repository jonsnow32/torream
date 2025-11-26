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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.download.DownloadStatus

class FeedAction(
  val fragment: Fragment,
  private val downloadRepository: DownloadRepository,
  private val downloadCoordinator: DownloadCoordinator,
  private val favoriteRepository: cloud.app.csplayer.favorites.FavoriteRepository
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
                showToast("Failed: $errorMsg")

                // Show retry option
                androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                  .setTitle("Download Failed")
                  .setMessage(errorMsg)
                  .setPositiveButton("Retry") { _, _ ->
                    fragment.lifecycleScope.launch {
                      try {
                        downloadCoordinator.startDownload(state.task)
                        showToast("Retrying download...")
                      } catch (e: Exception) {
                        showToast("Failed to retry: ${e.message}")
                      }
                    }
                  }
                  .setNegativeButton("Cancel", null)
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
          val favoriteText = if (isFav) "⭐ Remove from favorites" else "☆ Add to favorites"

          val listOption = listOf("Play", favoriteText, "Details", "Delete from library")

          withContext(Dispatchers.Main) {
            SelectionDialog.single(listOption, -1, item.media.name, false).show(
              fragment.parentFragmentManager
            ) { bundle ->
              bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
                when (listOption[index]) {
                  "Play" -> {
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
                  "Details" -> {
                    showToast("File: ${item.media.uri}\nDuration: ${item.media.duration}ms")
                  }
                  "Delete from library" -> {
                    androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                      .setTitle("Delete from library?")
                      .setMessage("This will remove the file from your library.")
                      .setPositiveButton("Delete") { _, _ ->
                        // TODO: Implement delete from library
                        showToast("Delete functionality coming soon")
                      }
                      .setNegativeButton("Cancel", null)
                      .show()
                  }
                }
              }
            }
          }
        }
      }

      is FeedData.FolderItem -> {
        // Show options for folder: Open, Show path, etc.
        val listOption = listOf("Open folder", "Show path", "Rescan")

        SelectionDialog.single(listOption, -1, item.folder.name, false).show(
          fragment.parentFragmentManager
        ) { bundle ->
          bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            when (listOption[index]) {
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
        }
      }

      is FeedData.PlaylistItem -> {
        // Show options for playlist: Play all, Edit, Delete, etc.
        val listOption = listOf("Play all", "Edit playlist", "Share", "Delete")

        SelectionDialog.single(listOption, -1, item.title, false).show(
          fragment.parentFragmentManager
        ) { bundle ->
          bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            when (listOption[index]) {
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
                androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                  .setTitle("Delete playlist?")
                  .setMessage("This will delete the playlist: ${item.title}")
                  .setPositiveButton("Delete") { _, _ ->
                    showToast("Delete functionality coming soon")
                  }
                  .setNegativeButton("Cancel", null)
                  .show()
              }
            }
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

          val listOption = buildList {
            // "play" - only if completed
            if (status == cloud.app.csplayer.download.DownloadStatus.COMPLETED) {
              add("play")
            }

            // "stream" - play partially downloaded file (downloading or paused with progress > 10%)
            if ((status == cloud.app.csplayer.download.DownloadStatus.DOWNLOADING ||
                 status == cloud.app.csplayer.download.DownloadStatus.PAUSED) &&
                (state?.progress ?: 0) > 10) {
              add("stream")
            }

            // "pause" - only if downloading
            if (status == cloud.app.csplayer.download.DownloadStatus.DOWNLOADING) {
              add("pause")
            }

            // "resume" - only if paused or failed
            if (status == cloud.app.csplayer.download.DownloadStatus.PAUSED ||
                status == cloud.app.csplayer.download.DownloadStatus.FAILED) {
              add("resume")
            }

            // "cancel" - only if downloading or queued
            if (status == cloud.app.csplayer.download.DownloadStatus.DOWNLOADING ||
                status == cloud.app.csplayer.download.DownloadStatus.QUEUED) {
              add("cancel")
            }

            // "show files" - only if completed or files exist
            if (status == cloud.app.csplayer.download.DownloadStatus.COMPLETED) {
              add("show files")
            }

            // "favorite" - always available
            add(favoriteText)

            // "delete" - always available
            add("delete")
          }

          if (listOption.isEmpty()) {
            showToast("No actions available")
            return@launch
          }

          withContext(Dispatchers.Main) {
            SelectionDialog.single(listOption, -1, "Select action", false).show(
              fragment.parentFragmentManager
            ) { bundle ->
          bundle?.let {
            it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
              Timber.d("Download item clicked: ${listOption[this]}")
              when (listOption[this]) {
                "stream" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      val state = downloadRepository.observeState(item.id).firstOrNull()

                      if (state == null) {
                        showToast("Download not found")
                        return@launch
                      }

                      // For stream, we need to find the file being downloaded
                      val downloadPath = state.task.fileName ?: state.task.targetPath
                      val downloadDir = java.io.File(downloadPath)

                      Timber.d("Attempting to stream from: ${downloadDir.absolutePath}")
                      Timber.d("Download progress: ${state.progress}%, downloaded: ${state.downloadedBytes} bytes")

                      // Find video files (may be incomplete)
                      var fileToPlay: java.io.File? = null

                      when {
                        // Direct file
                        downloadDir.isFile && downloadDir.exists() -> {
                          fileToPlay = downloadDir
                        }

                        // Directory - find largest video file
                        downloadDir.isDirectory && downloadDir.exists() -> {
                          val videoFiles = mutableListOf<java.io.File>()

                          fun findVideoFiles(dir: java.io.File) {
                            dir.listFiles()?.forEach { file ->
                              when {
                                file.isDirectory -> findVideoFiles(file)
                                file.isFile && isVideoFile(file) && file.length() > 1024 * 1024 -> {
                                  // Only include files > 1MB
                                  videoFiles.add(file)
                                }
                              }
                            }
                          }

                          findVideoFiles(downloadDir)

                          if (videoFiles.isNotEmpty()) {
                            fileToPlay = videoFiles.maxByOrNull { it.length() }
                            Timber.d("Found ${videoFiles.size} video files, streaming largest: ${fileToPlay?.name} (${fileToPlay?.length()} bytes)")
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

                      Timber.i("Streaming partially downloaded file: ${fileToPlay.absolutePath} (${fileToPlay.length() / (1024 * 1024)} MB)")

                      // Create VideoLink from file
                      val videoLink = VideoLink(
                        url = fileToPlay.absolutePath,
                        name = when (item) {
                          is FeedData.HttpDownloadItem -> "${item.title} (Streaming ${state.progress}%)"
                          is FeedData.TorrentDownloadItem -> "${item.title} (Streaming ${state.progress}%)"
                          else -> fileToPlay.nameWithoutExtension
                        },
                        headers = emptyMap(),
                        subtitles = emptyList(),
                        position = 0,
                        width = 0,
                        height = 0
                      )

                      val playbackData = PlaybackData(
                        title = videoLink.name,
                        videoLinks = listOf(videoLink),
                        subtitles = emptyList(),
                        videoStartIndex = 0,
                        subtitleStartIndex = 0,
                        isSameEpisode = true,
                        hasAd = false
                      )

                      val bundle = PlaybackDataHelper.createBundle(playbackData)
                      fragment.requireActivity().navigate(R.id.global_to_navigation_mpv_player, bundle)

                    } catch (e: Exception) {
                      Timber.e(e, "Error streaming download %s", item.id)
                      showToast("Failed to stream: ${e.message}")
                    }
                  }
                }

                "play" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      // Get download state to check completion and get file path
                      val state = downloadRepository.observeState(item.id).firstOrNull()

                      if (state == null) {
                        showToast("Download not found")
                        return@launch
                      }

                      // Check if download is completed
                      if (state.status != cloud.app.csplayer.download.DownloadStatus.COMPLETED) {
                        showToast("Download not completed yet")
                        return@launch
                      }

                      // Get downloaded path from database
                      val downloadedFilePath = state.task.fileName

                      if (downloadedFilePath.isNullOrBlank()) {
                        // Fallback to targetPath if downloadedFilePath not set (old downloads)
                        Timber.w("downloadedFilePath is null, using targetPath as fallback")
                        val fallbackPath = state.task.targetPath
                        if (fallbackPath.isBlank()) {
                          showToast("Download path not found")
                          return@launch
                        }
                      }

                      val savedPath = java.io.File(downloadedFilePath ?: state.task.targetPath)

                      if (!savedPath.exists()) {
                        showToast("Downloaded file/folder not found")
                        Timber.e("Path not found: ${savedPath.absolutePath}")
                        return@launch
                      }

                      // Determine actual file to play
                      var actualFile: java.io.File? = null

                      when {
                        // Case 1: Path is a file (HTTP downloads)
                        savedPath.isFile -> {
                          actualFile = savedPath
                          Timber.d("Playing file directly: ${savedPath.absolutePath}")
                        }

                        // Case 2: Path is a directory (Torrent downloads)
                        savedPath.isDirectory -> {
                          Timber.d("Searching for video files in directory: ${savedPath.absolutePath}")

                          // Search recursively for video files
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

                          if (videoFiles.isNotEmpty()) {
                            // Select largest video file (main video)
                            actualFile = videoFiles.maxByOrNull { it.length() }
                            Timber.d("Found ${videoFiles.size} video files, playing largest: ${actualFile?.name} (${actualFile?.length()} bytes)")
                          } else {
                            Timber.e("No video files found in directory: ${savedPath.absolutePath}")
                          }
                        }
                      }

                      // Final check
                      if (actualFile == null || !actualFile.exists()) {
                        showToast("No video file found")
                        Timber.e("Could not find playable video file for taskId=${item.id}")
                        return@launch
                      }

                      Timber.d("Playing downloaded file: ${actualFile.absolutePath}")

                      // Create VideoLink from downloaded file
                      val videoLink = VideoLink(
                        url = actualFile.absolutePath,
                        name = when (item) {
                          is FeedData.HttpDownloadItem -> item.title
                          is FeedData.TorrentDownloadItem -> item.title
                          else -> actualFile.nameWithoutExtension
                        },
                        headers = emptyMap(),
                        subtitles = emptyList(),
                        position = 0,
                        width = 0,
                        height = 0
                      )

                      // Create PlaybackData
                      val playbackData = PlaybackData(
                        title = videoLink.name,
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
                      Timber.e(e, "Error playing download %s", item.id)
                      showToast("Failed to play: ${e.message}")
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

                "delete" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      // Delete using coordinator (will cancel worker and remove from DB)
                      downloadCoordinator.deleteDownload(item.id)

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

                "show files" -> {
                  fragment.lifecycleScope.launch {
                    try {
                      // Get download state
                      val state = downloadRepository.observeState(item.id).firstOrNull()

                      if (state == null) {
                        showToast("Download not found")
                        return@launch
                      }

                      // Get download path
                      val downloadPath = state.task.fileName ?: state.task.targetPath
                      val dir = java.io.File(downloadPath)

                      if (!dir.exists()) {
                        showToast("Download folder not found")
                        Timber.e("Directory not found: ${dir.absolutePath}")
                        return@launch
                      }

                      // Collect all files
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

                      // Sort by size (largest first) and create display list
                      val sortedFiles = allFiles.sortedByDescending { it.length() }
                      val fileNames = sortedFiles.map { file ->
                        val size = formatFileSize(file.length())
                        val type = if (isVideoFile(file)) "🎬" else "📄"
                        "$type ${file.name} ($size)"
                      }

                      // Show file selection dialog
                      SelectionDialog.single(
                        fileNames,
                        -1,
                        "Files (${allFiles.size})",
                        false
                      ).show(fragment.parentFragmentManager) { bundle ->
                        bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { selectedIndex ->
                          // User selected a file, play it
                          val selectedFile = sortedFiles[selectedIndex]
                          playFile(selectedFile, item)
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
          }
        }
          }
        }
      }

      else -> {}
    }
  }

  // Helper function to format file size
  private fun formatFileSize(bytes: Long): String {
    return when {
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "${bytes / 1024} KB"
      bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
      else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
  }

  // Helper function to play a specific file
  private fun playFile(file: java.io.File, item: FeedData) {
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
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
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
  private fun playDownloadedFile(item: FeedData, state: cloud.app.csplayer.download.DownloadState) {
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
