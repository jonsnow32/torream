package cloud.app.csplayer.ui.library

import androidx.paging.PagingSource
import androidx.paging.PagingState
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.TorrentRepository
import cloud.app.csplayer.ui.feed.FeedData
import kotlinx.coroutines.CancellationException
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
  private val torrentRepository: TorrentRepository,
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
    Timber.d("Loading downloads with limit=$limit, offset=$offset")

    // Get all torrents (active downloads)
    val torrents = torrentRepository.getAllTorrents()

    Timber.d("Found ${torrents.size} torrents")

    // Convert to FeedData and apply pagination manually since repository doesn't support it
    return torrents
      .drop(offset)
      .take(limit)
      .map { torrent ->
        FeedData.TorrentDownloadItem(
          id = torrent.infoHash,
          title = torrent.name,
          torrentState = torrent
        ) as FeedData
      }
      .also {
        Timber.d("Successfully loaded ${it.size} download items")
      }
  }

  /**
   * Load favorite media items
   * Note: Currently the Media entity has isFavorite field but no DAO query for it.
   * This is a placeholder that returns empty list until DAO is updated.
   */
  private fun loadFavorites(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading favorites with limit=$limit, offset=$offset")

    // TODO: Add DAO query for favorites when implemented
    // For now, return empty list
    Timber.w("Favorites query not yet implemented in MediaDao")

    return emptyList()
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
   * Load playlists
   * Note: Playlist feature not yet implemented in the app.
   * This is a placeholder that returns empty list.
   */
  private fun loadPlaylists(limit: Int, offset: Int): List<FeedData> {
    Timber.d("Loading playlists with limit=$limit, offset=$offset")

    // TODO: Implement playlist feature
    Timber.w("Playlists feature not yet implemented")

    return emptyList()
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

