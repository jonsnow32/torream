package cloud.app.csplayer.media.repository

import android.content.Context
import android.media.MediaMetadata
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.dataSource.MediaPermissionException
import cloud.app.csplayer.media.dataSource.MediaStoreDataSource
import cloud.app.csplayer.media.entities.FolderEntity
import cloud.app.csplayer.media.entities.MediaEntity
import cloud.app.csplayer.media.entities.MediaWithPlayback
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.model.Media
import cloud.app.csplayer.model.MediaTypeFilter
import cloud.app.csplayer.model.SyncState
import cloud.app.csplayer.ui.feed.FeedFilterConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
  private val mediaDao: MediaDao,
  private val folderDao: FolderDao,
  private val mediaStore: MediaStoreDataSource,
  private val scope: CoroutineScope,
  @ApplicationContext private val context: Context
) : MediaRepository {

  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  private var syncJob: Job? = null

  init {
    startAutoSync()
  }

  /**
   * Check if required media permissions are granted
   */
  private fun hasMediaPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val hasVideoPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_MEDIA_VIDEO
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED

      val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_MEDIA_AUDIO
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED

      hasVideoPermission && hasAudioPermission
    } else {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
  }

  override fun observeMedia(): Flow<List<Media>> {
    return mediaDao.observeAll()
      .map { entities -> entities.map { e -> e.toMediaDomain() } }
      .flowOn(Dispatchers.IO)
  }

  override fun observeFolders(): Flow<List<Folder>> {
    return folderDao.observeAll()
      .map { entities -> entities.map { e -> e.toFolderDomain() } }
      .flowOn(Dispatchers.IO)
  }

  override fun observeSyncState(): Flow<SyncState> = _syncState.asStateFlow()

  @OptIn(kotlinx.coroutines.FlowPreview::class)
  private fun startAutoSync() {
    syncJob = mediaStore.observeMediaChanges()
      .debounce(500) // Wait 500ms after last change
      .onEach { performSync() }
      .launchIn(scope)
  }

  internal suspend fun performSync() {
    Timber.d("performSync: Starting sync...")
    _syncState.value = SyncState.Syncing(0f)

    try {
      val mediaItems = mediaStore.queryAllMedia()
      Timber.d("performSync: Found ${mediaItems.size} media items")

      // Parallel processing
      coroutineScope {
        val mediaJob = launch { syncMedia(mediaItems) }
        val folderJob = launch { syncFolders(mediaItems) }

        mediaJob.join()
        folderJob.join()
      }

      Timber.d("performSync: Sync completed successfully")
      _syncState.value = SyncState.Completed
    } catch (e: kotlinx.coroutines.CancellationException) {
      // Don't catch CancellationException - let it propagate to properly cancel the coroutine
      Timber.d("performSync: Cancelled")
      throw e
    } catch (e: SecurityException) {
      Timber.e(e, "performSync: Missing permission")
      _syncState.value = SyncState.Error.MissingPermission(
        e.message ?: "Storage permission is required to access media files"
      )
    } catch (e: java.io.IOException) {
      Timber.e(e, "performSync: Storage error")
      _syncState.value = SyncState.Error.StorageError(
        e.message ?: "Unable to access storage"
      )
    } catch (e: Exception) {
      Timber.e(e, "performSync: Sync failed")
      _syncState.value = SyncState.Error.Generic(
        e.message ?: "Unknown error occurred"
      )
    }
  }

  // Move cleanup helper earlier so it's visible when used in syncMedia
  private fun cleanupOrphanedFiles(uris: List<String>) {
    // Attempt to delete local file paths only (file:// or plain absolute paths). Skip content:// URIs.
    uris.forEach { uriStr ->
      try {
        val uri = uriStr.toUri()
        if (uri.scheme == null || uri.scheme == "file") {
          val path = if (uri.scheme == "file") uri.path else uriStr
          if (path != null) {
            val f = File(path)
            if (f.exists() && f.isFile) {
              f.delete()
            }
          }
        }
      } catch (_: Exception) {
        // ignore
      }
    }
  }

  private suspend fun syncMedia(items: List<Media>) = withContext(Dispatchers.IO) {
    val currentUris = items.map { it.uri }.toSet()

    // Get all media with their playback information joined
    val existingMediaWithPlayback = mediaDao.getAllWithPlayback().associateBy { it.media.uri }

    // Prepare batch operations
    val toUpsert = items.map { item ->
      val existing = existingMediaWithPlayback[item.uri]

      existing?.media?.copy(
        // Update media information from MediaStore
        path = item.path,
        name = item.name,
        parentPath = File(item.path).parent ?: "/",
        size = item.size,
        duration = item.duration,
        width = item.width,
        height = item.height,
        dateModified = item.dateModified,
        mediaStoreId = item.id,
        mimeType = item.mimeType
        // Preserve user metadata (thumbnailPath, isFavorite, customMetadata)
      ) ?: MediaEntity(
        uri = item.uri,
        path = item.path,
        name = item.name,
        parentPath = File(item.path).parent ?: "/",
        size = item.size,
        duration = item.duration,
        width = item.width,
        height = item.height,
        dateModified = item.dateModified,
        mediaStoreId = item.id,
        mimeType = item.mimeType
      )
    }

    val toDelete = existingMediaWithPlayback.keys.filterNot { it in currentUris }

    // Execute in transaction
    mediaDao.transaction {
      if (toUpsert.isNotEmpty()) {
        mediaDao.upsertAll(toUpsert)
      }
      if (toDelete.isNotEmpty()) {
        mediaDao.deleteByUris(toDelete)
        // Note: MediaPlaybackEntity has CASCADE delete, so playback data will be automatically deleted
      }
    }

    // Async cleanup
    launch { cleanupOrphanedFiles(toDelete) }

    Timber.d("syncMedia: Synced ${toUpsert.size} media items, " +
      "${existingMediaWithPlayback.count { it.value.playback != null }} had playback data preserved")
  }

  private suspend fun syncFolders(items: List<Media>) = withContext(Dispatchers.IO) {
    // Group media by their parent folder path
    val groupedByFolder = items
      .groupBy { File(it.path).parent ?: "/" }
      .mapValues { entry ->
        // Convert Media to MediaEntity for buildFolderTree
        entry.value.map { media ->
          MediaEntity(
            uri = media.uri,
            path = media.path,
            name = media.name,
            parentPath = File(media.path).parent ?: "/",
            size = media.size,
            duration = media.duration,
            width = media.width,
            height = media.height,
            dateModified = media.dateModified,
            mediaStoreId = media.id,
            mimeType = media.mimeType
          )
        }
      }

    val folders = buildFolderTree(groupedByFolder)

    folderDao.transaction {
      folderDao.upsertAll(folders)

      val currentPaths = folders.map { it.path }.toSet()
      val obsolete = folderDao.getAll()
        .map { it.path }
        .filterNot { it in currentPaths }

      if (obsolete.isNotEmpty()) {
        folderDao.deleteByPaths(obsolete)
      }
    }
  }

  private fun buildFolderTree(
    groupedByFolder: Map<String, List<MediaEntity>>
  ): List<FolderEntity> {
    val allFolders = mutableMapOf<String, FolderEntity>()
    val mediaCountByFolder = groupedByFolder.mapValues { it.value.size }

    // Step 1: Create all folders
    groupedByFolder.keys.forEach { path ->
      var currentPath = path

      while (currentPath.isNotEmpty() && currentPath != "/") {
        val currentFile = File(currentPath)
        val parentPath = currentFile.parent ?: ""

        // ✅ Validate and normalize folder name
        val folderName = when {
          // Root storage paths - use friendly names
          currentPath == "/storage/emulated/0" -> "Internal Storage"
          currentPath == "/storage/emulated" -> "Emulated"
          // External SD card - check if it's a root storage path
          currentPath.matches(Regex("/storage/[^/]+$")) && currentFile.name.isNotEmpty() -> {
            // e.g., /storage/sdcard1 -> "SD Card 1"
            currentFile.name
          }
          // Normal folder - use file name directly
          currentFile.name.isNotEmpty() -> currentFile.name
          // Fallback - should not happen
          else -> "Unknown"
        }

        // Skip if name is still invalid
        if (folderName.isBlank() || folderName == "0") {
          currentPath = parentPath
          continue
        }

        if (currentPath !in allFolders) {
          allFolders[currentPath] = FolderEntity(
            path = currentPath,
            name = folderName,
            parentPath = parentPath,
            modified = currentFile.lastModified(),
            mediaCount = 0,
            childCount = 0,
            isHidden = currentFile.isHidden
          )
        }

        currentPath = parentPath
      }
    }

    // Step 2: Update media counts
    mediaCountByFolder.forEach { (folderPath, count) ->
      allFolders[folderPath]?.let { folder ->
        allFolders[folderPath] = folder.copy(
          mediaCount = count
        )
      }
    }

    // Step 3: Filter empty folders (iterative)
    var updatedFolders = allFolders.values.toList()
    var hasChanges = true

    while (hasChanges) {
      val childCountByFolder = updatedFolders
        .groupBy { it.parentPath }
        .mapValues { it.value.size }

      val filtered = updatedFolders
        .map { folder ->
          folder.copy(
            mediaCount = mediaCountByFolder[folder.path] ?: 0,
            childCount = childCountByFolder[folder.path] ?: 0
          )
        }
        .filter { folder ->
          // Keep folders that have media OR children
          folder.mediaCount > 0 || folder.childCount > 0
        }

      hasChanges = (filtered.size != updatedFolders.size)
      updatedFolders = filtered
    }

    Timber.d(
      "buildFolderTree: Kept ${updatedFolders.size} folders out of ${allFolders.size} " +
        "(filtered ${allFolders.size - updatedFolders.size} empty/invalid folders)"
    )

    return updatedFolders
  }

  override suspend fun refreshMedia(path: String?): Boolean {
    // Directly perform sync to query MediaStore and update database
    // No need to scan - MediaStore already has data, we just need permission to read it
    performSync()
    return true
  }

  override suspend fun getMediaByFolder(folderPath: String): List<Media> = withContext(Dispatchers.IO) {
    return@withContext mediaDao.getByFolderWithPlayback(folderPath).map { it.toMediaDomain() }
  }

  override suspend fun getMediaByFolderPaged(
    folderPath: String,
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> =
    withContext(Dispatchers.IO) {
      // Load more items than needed to ensure correct pagination after sorting
      val allMedia = mediaDao.getByFolderPagedWithPlayback(folderPath, limit * 10, 0).map { it.toMediaDomain() }
      val sorted = sortMediaList(allMedia, sortBy, sortOrder)

      // Apply pagination to sorted results
      val start = offset.coerceAtMost(sorted.size)
      val end = (offset + limit).coerceAtMost(sorted.size)

      return@withContext if (start < sorted.size) sorted.subList(start, end) else emptyList()
    }

  override suspend fun getMediaByFolderPagedFiltered(
    folderPath: String,
    limit: Int,
    offset: Int,
    mediaTypeFilter: MediaTypeFilter,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> = withContext(Dispatchers.IO) {
    // If ALL, use the non-filtered method for better performance
    if (mediaTypeFilter == MediaTypeFilter.ALL) {
      return@withContext getMediaByFolderPaged(folderPath, limit, offset, sortBy, sortOrder)
    }

    // Determine MIME type pattern based on filter
    val mimeTypePattern = when (mediaTypeFilter) {
      MediaTypeFilter.VIDEO -> "video/%"
      MediaTypeFilter.AUDIO -> "audio/%"
      MediaTypeFilter.ALL -> "%"
    }

    // Load more items than needed to ensure correct pagination after sorting
    val allMedia = mediaDao.getByFolderPagedFilteredWithPlayback(folderPath, mimeTypePattern, limit * 10, 0).map { it.toMediaDomain() }
    val sorted = sortMediaList(allMedia, sortBy, sortOrder)

    // Apply pagination to sorted results
    val start = offset.coerceAtMost(sorted.size)
    val end = (offset + limit).coerceAtMost(sorted.size)

    return@withContext if (start < sorted.size) sorted.subList(start, end) else emptyList()
  }

  override suspend fun getAllMediaPaged(
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> = withContext(Dispatchers.IO) {
    // Load more items than needed to ensure correct pagination after sorting
    val allMedia = mediaDao.getAllPagedWithPlayback(limit * 10, 0).map { it.toMediaDomain() }

    Timber.d("getAllMediaPaged: Found ${allMedia.size} media items before sorting")

    // If no data and no permission, throw exception to trigger error UI
    if (allMedia.isEmpty() && offset == 0 && !hasMediaPermissions()) {
      Timber.w("getAllMediaPaged: No media in database and no permission - throwing exception")
      throw MediaPermissionException("Media access permission is required to load media files")
    }

    val sorted = sortMediaList(allMedia, sortBy, sortOrder)

    // Apply pagination to sorted results
    val start = offset.coerceAtMost(sorted.size)
    val end = (offset + limit).coerceAtMost(sorted.size)

    val result = if (start < sorted.size) sorted.subList(start, end) else emptyList()

    Timber.d("getAllMediaPaged: Returning ${result.size} media items (offset=$offset, limit=$limit)")

    return@withContext result
  }

  override suspend fun getAllMediaPagedFiltered(
    limit: Int,
    offset: Int,
    mediaTypeFilter: MediaTypeFilter,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> = withContext(Dispatchers.IO) {
    // If ALL, use the non-filtered method for better performance
    if (mediaTypeFilter == MediaTypeFilter.ALL) {
      return@withContext getAllMediaPaged(limit, offset, sortBy, sortOrder)
    }

    // Determine MIME type pattern based on filter
    val mimeTypePattern = when (mediaTypeFilter) {
      MediaTypeFilter.VIDEO -> "video/%"
      MediaTypeFilter.AUDIO -> "audio/%"
      MediaTypeFilter.ALL -> "%"
    }

    // Load more items than needed to ensure correct pagination after sorting
    val allMedia = mediaDao.getAllPagedFilteredWithPlayback(mimeTypePattern, limit * 10, 0).map { it.toMediaDomain() }
    val sorted = sortMediaList(allMedia, sortBy, sortOrder)

    // Apply pagination to sorted results
    val start = offset.coerceAtMost(sorted.size)
    val end = (offset + limit).coerceAtMost(sorted.size)

    return@withContext if (start < sorted.size) sorted.subList(start, end) else emptyList()
  }

  override suspend fun getFoldersPaged(
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Folder> =
    withContext(Dispatchers.IO) {
      // Get only root-level folders (folders with no parent or common root paths)
      val allFolders = folderDao.getAll().map { it.toFolderDomain() }
      val rootFolders = allFolders.filter { folder ->
        folder.parentPath.isEmpty() ||
        folder.parentPath == "/" ||
        folder.parentPath == "/storage" ||
        folder.parentPath == "/storage/emulated" ||
        folder.parentPath == "/storage/emulated/0"
      }

      val sorted = sortFolderList(rootFolders, sortBy, sortOrder)

      Timber.d("getFoldersPaged: Found ${sorted.size} root folders (offset=$offset, limit=$limit)")

      // If no data and no permission, throw exception to trigger error UI
      if (sorted.isEmpty() && offset == 0 && !hasMediaPermissions()) {
        Timber.w("getFoldersPaged: No folders in database and no permission - throwing exception")
        throw MediaPermissionException("Media access permission is required to load folders")
      }

      // Apply pagination
      val start = offset.coerceAtMost(sorted.size)
      val end = (offset + limit).coerceAtMost(sorted.size)

      Timber.d("getFoldersPaged: Returning ${end - start} folders")
      return@withContext sorted.subList(start, end)
    }

  override suspend fun getSubfoldersPaged(parentPath: String, limit: Int, offset: Int): List<Folder> =
    withContext(Dispatchers.IO) {
      // Get only direct children of the specified parent
      val allFolders = folderDao.getAll().map { it.toFolderDomain() }
      val subfolders = allFolders
        .filter { it.parentPath == parentPath }
        .sortedBy { it.name.lowercase() }

      // Apply pagination
      val start = offset.coerceAtMost(subfolders.size)
      val end = (offset + limit).coerceAtMost(subfolders.size)

      Timber.d("getSubfoldersPaged: Found ${subfolders.size} subfolders in $parentPath, returning ${end - start}")
      return@withContext subfolders.subList(start, end)
    }

  override suspend fun countMediaInFolder(folderPath: String): Int =
    withContext(Dispatchers.IO) {
      return@withContext mediaDao.countMediaInFolder(folderPath)
    }

  override suspend fun countAllFolders(): Int =
    withContext(Dispatchers.IO) {
      return@withContext folderDao.countAllFolders()
    }

  override suspend fun countAllMedia(): Int =
    withContext(Dispatchers.IO) {
      return@withContext mediaDao.countAllMedia()
    }

  override suspend fun updateMediaMetadata(
    uri: String,
    metadata: MediaMetadata
  ) = withContext(Dispatchers.IO) {
    // Find existing entity
    val all = mediaDao.getAll()
    val existing = all.firstOrNull { it.uri == uri } ?: return@withContext

    // Extract metadata using proper accessor methods
    val title = if (metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE)) {
      metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
    } else null

    val durationMs = if (metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
      metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
    } else null

    val updated = existing.copy(
      name = title ?: existing.name,
      duration = durationMs ?: existing.duration
      // You can extract more fields if needed and map into customMetadata
    )

    mediaDao.upsertAll(listOf(updated))
  }

  override suspend fun search(
    query: String,
    limit: Int,
    offset: Int,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> = withContext(Dispatchers.IO) {
    // Query MediaStore directly with pagination for real-time media search
    // This bypasses the database and queries MediaStore on-demand
    Timber.d("search: Querying MediaStore directly with query='$query', limit=$limit, offset=$offset")

    try {
      // Use the new queryMedia method with pagination support
      val allMedia = mediaStore.queryMedia(query, limit * 10, 0)
      val sorted = sortMediaList(allMedia, sortBy, sortOrder)

      // Apply pagination to sorted results
      val start = offset.coerceAtMost(sorted.size)
      val end = (offset + limit).coerceAtMost(sorted.size)

      val result = if (start < sorted.size) sorted.subList(start, end) else emptyList()

      Timber.d("search: Returning ${result.size} items from MediaStore")

      return@withContext result
    } catch (e: SecurityException) {
      Timber.e(e, "search: Permission denied accessing MediaStore")
      throw e
    } catch (e: Exception) {
      Timber.e(e, "search: Error querying MediaStore")
      emptyList()
    }
  }

  override suspend fun getRecentlyPlayed(limit: Int, offset: Int): List<Media> =
    withContext(Dispatchers.IO) {
      Timber.d("getRecentlyPlayed: limit=$limit, offset=$offset")

      // Use DAO query that JOINs media and playback tables in a single query
      val mediaWithPlayback = mediaDao.getRecentlyPlayedWithPlayback(limit, offset)

      Timber.d("getRecentlyPlayed: Found ${mediaWithPlayback.size} recently played items")

      // Convert to Media domain models
      return@withContext mediaWithPlayback.map { it.toMediaDomain() }
    }

  // Sorting helpers
  private fun sortMediaList(
    media: List<Media>,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> {
    val comparator: Comparator<Media> = when (sortBy) {
      FeedFilterConfig.SortBy.TITLE -> compareBy { it.name.lowercase() }
      FeedFilterConfig.SortBy.DURATION -> compareBy { it.duration }
      FeedFilterConfig.SortBy.DATE -> compareBy { it.dateModified }
      FeedFilterConfig.SortBy.SIZE -> compareBy { it.size }
      FeedFilterConfig.SortBy.LOCATION -> compareBy { it.path.lowercase() }
    }

    return when (sortOrder) {
      FeedFilterConfig.SortOrder.ASCENDING -> media.sortedWith(comparator)
      FeedFilterConfig.SortOrder.DESCENDING -> media.sortedWith(comparator.reversed())
    }
  }

  private fun sortFolderList(
    folders: List<Folder>,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Folder> {
    val comparator: Comparator<Folder> = when (sortBy) {
      FeedFilterConfig.SortBy.TITLE -> compareBy { it.name.lowercase() }
      FeedFilterConfig.SortBy.DATE -> compareBy { it.modified }
      FeedFilterConfig.SortBy.LOCATION -> compareBy { it.path.lowercase() }
      FeedFilterConfig.SortBy.DURATION -> compareBy { it.mediaCount } // Use media count for folders
      FeedFilterConfig.SortBy.SIZE -> compareBy { it.mediaCount } // Use media count for folders
    }

    return when (sortOrder) {
      FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedWith(comparator)
      FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedWith(comparator.reversed())
    }
  }

  // Mapping helpers
  private fun MediaEntity.toMediaDomain(): Media {
    return Media(
      id = this.mediaStoreId,
      uri = this.uri,
      path = this.path,
      name = this.name,
      size = this.size,
      duration = this.duration,
      width = this.width,
      height = this.height,
      dateModified = this.dateModified,
      mimeType = this.mimeType
    )
  }

  private fun MediaWithPlayback.toMediaDomain(): Media {
    return Media(
      id = this.media.mediaStoreId,
      uri = this.media.uri,
      path = this.media.path,
      name = this.media.name,
      size = this.media.size,
      duration = this.media.duration,
      width = this.media.width,
      height = this.media.height,
      dateModified = this.media.dateModified,
      mimeType = this.media.mimeType,
      // Include playback information
      position = this.playback?.position ?: 0L,
      speed = this.playback?.speed ?: 1.0f,
      aspectRatio = this.playback?.aspectRatio,
      audioTrackIndex = this.playback?.audioTrackIndex ?: -1,
      textTrackIndex = this.playback?.textTrackIndex ?: -1,
      zoomType = this.playback?.zoomType ?: "fit",
      subtitles = this.playback?.subtitles,
      isFinished = this.playback?.isFinished ?: false,
      plays = 0 // Not tracked in MediaPlaybackEntity
    )
  }

  private fun FolderEntity.toFolderDomain(): Folder {
    return Folder(
      path = this.path,
      name = this.name,
      parentPath = this.parentPath,
      modified = this.modified,
      mediaCount = this.mediaCount,
      childCount = this.childCount,
      thumbnail = null
    )
  }

}
