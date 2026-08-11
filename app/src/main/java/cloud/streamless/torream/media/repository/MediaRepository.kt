package cloud.streamless.torream.media.repository

import android.media.MediaMetadata
import cloud.streamless.torream.model.Folder
import cloud.streamless.torream.model.Media
import cloud.streamless.torream.model.MediaTypeFilter
import cloud.streamless.torream.model.SyncState
import cloud.streamless.torream.ui.feed.FeedFilterConfig
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
  fun observeMedia(): Flow<List<Media>>
  fun observeFolders(): Flow<List<Folder>>
  fun observeSyncState(): Flow<SyncState>
  fun hasMediaPermission(): Boolean
  suspend fun refreshMedia(path: String? = null): Boolean
  suspend fun getMediaByFolder(folderPath: String): List<Media>
  suspend fun getMediaByFolderPaged(
    folderPath: String,
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING
  ): List<Media>

  suspend fun getMediaByFolderPagedFiltered(
    folderPath: String,
    limit: Int,
    offset: Int,
    mediaTypeFilter: MediaTypeFilter,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING
  ): List<Media>

  suspend fun getAllMediaPaged(
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING
  ): List<Media>

  suspend fun getAllMediaPagedFiltered(
    limit: Int,
    offset: Int,
    mediaTypeFilter: MediaTypeFilter,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING
  ): List<Media>

  suspend fun getFoldersPaged(
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.TITLE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.ASCENDING
  ): List<Folder>

  suspend fun getSubfoldersPaged(parentPath: String, limit: Int, offset: Int): List<Folder>
  suspend fun countMediaInFolder(folderPath: String): Int
  suspend fun countAllFolders(): Int
  suspend fun countAllMedia(): Int
  suspend fun updateMediaMetadata(uri: String, metadata: MediaMetadata)
  suspend fun getMediaByUri(uri: String): Media?
  suspend fun search(
    query: String,
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy = FeedFilterConfig.SortBy.DATE,
    sortOrder: FeedFilterConfig.SortOrder = FeedFilterConfig.SortOrder.DESCENDING
  ): List<Media>

  suspend fun getRecentlyPlayed(limit: Int, offset: Int): List<Media>
  suspend fun clearHistory(): Boolean
  suspend fun deletePlaybackHistory(mediaUri: String): Boolean
  suspend fun isInHistory(uri: String): Boolean
  suspend fun syncDownloadedFilesState()
  suspend fun isFileExists(uri: String, path: String) : Boolean
  suspend fun setFolderPrivate(path: String, isPrivate: Boolean)
  suspend fun getPrivateFoldersPaged(limit: Int, offset: Int): List<Folder>
  suspend fun countPrivateFolders(): Int
  suspend fun setMediaPrivate(uri: String, isPrivate: Boolean, newPath: String, originalPath: String?)
  suspend fun getPrivateMediaPaged(limit: Int, offset: Int): List<Media>
  suspend fun countPrivateMedia(): Int
}
