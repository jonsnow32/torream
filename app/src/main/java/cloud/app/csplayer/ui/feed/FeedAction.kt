package cloud.app.csplayer.ui.feed

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.R
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.model.Media
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
import cloud.app.csplayer.download.DownloadState
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.utils.UnifiedFile
import cloud.app.csplayer.utils.UnifiedFileFactory

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

    // Audio file extensions supported by MPV player
    private val AUDIO_EXTENSIONS = setOf(
      "mp3", "aac", "flac", "wav", "ogg", "m4a", "wma"
    )

    private fun isVideoFile(file: UnifiedFile): Boolean {
      val fileName = file.name.lowercase()
      val extension = fileName.substringAfterLast('.', "")
      return extension in VIDEO_EXTENSIONS
    }

    private fun isAudioFile(file: UnifiedFile): Boolean {
      val fileName = file.name.lowercase()
      val extension = fileName.substringAfterLast('.', "")
      return extension in AUDIO_EXTENSIONS
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
        fragment.lifecycleScope.launch {
          try {
            // Convert playlist id from String to Long
            val playlistId = item.id.toLongOrNull() ?: run {
              showToast("Invalid playlist ID")
              return@launch
            }

            // Get all playlist items from repository
            val playlistItems = playlistRepository.getPlaylistItems(playlistId)
              .firstOrNull() ?: emptyList()

            if (playlistItems.isEmpty()) {
              showToast("Playlist is empty")
              return@launch
            }

            Timber.d("Playing ${playlistItems.size} items from playlist: ${item.title}")

            // Play all playlist items
            playPlaylistItems(playlistItems)
          } catch (e: Exception) {
            Timber.e(e, "Error playing playlist items")
            showToast("Error loading playlist: ${e.message}")
          }
        }
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

  private fun showPlayListItems(item: FeedData.PlaylistItem) {
    fragment.lifecycleScope.launch {
      try {
        // Get playlist items
        val playlistId = item.id.toLongOrNull() ?: run {
          showToast("Invalid playlist ID")
          return@launch
        }
        val playlistItems = playlistRepository.getPlaylistItems(playlistId)
          .firstOrNull() ?: emptyList()

        if (playlistItems.isEmpty()) {
          showToast("Playlist is empty")
          return@launch
        }

            // Convert media URIs to display names
            val itemNames = playlistItems.map { playlistItem ->
              // Extract filename from URI or use a generic name
              playlistItem.mediaUri.substringAfterLast('/').let { name ->
                if (name.isEmpty()) "Unknown" else name.replace("%20", " ")
              }
            }

        // Show selection dialog with playlist items
        val dialog = SelectionDialog.multiple(
          items = itemNames,
          selectedIndex = emptyList(),
          name = item.title
        )

        // Show dialog with callback for selected items
        dialog.show(fragment.parentFragmentManager) { resultBundle ->
          if (resultBundle != null) {
            val selectedIndices =
              resultBundle.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED) ?: emptyList()
            if (selectedIndices.isNotEmpty()) {
              Timber.d("Selected ${selectedIndices.size} items from playlist: ${item.title}")
              showToast("Selected ${selectedIndices.size} items from playlist")

              // Get selected items and play them
              val selectedItems = selectedIndices.mapNotNull { index ->
                playlistItems.getOrNull(index)
              }

              if (selectedItems.isNotEmpty()) {
                // Play first selected item with others in queue
                playPlaylistItems(selectedItems)
              }
            }
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "Error loading playlist items")
        showToast("Error loading playlist items: ${e.message}")
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
                handleMediaItemAction(item, actionItems[index])
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
            handlePlaylistItemAction(item, actionItems[index])
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

          val isFav = favoriteRepository.isFavorite(item.id)
          val unifiedFile = fragment.context?.let {
            UnifiedFileFactory.fromPath(it, state?.task?.targetPath ?: "")
          }
          val actionItems = buildDownloadItemActions(status, unifiedFile)

          if (actionItems.isEmpty()) {
            showToast("No actions available")
            return@launch
          }

          withContext(Dispatchers.Main) {
            FeedActionDialog.newInstance(actionItems).show(
              fragment.parentFragmentManager
            ) { bundle ->
              bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
                handleDownloadItemAction(item, actionItems[index].id)
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
        id = (if (isFav) "add_to_favorite" else "remove_from_favorite"),
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
        title = fragment.getString(R.string.add_to_playlist),
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
        iconRes = R.drawable.outline_play_circle_24,
        isDestructive = false
      ),
      ActionItem(
        id = "show_items",
        title = fragment.getString(R.string.show_items),
        iconRes = R.drawable.outline_format_list_bulleted_24,
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
    unifiedFile: UnifiedFile? = null
  ): List<ActionItem> {
    return buildList {
      val isFolder = unifiedFile?.isDirectory == true
      val isFile = unifiedFile?.isFile == true
      // "play" - only if completed
      if (status == DownloadStatus.COMPLETED && isFile) {
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
        add(
          ActionItem(
            id = "stream",
            title = fragment.getString(R.string.stream),
            iconRes = R.drawable.play_to_pause,
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

      // "show files" - only if completed
      if (status == DownloadStatus.COMPLETED) {
        if (isFolder) {
          add(
            ActionItem(
              id = "show_files",
              title = fragment.getString(R.string.show_files),
              iconRes = R.drawable.outline_arrow_outward_24,
              isDestructive = false
            )
          )
          add(
            ActionItem(
              id = "add_to_playlist",
              title = fragment.getString(R.string.add_to_playlist),
              iconRes = androidx.media3.session.R.drawable.media3_icon_playlist_add,
              isDestructive = false
            )
          )
          add(
            ActionItem(
              id = "create_playlist_from_folder",
              title = fragment.getString(R.string.add_playlist),
              iconRes = R.drawable.ic_baseline_add_24,
              isDestructive = false
            )
          )
        }
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
    actionItem: ActionItem
  ) {
    when (actionItem.id) {
      "play" -> {
        playMediaItem(item)
      }

      "remove_from_favorite",
      "add_to_favorite" -> {
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

  private fun handlePlaylistItemAction(item: FeedData.PlaylistItem, actionItem: ActionItem) {
    when (actionItem.id) {
      "play" -> {
        onItemClick(item) // Reuse click action
      }

      "edit" -> {
        showToast("Edit playlist functionality coming soon")
      }

      "show_items" -> {
        showPlayListItems(item)
      }
      "share" -> {
        showToast("Share playlist functionality coming soon")
      }

      "delete" -> {
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
  ) {

    val context = fragment.context ?: run {
      Timber.e("Context is null in handleDownloadItemAction")
      return
    }
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
          //todo implement streaming of partial files
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

      "add_to_favorite",
      "remove_from_favorite" -> {
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

      "add_to_playlist" -> {
        fragment.lifecycleScope.launch {
          try {
            val state = downloadRepository.observeState(item.id).firstOrNull()
            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            val targetPath = state.task.targetPath
            val uniFile = UnifiedFileFactory.fromPath(context, targetPath)

            if (uniFile == null || !uniFile.exists()) {
              showToast("Download folder not found")
              Timber.w("add_to_playlist: Download folder not found - targetPath=$targetPath")
              return@launch
            }

            // Convert download files to MediaItems
            val mediaItems = mutableListOf<FeedData.MediaItem>()

            if (uniFile.isFile) {
              // Single file - create MediaItem
              val uri = uniFile.uri.toString()
              mediaItems.add(
                FeedData.MediaItem(
                  id = uri,
                  title = uniFile.name,
                  type = FeedData.Type.Audio,
                  media = Media(
                    id = uri.hashCode().toLong(),
                    uri = uri,
                    path = targetPath,
                    name = uniFile.name,
                    size = uniFile.length(),
                    duration = 0L,
                    width = 0,
                    height = 0,
                    dateModified = System.currentTimeMillis(),
                    mimeType = "",
                    position = 0L
                  )
                )
              )
            } else if (uniFile.isDirectory) {
              // Directory - collect all media files
              val videoExtensions = setOf(
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
                "mpg", "mpeg", "3gp", "ts", "m2ts", "vob", "ogv", "rmvb"
              )
              val audioExtensions = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "wma")

              fun collectMediaFiles(dir: UnifiedFile) {
                try {
                  dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                      collectMediaFiles(file)
                    } else if (file.isFile) {
                      val ext = file.name.substringAfterLast('.', "").lowercase()
                      val isMedia = ext in videoExtensions || ext in audioExtensions
                      if (isMedia) {
                        val uri = file.uri.toString()
                        val type = if (ext in audioExtensions) FeedData.Type.Audio else FeedData.Type.Video
                        mediaItems.add(
                          FeedData.MediaItem(
                            id = uri,
                            title = file.name,
                            type = type,
                    media = Media(
                      id = uri.hashCode().toLong(),
                      uri = uri,
                      path = file.filePath ?: uri,
                      name = file.name,
                      size = file.length(),
                      duration = 0L,
                      width = 0,
                      height = 0,
                      dateModified = System.currentTimeMillis(),
                      mimeType = "",
                      position = 0L
                    )
                          )
                        )
                      }
                    }
                  }
                } catch (e: Exception) {
                  Timber.w(e, "Error collecting media files from directory")
                }
              }

              collectMediaFiles(uniFile)
            }

            if (mediaItems.isEmpty()) {
              showToast("No media files found in download")
              Timber.w("add_to_playlist: No media files found in download - targetPath=$targetPath")
              return@launch
            }

            Timber.d("Converting ${mediaItems.size} download file(s) to MediaItems for playlist")
            showAddToPlaylistDialog(mediaItems)

          } catch (e: Exception) {
            Timber.e(e, "Error adding download to playlist")
            showToast("Failed to add to playlist: ${e.message}")
          }
        }
      }
      "create_playlist_from_folder" -> {
        fragment.lifecycleScope.launch {
          try {

            val state = downloadRepository.observeState(item.id).firstOrNull()
            if (state == null) {
              showToast("Download not found")
              return@launch
            }

            val targetPath = state.task.targetPath

            playlistRepository.createPlaylistFromFolder(fragment.requireContext(), item.title, targetPath)
            showToast("Playlist created from folder")
          } catch (e: Exception) {
            Timber.e(e, "Error creating playlist from folder")
            showToast("Failed to create playlist: ${e.message}")
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

            // targetPath can be either a file system path or a URI
            val targetPath = state.task.targetPath

            // Use fromPath to automatically handle both URIs and file paths
            val dir = UnifiedFileFactory.fromPath(context, targetPath)

            if (dir == null || !dir.exists()) {
              showToast("Download folder not found")
              Timber.e("show_files: Failed to open or path doesn't exist - targetPath=$targetPath")
              return@launch
            }

            val allFiles = mutableListOf<UnifiedFile>()
            fun collectFiles(directory: UnifiedFile) {
              directory.listFiles()?.forEach { file: UnifiedFile ->
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
              ActionItem(
                id = index.toString(),
                title = "${file.name} ($size)",
                iconRes = getFileIconRes(file),
                isDestructive = false
              )
            }

            FeedActionDialog.newInstance(fileActionItems, R.string.select_files).show(
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

  fun getFileIconRes(file: UnifiedFile): Int {
    return when {
      isVideoFile(file) -> R.drawable.outline_play_circle_24
      isAudioFile(file) -> R.drawable.outline_music_note_24
      else -> R.drawable.ic_file
    }
  }

  // Helper function to play a specific file
  fun playFile(file: UnifiedFile, item: FeedData) {
    fragment.lifecycleScope.launch {
      try {
        if (!file.exists()) {
          showToast("File not found: ${file.name}")
          return@launch
        }

        Timber.d("Playing selected file: ${file.uri.path}")

        // Scan file into MediaStore to ensure it can be tracked in history
        // This is done asynchronously and won't block playback
        withContext(Dispatchers.IO) {
          try {
            val intent =
              android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = file.uri
            fragment.requireContext().sendBroadcast(intent)
            Timber.d("Requested MediaStore scan for: ${file.uri}")
          } catch (e: Exception) {
            Timber.w(e, "Failed to scan file into MediaStore")
          }
        }

        val url = file.uri.toString()

        // Create VideoLink from file
        val videoLink = VideoLink(
          url = url,
          name = file.name,
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
            else -> file.name
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

  fun showListFile(item: FeedData, state: DownloadState) {
    val context = fragment.context ?: run {
      showToast("Context not available")
      return
    }

    // targetPath can be either a file system path or a URI
    val targetPath = state.task.targetPath

    // Use fromPath to automatically handle both URIs and file paths
    val uniFile = UnifiedFileFactory.fromPath(context, targetPath)

    if (uniFile == null || !uniFile.exists()) {
      showToast(context.getString(R.string.downloaded_file_not_found))
      Timber.e("playDownloadedFile: Failed to open or path doesn't exist - targetPath=$targetPath")
      return
    }

    if (!uniFile.isDirectory) {
      showToast("Downloaded path is not a directory")
      return
    }

    val allFiles = mutableListOf<UnifiedFile>()
    fun collectFiles(directory: UnifiedFile) {
      directory.listFiles()?.forEach { file: UnifiedFile ->
        when {
          file.isDirectory -> collectFiles(file)
          file.isFile -> allFiles.add(file)
        }
      }
    }
    collectFiles(uniFile)
    val sortedFiles = allFiles.sortedByDescending { it.length() }
    val fileActionItems = sortedFiles.mapIndexed { index, file ->
      val size = formatFileSize(file.length())
      ActionItem(
        id = index.toString(),
        title = "${file.name} ($size)",
        iconRes = getFileIconRes(file),
        isDestructive = false
      )
    }

    FeedActionDialog.newInstance(fileActionItems, R.string.select_files).show(
      fragment.parentFragmentManager
    ) { bundle ->
      bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)
        ?.let { selectedIndex ->
          playFile(sortedFiles[selectedIndex], item)
        }
    }
  }

  // Helper function to play completed download
  fun playDownloadedFile(item: FeedData, state: cloud.app.csplayer.download.DownloadState) {
    try {
      val context = fragment.context ?: run {
        showToast("Context not available")
        return
      }

      // targetPath can be either a file system path or a URI
      val targetPath = state.task.targetPath

      // Use fromPath to automatically handle both URIs and file paths
      val uniFile = UnifiedFileFactory.fromPath(context, targetPath)

      if (uniFile == null || !uniFile.exists()) {
        showToast(context.getString(R.string.downloaded_file_not_found))
        Timber.e("playDownloadedFile: Failed to open or path doesn't exist - targetPath=$targetPath")
        return
      }


      // Check if it's a file or directory
      if (uniFile.isFile) {
        // Single file - play directly
        playFile(uniFile, item)
      } else if (uniFile.isDirectory) {
        // Directory - show file selection dialog
        showListFile(item, state)
      } else {
        showToast("Invalid download path")
        Timber.e("playDownloadedFile: Path is neither file nor directory - isFile=${uniFile.isFile}, isDirectory=${uniFile.isDirectory}")
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
    showAddToPlaylistDialog(listOf(item))
  }

  /**
   * Show dialog to add one or multiple media items to a playlist
   * @param items List of media items to add to playlist
   */
  private fun showAddToPlaylistDialog(items: List<FeedData.MediaItem>) {
    if (items.isEmpty()) {
      Timber.w("No items to add to playlist")
      showToast("No items selected")
      return
    }

    fragment.lifecycleScope.launch {
      try {
        Timber.d("Loading playlists for add to playlist dialog (${items.size} item(s))")

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
            bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)
              ?.let { selectedIndex ->
                Timber.d("User selected playlist at index: $selectedIndex")
                val selectedPlaylist = playlists[selectedIndex]
                Timber.d("Selected playlist: ${selectedPlaylist.name}")
                addMediaToPlaylist(items, selectedPlaylist.id)
              }
          }
        }

      } catch (e: Exception) {
        Timber.e(e, "Error showing playlist dialog")
        showToast("Error: ${e.message}")
      }
    }
  }

  private fun playPlaylistItems(items: List<cloud.app.csplayer.media.entities.PlaylistItemEntity>) {
    fragment.viewLifecycleOwner.lifecycleScope.launch {
      try {
        if (items.isEmpty()) {
          showToast("No items to play")
          return@launch
        }

        // Convert playlist items to VideoLinks
        val videoLinks = items.mapIndexed { index, item ->
          VideoLink(
            url = item.mediaUri,
            name = item.mediaUri.substringAfterLast('/').let { filename ->
              if (filename.isEmpty()) {
                "Item ${index + 1}"
              } else {
                // Decode URL-encoded filenames (e.g., %20 to space)
                java.net.URLDecoder.decode(filename, "UTF-8")
              }
            },
            headers = emptyMap(),
            subtitles = emptyList(),
            position = 0,
            width = 0,
            height = 0
          )
        }

        // Create PlaybackData with all items
        val playbackData = PlaybackData(
          title = videoLinks.firstOrNull()?.name ?: "Playlist",
          videoLinks = videoLinks,
          subtitles = emptyList(),
          videoStartIndex = 0,
          subtitleStartIndex = 0,
          isSameEpisode = true,
          hasAd = false
        )

        Timber.d("Playing ${items.size} playlist items")

        // Navigate to MPV player with PlaybackData
        val bundle = PlaybackDataHelper.createBundle(playbackData)
        fragment.requireActivity().navigate(R.id.global_to_navigation_mpv_player, bundle)
      } catch (e: Exception) {
        Timber.e(e, "Error playing playlist items")
        showToast("Failed to play playlist: ${e.message}")
      }
    }
  }

  @Suppress("UNUSED")
  private fun addMediaToPlaylist(item: FeedData.MediaItem, playlistId: Long) {
    addMediaToPlaylist(listOf(item), playlistId)
  }

  /**
   * Add one or multiple media items to a playlist
   * @param items List of media items to add
   * @param playlistId ID of the target playlist
   */
  private fun addMediaToPlaylist(items: List<FeedData.MediaItem>, playlistId: Long) {
    fragment.lifecycleScope.launch {
      try {
        Timber.d("Adding ${items.size} media item(s) to playlist - playlistId: $playlistId")

        val successCount = withContext(Dispatchers.IO) {
          var count = 0
          for (item in items) {
            try {
              val mediaUri = item.media.uri
              Timber.d("Adding media to playlist - playlistId: $playlistId, mediaUri: $mediaUri")
              playlistRepository.addMediaToPlaylist(playlistId, mediaUri)
              count++
            } catch (e: Exception) {
              Timber.w(e, "Failed to add individual media item to playlist")
            }
          }
          count
        }

        showToast("Added $successCount of ${items.size} item(s) to playlist")
        Timber.d("Successfully added $successCount media item(s) to playlist")

      } catch (e: Exception) {
        Timber.e(e, "Error adding media to playlist")
        showToast("Failed to add to playlist: ${e.message}")
      }
    }
  }
}
