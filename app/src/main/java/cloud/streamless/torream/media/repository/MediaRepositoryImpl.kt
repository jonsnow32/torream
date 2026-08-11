package cloud.streamless.torream.media.repository

import android.content.Context
import android.media.MediaMetadata
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cloud.streamless.torream.download.DownloadStatus
import cloud.streamless.torream.media.dao.DownloadDao
import cloud.streamless.torream.media.dao.FolderDao
import cloud.streamless.torream.media.dao.MediaDao
import cloud.streamless.torream.media.dataSource.MediaPermissionException
import cloud.streamless.torream.media.dataSource.MediaStoreDataSource
import cloud.streamless.torream.media.dataSource.MediaStoreDataSourceImpl
import cloud.streamless.torream.media.entities.FolderEntity
import cloud.streamless.torream.media.entities.HttpEntity
import cloud.streamless.torream.media.entities.MediaEntity
import cloud.streamless.torream.media.entities.MediaWithPlayback
import cloud.streamless.torream.media.entities.TorrentEntity
import cloud.streamless.torream.model.Folder
import cloud.streamless.torream.model.Media
import cloud.streamless.torream.model.MediaTypeFilter
import cloud.streamless.torream.model.SyncState
import cloud.streamless.torream.ui.feed.FeedFilterConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
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
  private val downloadDao: DownloadDao,
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

  override fun hasMediaPermission(): Boolean = hasMediaPermissions()

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
      // Get media from MediaStore
      val mediaItems = mediaStore.queryAllMedia().toMutableList()
      Timber.d("performSync: Found ${mediaItems.size} media items from MediaStore")

      // Scan app's Download folder for media not in MediaStore
      val downloadFolderItems = (mediaStore as? MediaStoreDataSourceImpl)?.scanAppDownloadFolder() ?: emptyList()
      if (downloadFolderItems.isNotEmpty()) {
        Timber.d("performSync: Found ${downloadFolderItems.size} media items in Download folder")

        // Add download folder items that aren't already in mediaItems
        val existingUris = mediaItems.map { it.uri }.toSet()
        downloadFolderItems.forEach { item ->
          if (item.uri !in existingUris) {
            mediaItems.add(item)
          }
        }
      }

      // Drop 0-byte or zero-duration files - they're unplayable (interrupted downloads,
      // corrupted files) and would otherwise show up in the feed and fail at playback time
      val playableItems = mediaItems.filter { it.size > 0L && it.duration > 0L }
      val droppedCount = mediaItems.size - playableItems.size
      if (droppedCount > 0) {
        Timber.w("performSync: Dropping $droppedCount unplayable item(s) (0 size or 0 duration)")
      }

      Timber.d("performSync: Total ${playableItems.size} media items after combining sources")

      // Parallel processing
      coroutineScope {
        val mediaJob = launch { syncMedia(playableItems) }
        val folderJob = launch { syncFolders(playableItems) }
        val downloadJob = launch { syncDownloadedFilesState() }

        mediaJob.join()
        folderJob.join()
        downloadJob.join()
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

    val privateUris = existingMediaWithPlayback.filter { it.value.media.isPrivate }.keys.toSet()
    val toDelete = existingMediaWithPlayback.keys.filterNot { it in currentUris || it in privateUris }

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

    val existingPrivatePaths = folderDao.getAll()
      .filter { it.isPrivate }
      .map { it.path }
      .toSet()
    val folders = buildFolderTree(groupedByFolder, existingPrivatePaths)

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
    groupedByFolder: Map<String, List<MediaEntity>>,
    existingPrivatePaths: Set<String> = emptySet()
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
            isHidden = currentFile.isHidden,
            isPrivate = currentPath in existingPrivatePaths
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

  /**
   * Sync downloaded files state - check if downloaded files still exist
   * Checks both HTTP downloads and Torrent downloads
   */
  override suspend fun syncDownloadedFilesState() = withContext(Dispatchers.IO) {
    Timber.d("syncDownloadedFilesState: Checking downloaded files existence...")

    try {
      // Sync HTTP downloads
      val httpDownloads = downloadDao.getAllHttpFlow().firstOrNull() ?: emptyList()
      if (httpDownloads.isNotEmpty()) {
        syncHttpDownloads(httpDownloads)
      }

      // Sync Torrent downloads
      val torrentDownloads = downloadDao.getAllTorrentFlow().firstOrNull() ?: emptyList()
      if (torrentDownloads.isNotEmpty()) {
        syncTorrentDownloads(torrentDownloads)
      }

      Timber.d("syncDownloadedFilesState: Sync completed")
    } catch (e: Exception) {
      Timber.e(e, "syncDownloadedFilesState: Error during sync")
    }
  }

  /**
   * Sync HTTP downloads - check if downloaded files exist
   */
  private suspend fun syncHttpDownloads(httpDownloads: List<HttpEntity>) = withContext(Dispatchers.IO) {
    Timber.d("syncHttpDownloads: Checking ${httpDownloads.size} HTTP downloads...")

    val toUpdate = mutableListOf<HttpEntity>()
    val missingFiles = mutableListOf<String>()

    httpDownloads.forEach { http ->
      try {
        // Check if file exists using targetPath and fileName
        val filePath = if (!http.fileName.isNullOrEmpty()) {
          "${http.targetPath}/${http.fileName}"
        } else {
          http.targetPath
        }

        val fileExists = checkFileExists(http.targetPath, filePath)

        if (!fileExists) {
          Timber.w("syncHttpDownloads: File not found - url=${http.url}, path=$filePath")
          missingFiles.add(http.url)
          // Mark as missing by setting downloadedBytes to 0
          toUpdate.add(http.copy(
            downloadedBytes = 0L,
            progress = 0,
            status = DownloadStatus.FAILED.name,
            error = "File not found"
          ))
        }
      } catch (e: Exception) {
        Timber.w(e, "syncHttpDownloads: Error checking file ${http.url}")
      }
    }

    // Update database if there are missing files
    if (toUpdate.isNotEmpty()) {
      toUpdate.forEach { http ->
        downloadDao.insertHttp(http)
      }
      Timber.d("syncHttpDownloads: Updated ${toUpdate.size} HTTP downloads with missing files")
    }

    if (missingFiles.isNotEmpty()) {
      Timber.d("syncHttpDownloads: Found ${missingFiles.size} missing HTTP downloads")
    }
  }

  /**
   * Sync Torrent downloads - check if downloaded files exist
   */
  private suspend fun syncTorrentDownloads(torrentDownloads: List<TorrentEntity>) = withContext(Dispatchers.IO) {
    Timber.d("syncTorrentDownloads: Checking ${torrentDownloads.size} torrent downloads...")

    val toUpdate = mutableListOf<TorrentEntity>()
    val missingFiles = mutableListOf<String>()

    torrentDownloads.forEach { torrent ->
      try {
        // For torrents, targetPath is usually the directory
        // Check if files in that directory exist
        val fileExists = checkFileExists(torrent.targetPath, torrent.targetPath)

        if (!fileExists) {
          Timber.w("syncTorrentDownloads: Directory not found - infoHash=${torrent.infoHash}, path=${torrent.targetPath}")
          missingFiles.add(torrent.infoHash)
          // Mark as missing
          toUpdate.add(torrent.copy(
            downloadedSize = 0L,
            progress = 0f,
            status = DownloadStatus.FAILED.name,
            error = "Directory not found"
          ))
        }
      } catch (e: Exception) {
        Timber.w(e, "syncTorrentDownloads: Error checking torrent ${torrent.infoHash}")
      }
    }

    // Update database if there are missing files
    if (toUpdate.isNotEmpty()) {
      toUpdate.forEach { torrent ->
        downloadDao.insertTorrent(torrent)
      }
      Timber.d("syncTorrentDownloads: Updated ${toUpdate.size} torrents with missing files")
    }

    if (missingFiles.isNotEmpty()) {
      Timber.d("syncTorrentDownloads: Found ${missingFiles.size} missing torrents")
    }
  }

  /**
   * Check if a file exists at the given URI or path
   * Supports both content:// URIs and file:// paths
   */
  private fun checkFileExists(uri: String, path: String): Boolean {
    return try {
      // First try the URI
      if (uri.isNotEmpty()) {
        try {
          val contentUri = uri.toUri()
          val exists = when {
            contentUri.scheme == "content" -> {
              // For content URIs, try to open a file descriptor
              try {
                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { it.fileDescriptor }
                true
              } catch (e: Exception) {
                Timber.w("checkFileExists: Content URI not accessible: $uri")
                false
              }
            }
            contentUri.scheme == "file" -> {
              // For file URIs, check the file path
              val filePath = contentUri.path
              if (filePath != null) File(filePath).exists() else false
            }
            contentUri.scheme == null -> {
              // No scheme, treat as file path
              File(uri).exists()
            }
            else -> false
          }

          if (exists) return true
        } catch (e: Exception) {
          Timber.w("checkFileExists: Error checking URI: $uri - ${e.message}")
        }
      }

      // Fallback to checking the path
      if (path.isNotEmpty()) {
        val file = File(path)
        file.exists() && file.isFile
      } else {
        false
      }
    } catch (e: Exception) {
      Timber.e(e, "checkFileExists: Unexpected error checking $uri or $path")
      false
    }
  }

  /**
   * Check if a specific file exists (public method)
   */
  override suspend fun isFileExists(uri: String, path: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext checkFileExists(uri, path)
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

    // If permission isn't granted, throw to trigger the permission-request error UI -
    // even if stale cached rows exist (e.g. from a prior grant, or a DB restored via
    // Android Auto Backup, which never restores the runtime permission grant with it).
    if (offset == 0 && !hasMediaPermissions()) {
      Timber.w("getAllMediaPaged: No media permission - throwing exception")
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
      val allFolders = folderDao.getAll().map { it.toFolderDomain() }
      val rootFolders = allFolders.filter { folder ->
        !folder.isPrivate && (
          folder.parentPath.isEmpty() ||
          folder.parentPath == "/" ||
          folder.parentPath == "/storage" ||
          folder.parentPath == "/storage/emulated" ||
          folder.parentPath == "/storage/emulated/0"
        )
      }

      val sorted = sortFolderList(rootFolders, sortBy, sortOrder)

      Timber.d("getFoldersPaged: Found ${sorted.size} root folders (offset=$offset, limit=$limit)")

      // If permission isn't granted, throw to trigger the permission-request error UI -
      // even if stale cached rows exist (e.g. from a prior grant, or a DB restored via
      // Android Auto Backup, which never restores the runtime permission grant with it).
      if (offset == 0 && !hasMediaPermissions()) {
        Timber.w("getFoldersPaged: No folder permission - throwing exception")
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
        .filter { it.parentPath == parentPath && !it.isPrivate }
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

  override suspend fun getMediaByUri(uri: String): Media? = withContext(Dispatchers.IO) {
    try {
      val mediaEntity = mediaDao.getByUri(uri)
      return@withContext mediaEntity?.toMediaDomain()
    } catch (e: Exception) {
      Timber.e(e, "Error getting media by URI: $uri")
      null
    }
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

  override suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      mediaDao.clearPlaybackHistory()
      Timber.d("History cleared successfully")
      true
    } catch (e: Exception) {
      Timber.e(e, "Error clearing history")
      false
    }
  }

  override suspend fun deletePlaybackHistory(mediaUri: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      mediaDao.deletePlaybackHistory(mediaUri)
      Timber.d("Deleted playback history for URI: $mediaUri")
      true
    } catch (e: Exception) {
      Timber.e(e, "Error deleting playback history for URI: $mediaUri")
      false
    }
  }

  override suspend fun isInHistory(uri: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
      mediaDao.isInHistory(uri)
    } catch (e: Exception) {
      Timber.e(e, "Error checking if URI is in history: $uri")
      false
    }
  }

  private fun sortMediaList(
    media: List<Media>,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Media> {
    return when (sortBy) {
      FeedFilterConfig.SortBy.TITLE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> media.sortedBy { it.name.lowercase() }
          FeedFilterConfig.SortOrder.DESCENDING -> media.sortedByDescending { it.name.lowercase() }
        }
      }
      FeedFilterConfig.SortBy.DATE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> media.sortedBy { it.dateModified }
          FeedFilterConfig.SortOrder.DESCENDING -> media.sortedByDescending { it.dateModified }
        }
      }
      FeedFilterConfig.SortBy.SIZE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> media.sortedBy { it.size }
          FeedFilterConfig.SortOrder.DESCENDING -> media.sortedByDescending { it.size }
        }
      }
      FeedFilterConfig.SortBy.DURATION -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> media.sortedBy { it.duration }
          FeedFilterConfig.SortOrder.DESCENDING -> media.sortedByDescending { it.duration }
        }
      }
      FeedFilterConfig.SortBy.LOCATION -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> media.sortedBy { it.path }
          FeedFilterConfig.SortOrder.DESCENDING -> media.sortedByDescending { it.path }
        }
      }
    }
  }

  private fun sortFolderList(
    folders: List<Folder>,
    sortBy: FeedFilterConfig.SortBy,
    sortOrder: FeedFilterConfig.SortOrder
  ): List<Folder> {
    return when (sortBy) {
      FeedFilterConfig.SortBy.TITLE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedBy { it.name.lowercase() }
          FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedByDescending { it.name.lowercase() }
        }
      }
      FeedFilterConfig.SortBy.DATE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedBy { it.modified }
          FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedByDescending { it.modified }
        }
      }
      FeedFilterConfig.SortBy.SIZE -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedBy { it.mediaCount }
          FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedByDescending { it.mediaCount }
        }
      }
      FeedFilterConfig.SortBy.DURATION -> {
        // Not applicable for folders, sort by name
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedBy { it.name.lowercase() }
          FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedByDescending { it.name.lowercase() }
        }
      }
      FeedFilterConfig.SortBy.LOCATION -> {
        when (sortOrder) {
          FeedFilterConfig.SortOrder.ASCENDING -> folders.sortedBy { it.path }
          FeedFilterConfig.SortOrder.DESCENDING -> folders.sortedByDescending { it.path }
        }
      }
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
      mimeType = this.mimeType,
      isPrivate = this.isPrivate,
      originalPath = this.customMetadata
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
      plays = 0, // Not tracked in MediaPlaybackEntity
      isPrivate = this.media.isPrivate,
      originalPath = this.media.customMetadata
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
      thumbnail = null,
      isPrivate = this.isPrivate
    )
  }

  override suspend fun setFolderPrivate(path: String, isPrivate: Boolean) =
    withContext(Dispatchers.IO) { folderDao.setPrivate(path, isPrivate) }

  override suspend fun getPrivateFoldersPaged(limit: Int, offset: Int): List<Folder> =
    withContext(Dispatchers.IO) {
      folderDao.getPrivatePaged(limit, offset).map { it.toFolderDomain() }
    }

  override suspend fun countPrivateFolders(): Int =
    withContext(Dispatchers.IO) { folderDao.countPrivate() }

  override suspend fun setMediaPrivate(uri: String, isPrivate: Boolean, newPath: String, originalPath: String?) =
    withContext(Dispatchers.IO) { mediaDao.setMediaPrivate(uri, isPrivate, newPath, originalPath) }

  override suspend fun getPrivateMediaPaged(limit: Int, offset: Int): List<Media> =
    withContext(Dispatchers.IO) {
      mediaDao.getPrivateMediaPaged(limit, offset).map { it.toMediaDomain() }
    }

  override suspend fun countPrivateMedia(): Int =
    withContext(Dispatchers.IO) { mediaDao.countPrivateMedia() }

}
