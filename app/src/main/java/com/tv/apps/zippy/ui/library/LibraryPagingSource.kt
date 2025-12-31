package com.tv.apps.zippy.ui.library

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tv.apps.zippy.download.DownloadRepository
import com.tv.apps.zippy.download.DownloadState
import com.tv.apps.zippy.download.DownloadType
import com.tv.apps.zippy.download.DownloadStatus
import com.tv.apps.zippy.media.repository.MediaRepository
import com.tv.apps.zippy.media.repository.PlaylistRepository
import com.tv.apps.zippy.ui.feed.FeedData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * PagingSource for Library sections: Downloads, Favorites, History, Playlists
 */

enum class LibrarySection {
  DOWNLOADS,
  FAVORITES,
  HISTORY,
  PLAYLISTS
}

class LibraryPagingSource(
  private val repository: MediaRepository,
  private val downloadRepository: DownloadRepository,
  private val favoriteRepository: com.tv.apps.zippy.favorites.FavoriteRepository,
  private val playlistRepository: PlaylistRepository,
  private val section: LibrarySection
) : PagingSource<Int, FeedData>() {

  override fun getRefreshKey(state: PagingState<Int, FeedData>): Int? {
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedData> {
    return try {
      val page = params.key ?: 0
      val pageSize = params.loadSize
      Timber.d("Loading $section page $page with pageSize $pageSize")

      val data = when (section) {
        LibrarySection.DOWNLOADS -> loadDownloads(pageSize, page * pageSize)
        LibrarySection.FAVORITES -> loadFavorites(pageSize, page * pageSize)
        LibrarySection.HISTORY -> loadHistory(pageSize, page * pageSize)
        LibrarySection.PLAYLISTS -> loadPlaylists(pageSize, page * pageSize)
      }

      LoadResult.Page(
        data = data,
        prevKey = if (page > 0) page - 1 else null,
        nextKey = if (data.size == pageSize) page + 1 else null
      )
    } catch (e: CancellationException) {
      Timber.d("Load cancelled for page ${params.key}")
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Error loading library data for section $section")
      LoadResult.Error(e)
    }
  }

  /**
   * Load downloads (torrent downloads)
   */
  private suspend fun loadDownloads(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading downloads with limit=$limit, offset=$offset using downloadRepository")

    return try {
      // Get snapshot of all tasks and current states
      val allTasks = downloadRepository.loadAllTask()
      val allStates = try {
        downloadRepository.observeAllStates().first()
      } catch (_: Exception) {
        emptyList()
      }

      val stateById = allStates.associateBy { it.task.id }

      // sort newest first
      val sorted = allTasks.sortedByDescending { it.createdAt }

      // apply pagination
      val start = offset.coerceAtMost(sorted.size)
      val end = (offset + limit).coerceAtMost(sorted.size)
      if (start >= end) return emptyList()

      val page = sorted.subList(start, end)

      // map to FeedData
      val items = page.map { task ->
        when (task.type) {
          DownloadType.HTTP -> {
            val state = stateById[task.id]
            val fileName = task.fileName?.substringAfterLast('/')
              ?: task.targetPath.substringAfterLast('/', task.source.substringAfterLast('/'))

            if (state != null) {
              FeedData.HttpDownloadItem(
                id = task.id,
                title = fileName,
                downloadState = state
              ) as FeedData
            } else {
              // Fallback if state is not found
              FeedData.HttpDownloadItem(
                id = task.id,
                title = fileName,
                downloadState = DownloadState(
                  task = task,
                  status = DownloadStatus.QUEUED
                )
              ) as FeedData
            }
          }

          DownloadType.TORRENT -> {
            val state = stateById[task.id]
            val title = state?.task?.fileName?.substringAfterLast('/')
              ?: task.source.substringAfterLast('/')

            if (state != null) {
              FeedData.TorrentDownloadItem(
                id = task.id,
                title = title,
                downloadState = state
              ) as FeedData
            } else {
              // Fallback if state is not found
              FeedData.TorrentDownloadItem(
                id = task.id,
                title = title,
                downloadState = DownloadState(
                  task = task,
                  status = DownloadStatus.QUEUED
                )
              ) as FeedData
            }
          }
        }
      }

      Timber.d("Loaded ${items.size} download items from downloadRepository")
      items
    } catch (e: Exception) {
      Timber.e(e, "Error loading downloads from downloadRepository")
      emptyList()
    }
  }

  /**
   * Load favorite items from FavoriteRepository
   */
  private suspend fun loadFavorites(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading favorites with limit=$limit, offset=$offset")

    return try {
      // Get all favorites from repository
      val allFavorites = favoriteRepository.observeAllFavorites().first()

      Timber.d("Loaded ${allFavorites.size} total favorites from repository")

      // Apply pagination
      val start = offset.coerceAtMost(allFavorites.size)
      val end = (offset + limit).coerceAtMost(allFavorites.size)

      if (start >= end) {
        Timber.d("No favorites in this page (start=$start, end=$end)")
        return emptyList()
      }

      val pageFavorites = allFavorites.subList(start, end)

      // Convert to FeedData based on type
      val feedItems = pageFavorites.mapNotNull { favorite ->
        when (favorite.type) {
          "media" -> {
            // Create MediaItem from favorite info
            try {
              val uri = favorite.uri ?: return@mapNotNull null

              // Load media metadata from database by URI
              val mediaFromDb = try {
                repository.getMediaByUri(uri)
              } catch (e: Exception) {
                Timber.w(e, "Could not load media from DB for URI: $uri")
                null
              }

              // Use media from DB if available, otherwise create minimal object
              val media = mediaFromDb ?: com.tv.apps.zippy.model.Media(
                id = uri.hashCode().toLong(), // Generate ID from URI hash
                uri = uri,
                path = uri,
                name = favorite.title,
                size = mediaFromDb?.size ?: 0L,
                duration = mediaFromDb?.duration ?: 0L,
                width = 0,
                height = 0,
                dateModified = favorite.addedAt, // Use favorite added timestamp
                mimeType = "video/*", // Default to video
                position = 0L
              )

              FeedData.MediaItem(
                id = uri,
                title = favorite.title,
                type = determineMediaType(media.mimeType),
                media = media
              ) as FeedData
            } catch (e: Exception) {
              Timber.e(e, "Error creating media item for favorite: ${favorite.id}")
              null
            }
          }

          "download_http" -> {
            // Load HTTP download state
            try {
              val state = downloadRepository.observeState(favorite.id).first()
              if (state != null) {
                FeedData.HttpDownloadItem(
                  id = favorite.id,
                  title = favorite.title,
                  downloadState = state
                ) as FeedData
              } else {
                Timber.w("Download state not found for favorite: ${favorite.id}")
                null
              }
            } catch (e: Exception) {
              Timber.e(e, "Error loading HTTP download for favorite: ${favorite.id}")
              null
            }
          }

          "download_torrent" -> {
            // Load torrent download state
            try {
              val state = downloadRepository.observeState(favorite.id).first()
              if (state != null) {
                FeedData.TorrentDownloadItem(
                  id = favorite.id,
                  title = favorite.title,
                  downloadState = state
                ) as FeedData
              } else {
                Timber.w("Download state not found for favorite: ${favorite.id}")
                null
              }
            } catch (e: Exception) {
              Timber.e(e, "Error loading torrent download for favorite: ${favorite.id}")
              null
            }
          }

          else -> {
            Timber.w("Unknown favorite type: ${favorite.type}")
            null
          }
        }
      }

      Timber.d("Converted ${feedItems.size} favorites to FeedData items")
      feedItems

    } catch (e: Exception) {
      Timber.e(e, "Error loading favorites")
      emptyList()
    }
  }

  /**
   * Load playback history (recently played media)
   * Optimized: Uses single JOIN query to fetch media with playback info in one go
   */
  private suspend fun loadHistory(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading history with limit=$limit, offset=$offset")

    try {
      // Get recently played media with playback info in a single JOIN query
      val recentlyPlayedMedia = repository.getRecentlyPlayed(limit, offset)

      Timber.d("Successfully loaded ${recentlyPlayedMedia.size} history items")

      // Convert to FeedData
      return recentlyPlayedMedia.map { media ->
        FeedData.MediaItem(
          id = media.uri,
          title = media.name,
          type = determineMediaType(media.mimeType),
          media = media
        ) as FeedData
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading history")
      return emptyList()
    }
  }

  /**
   * Load playlists from PlaylistRepository
   * Uses paginated query from DAO for efficient database access
   */
  private suspend fun loadPlaylists(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading playlists with limit=$limit, offset=$offset")

    return try {
      // Get paginated playlists directly from repository
      val pagePlaylist = playlistRepository.getPlayLists(limit, offset)

      Timber.d("Loaded ${pagePlaylist.size} playlists from repository")

      // Convert to FeedData
      val feedItems = pagePlaylist.mapNotNull { playlist ->
        try {
          FeedData.PlaylistItem(
            id = playlist.id.toString(),
            title = playlist.name,
            itemCount = playlist.itemCount
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
   * Determine media type based on MIME type
   * Uses small grid view for library sections
   */
  private fun determineMediaType(mimeType: String): FeedData.Type {
    return when {
      mimeType.startsWith("video/") -> FeedData.Type.Video
      mimeType.startsWith("audio/") -> FeedData.Type.Audio
      else -> FeedData.Type.Video // Default to video
    }
  }
}
