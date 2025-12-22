package cloud.app.csplayer.ui.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import cloud.app.csplayer.model.MediaTypeFilter
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.PlaylistRepository
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedFilterConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * PagingSource that loads media and folders from MediaRepository (Room database).
 * Shows all media files indexed from MediaStore.
 *
 * @param repository MediaRepository for accessing media and folder data
 * @param playlistRepository PlaylistRepository for accessing playlist data
 * @param rootFolderPath Optional root folder path. If provided, only shows media from this folder.
 *                       If null, shows all folders.
 * @param viewMode Display type that determines how items are shown (GRID or LIST)
 * @param groupMode Folder view mode (CAROUSEL, FOLDERS or TREE)
 * @param mediaTypeFilter Media type filter (ALL, VIDEO, or AUDIO)
 * @param searchQuery Optional search query to filter items by name
 */
class FeedPagingSource(
  private val repository: MediaRepository,
  private val playlistRepository: PlaylistRepository? = null,
  private val rootFolderPath: String? = null,
  private val viewMode: FeedFilterConfig.ViewMode = FeedFilterConfig.ViewMode.GRID,
  private val groupMode: FeedFilterConfig.GroupMode = FeedFilterConfig.GroupMode.FOLDERS,
  private val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
  private val sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
  private val sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING,
  private val searchQuery: String? = null
) : PagingSource<Int, FeedData>() {

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedData> {
    return try {
      val page = params.key ?: 0
      val pageSize = params.loadSize
      Timber.d("Loading page $page with pageSize $pageSize, rootFolder: $rootFolderPath, groupMode: $groupMode")

      // Always trigger sync on first page to ensure fresh data and permission check
      // This will throw MediaPermissionException if permission is revoked
      if (page == 0) {
        Timber.d("First page load - checking database state")
        val hasData = try {
          val folders = repository.observeFolders().first()
          val media = repository.observeMedia().first()
          Timber.d("Database check: ${folders.size} folders, ${media.size} media items")
          folders.isNotEmpty() || media.isNotEmpty()
        } catch (e: Exception) {
          Timber.e(e, "Error checking database")
          false
        }

        // If database is empty, definitely need to sync (will throw if no permission)
        if (!hasData) {
          Timber.w("Database is empty, triggering sync - will throw if no permission")
          repository.refreshMedia()
        }
        // Note: If database has data, we'll still try to load from it
        // but syncState observer will show permission error if sync fails in background
      }

      // Load data based on groupMode and rootFolderPath
      val data = if(searchQuery.isNullOrEmpty())when (groupMode) {
        FeedFilterConfig.GroupMode.CAROUSEL -> loadAllMediaPaged(pageSize, page * pageSize)
        else -> {
          when (rootFolderPath) {
            null -> loadAllFoldersPaged(pageSize, page * pageSize)
            else -> loadMediaFromFolderPaged(rootFolderPath, pageSize, page * pageSize)
          }
        }
      } else {
        search(pageSize, page * pageSize, searchQuery)
      }

      LoadResult.Page(
        data = data,
        prevKey = if (page > 0) page - 1 else null,
        nextKey = if (data.size == pageSize) page + 1 else null
      )
    } catch (e: CancellationException) {
      Timber.d("Load cancelled for page ${params.key}")
      throw e // Re-throw to let Paging handle it properly
    } catch (e: Exception) {
      Timber.e(e, "Error loading feed data")
      LoadResult.Error(e)
    }
  }

  private suspend fun search(
    limit: Int,
    offset: Int,
    searchQuery: String
  ): List<FeedData> {
    Timber.d("Searching MediaStore with query='$searchQuery', limit=$limit, offset=$offset, sortBy=$sortBy, sortOrder=$sortOrder")

    // Search directly from MediaStore using the repository
    // Let exceptions propagate to outer catch block for proper error handling
    val mediaList = repository.search(searchQuery, limit, offset, sortBy, sortOrder)

    Timber.d("Found ${mediaList.size} media items from MediaStore search")

    // Convert Media to FeedData.MediaItem
    return mediaList.map { media ->
      val mediaType = determineMediaType(media.mimeType)

      FeedData.MediaItem(
        id = media.uri,
        title = media.name,
        type = mediaType,
        media = media
      ) as FeedData
    }.also {
      Timber.d("Successfully loaded ${it.size} media items from search")
    }
  }

  override fun getRefreshKey(state: PagingState<Int, FeedData>): Int? {
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }

  /**
   * Load all folders from MediaRepository with pagination
   */
  private suspend fun loadAllFoldersPaged(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading folders with limit=$limit, offset=$offset, groupMode=$groupMode, mediaTypeFilter=$mediaTypeFilter, sortBy=$sortBy, sortOrder=$sortOrder")

    val folders = repository.getFoldersPaged(limit, offset, sortBy, sortOrder)

    Timber.d("Found ${folders.size} folders")


    // Determine folder type based on displayType
    val folderType = when (viewMode) {
      FeedFilterConfig.ViewMode.GRID -> FeedData.Type.FolderSmall
      FeedFilterConfig.ViewMode.LIST -> FeedData.Type.Folder
    }

    // Convert Folder to FeedData.FolderItem
    return folders.map { folder ->
      FeedData.FolderItem(
        id = folder.path,
        title = folder.name,
        folder = folder,
        type = folderType
      ) as FeedData
    }.also {
      Timber.d("Successfully loaded ${it.size} folders")
    }

  }

  /**
   * Load all playlists with pagination
   */
  private suspend fun loadPlaylistsPaged(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading playlists with limit=$limit, offset=$offset")

    if (playlistRepository == null) {
      Timber.w("PlaylistRepository not available, returning empty list")
      return emptyList()
    }

    return try {
      // Get playlists from repository using pagination
      val allPlaylists = playlistRepository.getPlayLists(limit, offset)
      Timber.d("Loaded ${allPlaylists.size} playlists from repository")

      // Determine playlist type based on viewMode
      val playlistType = when (viewMode) {
        FeedFilterConfig.ViewMode.GRID -> FeedData.Type.PlayListSmall
        FeedFilterConfig.ViewMode.LIST -> FeedData.Type.PlayList
      }

      // Convert to FeedData.PlaylistItem
      val feedItems = allPlaylists.mapNotNull { playlist ->
        try {
          FeedData.PlaylistItem(
            id = playlist.id.toString(),
            title = playlist.name,
            description = playlist.description,
            itemCount = playlist.itemCount,
            type = playlistType,
            thumbnailPath = playlist.thumbnailPath,
            createdAt = playlist.createdAt,
            updatedAt = playlist.updatedAt
          ) as FeedData
        } catch (e: Exception) {
          Timber.e(e, "Error creating playlist item for playlist: ${playlist.id}")
          null
        }
      }

      Timber.d("Converted ${feedItems.size} playlists to FeedData items")
      feedItems
    } catch (e: Exception) {
      Timber.e(e, "Error loading playlists")
      emptyList()
    }
  }

  /**
   * Load all media files (for CAROUSEL mode) with pagination
   * Does not show folders, only media files
   */
  private suspend fun loadAllMediaPaged(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading all media with limit=$limit, offset=$offset, mediaTypeFilter=$mediaTypeFilter, sortBy=$sortBy, sortOrder=$sortOrder")

    // Load media files with filtering
    // Let exceptions propagate to outer catch block
    val mediaList = if (mediaTypeFilter != MediaTypeFilter.ALL) {
      repository.getAllMediaPagedFiltered(limit, offset, mediaTypeFilter, sortBy, sortOrder)
    } else {
      repository.getAllMediaPaged(limit, offset, sortBy, sortOrder)
    }

    Timber.d("Loaded ${mediaList.size} media files (filter: $mediaTypeFilter)")

    // Convert Media to FeedData.MediaItem
    return mediaList.map { media ->
      val mediaType = determineMediaType(media.mimeType)

      FeedData.MediaItem(
        id = media.uri,
        title = media.name,
        type = mediaType,
        media = media
      ) as FeedData
    }.also {
      Timber.d("Successfully loaded ${it.size} media items")
    }
  }

  /**
   * Load media files from a specific folder with pagination
   * Also loads subfolders at the beginning of the list (only in TREE mode)
   */
  private suspend fun loadMediaFromFolderPaged(
    folderPath: String,
    limit: Int,
    offset: Int
  ): List<FeedData> {
    Timber.d("Loading content from folder: $folderPath with limit=$limit, offset=$offset, groupMode=$groupMode")

    val items = mutableListOf<FeedData>()

    // For first page, load subfolders first (only in TREE mode for hierarchical structure)
    // FOLDERS and CAROUSEL modes don't show subfolders within media list
    if (offset == 0 && groupMode == FeedFilterConfig.GroupMode.FOLDERS) {
      val subfolders = repository.getSubfoldersPaged(folderPath, limit = 100, offset = 0)

      // Determine folder type based on displayType
      val folderType = when (viewMode) {
        FeedFilterConfig.ViewMode.GRID -> FeedData.Type.FolderSmall
        FeedFilterConfig.ViewMode.LIST -> FeedData.Type.Folder
      }

      subfolders.forEach { folder ->
        items.add(
          FeedData.FolderItem(
            id = folder.path,
            title = folder.name,
            folder = folder,
            type = folderType
          )
        )
      }

      Timber.d("Loaded ${subfolders.size} subfolders")
    }

    // Load media files with adjusted pagination
    val adjustedOffset = maxOf(0, offset - if (offset == 0) 0 else 0) // Simple for now
    val mediaList = if (mediaTypeFilter != MediaTypeFilter.ALL) {
      // Use filtered query when a specific media type is selected
      repository.getMediaByFolderPagedFiltered(folderPath, limit, adjustedOffset, mediaTypeFilter, sortBy, sortOrder)
    } else {
      // Use standard query for ALL to avoid unnecessary filtering
      repository.getMediaByFolderPaged(folderPath, limit, adjustedOffset, sortBy, sortOrder)
    }

    Timber.d("Loaded ${mediaList.size} media files (filter: $mediaTypeFilter)")

    // Convert Media to FeedData.MediaItem
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

    Timber.d("Successfully loaded ${items.size} total items from folder")
    return items
  }

  /**
   * Load only media files from a specific folder (no subfolders)
   * Used in CAROUSEL mode when viewing a specific folder
   */
  private suspend fun loadMediaOnlyFromFolderPaged(
    folderPath: String,
    limit: Int,
    offset: Int
  ): List<FeedData.MediaItem> {
    return try {
      Timber.d("Loading media only from folder: $folderPath with limit=$limit, offset=$offset, mediaTypeFilter=$mediaTypeFilter")

      // Load media files with filtering
      val mediaList = if (mediaTypeFilter != MediaTypeFilter.ALL) {
        repository.getMediaByFolderPagedFiltered(folderPath, limit, offset, mediaTypeFilter)
      } else {
        repository.getMediaByFolderPaged(folderPath, limit, offset)
      }

      Timber.d("Loaded ${mediaList.size} media files (filter: $mediaTypeFilter)")

      // Convert Media to FeedData.MediaItem
      mediaList.map { media ->
        val mediaType = determineMediaType(media.mimeType)

        FeedData.MediaItem(
          id = media.uri,
          title = media.name,
          type = mediaType,
          media = media
        )
      }.also {
        Timber.d("Successfully loaded ${it.size} media items from folder")
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading media from folder")
      emptyList()
    }
  }

  /**
   * Load all folders from MediaRepository
   * @deprecated Use loadAllFoldersPaged instead
   */
  @Deprecated("Use loadAllFoldersPaged for better performance")
  private suspend fun loadAllFolders(): List<FeedData.FolderItem> {
    Timber.d("Loading all folders from MediaRepository...")

    val folders = repository.observeFolders().first()

    Timber.d("Found ${folders.size} folders")


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
   * Load media files from a specific folder
   * @deprecated Use loadMediaFromFolderPaged for better performance
   */
  @Deprecated("Use loadMediaFromFolderPaged for better performance")
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
   * Determine media type based on MIME type and displayType
   */
  private fun determineMediaType(mimeType: String): FeedData.Type {
    return when (viewMode) {
      FeedFilterConfig.ViewMode.GRID -> {
        when {
          mimeType.startsWith("video/") -> FeedData.Type.VideoSmall
          mimeType.startsWith("audio/") -> FeedData.Type.AudioSmall
          else -> FeedData.Type.VideoSmall // Default to video
        }
      }

      FeedFilterConfig.ViewMode.LIST -> {
        when {
          mimeType.startsWith("video/") -> FeedData.Type.Video
          mimeType.startsWith("audio/") -> FeedData.Type.Audio
          else -> FeedData.Type.Video // Default to video
        }
      }
    }
  }
}
