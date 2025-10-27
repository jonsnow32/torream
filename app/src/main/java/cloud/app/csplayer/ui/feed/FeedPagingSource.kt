package cloud.app.csplayer.ui.feed

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.ui.filesystem.FileTreePreferences
import timber.log.Timber

/**
 * PagingSource that loads folders from FileTreePreferences.
 * Shows folders that user has selected via SAF (Storage Access Framework) or All Files Access.
 *
 * @param context Android context
 * @param rootFolderPath Optional root folder path. If provided, only shows files from this folder.
 *                       If null, shows all folders from FileTreePreferences.
 */
class FeedPagingSource(
  private val context: Context,
  private val rootFolderPath: String? = null
) : PagingSource<Int, FeedData>() {

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedData> {
    return try {
      val page = params.key ?: 0
      val pageSize = params.loadSize

      Timber.d("Loading page $page with pageSize $pageSize, rootFolder: $rootFolderPath")

      // Load folders or files based on rootFolderPath
      val allData = if (rootFolderPath != null) {
        // Load files from specific root folder
        loadFilesFromRootFolder(rootFolderPath)
      } else {
        // Load all folders from user selections in FileTreePreferences
        loadFoldersFromPreferences()
      }

      val startIndex = page * pageSize
      val endIndex = minOf(startIndex + pageSize, allData.size)

      Timber.d("Total items: ${allData.size}, startIndex: $startIndex, endIndex: $endIndex")

      if (startIndex >= allData.size) {
        // No more data
        LoadResult.Page(
          data = emptyList(),
          prevKey = if (page > 0) page - 1 else null,
          nextKey = null
        )
      } else {
        val data = allData.subList(startIndex, endIndex)
        LoadResult.Page(
          data = data,
          prevKey = if (page > 0) page - 1 else null,
          nextKey = if (endIndex < allData.size) page + 1 else null
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading feed data")
      LoadResult.Error(e)
    }
  }

  override fun getRefreshKey(state: PagingState<Int, FeedData>): Int? {
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }

  /**
   * Load folders from FileTreePreferences (user-selected folders)
   */
  private fun loadFoldersFromPreferences(): List<FeedData.FolderItem> {
    val folderItems = mutableListOf<FeedData.FolderItem>()

    try {
      Timber.d("Loading folders from FileTreePreferences...")

      // Get user-selected folders from preferences
      val selectedFolders = FileTreePreferences.loadSelectedFolders(context)

      Timber.d("Found ${selectedFolders.size} selected folders")

      // Convert FileTreeNode to FeedData.FolderItem
      selectedFolders.forEach { node ->
        try {
          // Count media files in this folder
          val mediaCount = countMediaFiles(node)

          val path = node.file.filePath ?: node.file.uri.toString()

          folderItems.add(
            FeedData.FolderItem(
              id = path,
              title = node.name,
              folder = Folder(
                id = path,
                title = node.name,
                path = path,
                subtitle = "$mediaCount items"
              ),
              type = FeedData.Type.FolderSmall
            )
          )

          Timber.d("Added folder: ${node.name} with $mediaCount items")
        } catch (e: Exception) {
          Timber.w(e, "Error processing folder: ${node.name}")
        }
      }

      Timber.d("Successfully loaded ${folderItems.size} folders")
    } catch (e: Exception) {
      Timber.e(e, "Error loading folders from preferences")
    }

    return folderItems
  }

  /**
   * Load video/audio files from a specific root folder
   */
  private fun loadFilesFromRootFolder(rootPath: String): List<FeedData> {
    val items = mutableListOf<FeedData>()

    try {
      Timber.d("Loading files from root folder: $rootPath")

      // Find the folder node from preferences
      val selectedFolders = FileTreePreferences.loadSelectedFolders(context)
      val rootNode = selectedFolders.find {
        val nodePath = it.file.filePath ?: it.file.uri.toString()
        nodePath == rootPath
      }

      if (rootNode == null) {
        Timber.w("Root folder not found in preferences: $rootPath")
        return emptyList()
      }

      // List all media files in this folder
      val files = rootNode.file.listFiles() ?: emptyArray()

      files.forEach { file ->
        try {
          if (file.isFile && isMediaFile(file.name ?: "")) {
            val filePath = file.filePath ?: file.uri.toString()
            val fileName = file.name ?: "Unknown"

            // Create Video object
            val video = cloud.app.csplayer.model.Video(
              id = filePath,
              title = fileName,
              subtitle = formatFileSize(file.length()),
              cover = "" // No cover for now
            )

            items.add(
              FeedData.VideoItem(
                id = filePath,
                title = fileName,
                video = video
              )
            )

            Timber.d("Added file: ${file.name}")
          } else if (file.isDirectory) {
            // Add subdirectories as folders
            val subMediaCount = countMediaFilesInDirectory(file)
            if (subMediaCount > 0) {
              val subPath = file.filePath ?: file.uri.toString()

              items.add(
                FeedData.FolderItem(
                  id = subPath,
                  title = file.name ?: "Unknown",
                  folder = Folder(
                    id = subPath,
                    title = file.name ?: "Unknown",
                    path = subPath,
                    subtitle = "$subMediaCount items"
                  ),
                  type = FeedData.Type.FolderSmall
                )
              )
            }
          }
        } catch (e: Exception) {
          Timber.w(e, "Error processing file: ${file.name}")
        }
      }

      Timber.d("Successfully loaded ${items.size} items from root folder")
    } catch (e: Exception) {
      Timber.e(e, "Error loading files from root folder")
    }

    return items
  }

  /**
   * Count media files in a directory (non-recursive, immediate children only)
   */
  private fun countMediaFilesInDirectory(file: cloud.app.csplayer.utils.KUniFile): Int {
    try {
      val files = file.listFiles() ?: return 0
      var count = 0

      files.forEach { subFile ->
        if (subFile.isFile && isMediaFile(subFile.name ?: "")) {
          count++
        }
      }

      return count
    } catch (e: Exception) {
      Timber.w(e, "Error counting files in directory: ${file.name}")
      return 0
    }
  }

  /**
   * Count media files in a folder (recursively)
   */
  private fun countMediaFiles(node: cloud.app.csplayer.ui.filesystem.FileTreeNode): Int {
    try {
      val files = node.file.listFiles() ?: return 0
      var count = 0

      files.forEach { file ->
        if (file.isFile && isMediaFile(file.name ?: "")) {
          count++
        } else if (file.isDirectory) {
          // Recursively count files in subdirectories
          try {
            val subFiles = file.listFiles()
            subFiles?.forEach { subFile ->
              if (subFile.isFile && isMediaFile(subFile.name ?: "")) {
                count++
              }
            }
          } catch (e: Exception) {
            Timber.w(e, "Error counting files in subdirectory: ${file.name}")
          }
        }
      }

      return count
    } catch (e: Exception) {
      Timber.w(e, "Error counting media files in folder: ${node.name}")
      return 0
    }
  }

  /**
   * Check if file is a media file based on extension
   */
  private fun isMediaFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in setOf(
      // Video formats
      "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg",
      // Audio formats
      "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape"
    )
  }

  /**
   * Format file size to human-readable string
   */
  private fun formatFileSize(bytes: Long): String {
    return when {
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "${bytes / 1024} KB"
      bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
      else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
  }
}

