package cloud.app.csplayer.ui.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import cloud.app.csplayer.media.repository.MediaRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * PagingSource that loads media and folders from MediaRepository (Room database).
 * Shows all media files indexed from MediaStore.
 *
 * @param repository MediaRepository for accessing media and folder data
 * @param rootFolderPath Optional root folder path. If provided, only shows media from this folder.
 *                       If null, shows all folders.
 */
class FeedPagingSource(
  private val repository: MediaRepository,
  private val rootFolderPath: String? = null
) : PagingSource<Int, FeedData>() {

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedData> {
    return try {
      val page = params.key ?: 0
      val pageSize = params.loadSize

      Timber.d("Loading page $page with pageSize $pageSize, rootFolder: $rootFolderPath")

      // Load media or folders based on rootFolderPath
      val allData = if (rootFolderPath != null) {
        // Load media from specific folder
        loadMediaFromFolder(rootFolderPath)
      } else {
        // Load all folders
        loadAllFolders()
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
   * Load all folders from MediaRepository
   * @throws Exception if no folders found or error loading data
   */
  private suspend fun loadAllFolders(): List<FeedData.FolderItem> {
    Timber.d("Loading all folders from MediaRepository...")

    val folders = repository.observeFolders().first()

    Timber.d("Found ${folders.size} folders")

    // Throw exception if no folders found - this will trigger error UI
    if (folders.isEmpty()) {
      throw NoFoldersFoundException("No media folders found. Please scan your device for media files.")
    }

    // Convert Folder to FeedData.FolderItem
    return folders.map { folder ->
      FeedData.FolderItem(
        id = folder.path,
        title = folder.name,
        folder = folder,
        type = FeedData.Type.FolderSmall
      )
    }.also {
      Timber.d("Successfully loaded ${it.size} folders")
    }
  }

  /**
   * Custom exception for when no folders are found
   */
  class NoFoldersFoundException(message: String) : Exception(message)

  /**
   * Load media files from a specific folder
   */
  private suspend fun loadMediaFromFolder(folderPath: String): List<FeedData> {
    return try {
      Timber.d("Loading media from folder: $folderPath")

      val mediaList = repository.getMediaByFolder(folderPath)

      Timber.d("Found ${mediaList.size} media files in folder")

      // Convert Media to FeedData.MediaItem
      val items = mutableListOf<FeedData>()

      mediaList.forEach { media ->
        val mediaType = determineMediaType(media.mimeType)

        items.add(
          FeedData.MediaItem(
            id = media.uri,
            title = media.name,
            type = mediaType,
            media = media
          )
        )
      }

      // Also load subfolders in this folder
      val subfolders = repository.observeFolders().first()
        .filter { it.parentPath == folderPath }

      subfolders.forEach { folder ->
        items.add(
          FeedData.FolderItem(
            id = folder.path,
            title = folder.name,
            folder = folder,
            type = FeedData.Type.FolderSmall
          )
        )
      }

      Timber.d("Successfully loaded ${items.size} items from folder")
      items
    } catch (e: Exception) {
      Timber.e(e, "Error loading media from folder")
      emptyList()
    }
  }

  /**
   * Determine media type based on MIME type
   */
  private fun determineMediaType(mimeType: String): FeedData.Type {
    return when {
      mimeType.startsWith("video/") -> FeedData.Type.VideoSmall
      mimeType.startsWith("audio/") -> FeedData.Type.AudioSmall
      else -> FeedData.Type.VideoSmall // Default to video
    }
  }
}

