package cloud.app.csplayer.media.repository

import android.media.MediaMetadata
import androidx.core.net.toUri
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.dataSource.MediaStoreDataSource
import cloud.app.csplayer.media.entities.FolderEntity
import cloud.app.csplayer.media.entities.MediaEntity
import cloud.app.csplayer.media.model.Folder
import cloud.app.csplayer.media.model.Media
import cloud.app.csplayer.media.model.SyncState
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
import java.io.File
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
  private val mediaDao: MediaDao,
  private val folderDao: FolderDao,
  private val mediaStore: MediaStoreDataSource,
  private val scope: CoroutineScope,
) : MediaRepository {

  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  private var syncJob: Job? = null

  init {
    startAutoSync()
  }

  override fun observeMedia(): Flow<List<Media>> {
    return mediaDao.observeAll()
      .map { entities: List<MediaEntity> -> entities.map { e -> e.toDomain() } }
      .flowOn(Dispatchers.IO)
  }

  override fun observeFolders(): Flow<List<Folder>> {
    return folderDao.observeAll()
      .map { entities: List<FolderEntity> -> entities.map { e -> e.toDomain() } }
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
    timber.log.Timber.d("performSync: Starting sync...")
    _syncState.value = SyncState.Syncing(0f)

    try {
      val mediaItems = mediaStore.queryAllMedia()
      timber.log.Timber.d("performSync: Found ${mediaItems.size} media items")

      // Parallel processing
      coroutineScope {
        val mediaJob = launch { syncMedia(mediaItems) }
        val folderJob = launch { syncFolders(mediaItems) }

        mediaJob.join()
        folderJob.join()
      }

      timber.log.Timber.d("performSync: Sync completed successfully")
      _syncState.value = SyncState.Completed
    } catch (e: SecurityException) {
      timber.log.Timber.e(e, "performSync: Missing permission")
      _syncState.value = SyncState.Error.MissingPermission(
        e.message ?: "Storage permission is required to access media files"
      )
    } catch (e: java.io.IOException) {
      timber.log.Timber.e(e, "performSync: Storage error")
      _syncState.value = SyncState.Error.StorageError(
        e.message ?: "Unable to access storage"
      )
    } catch (e: Exception) {
      timber.log.Timber.e(e, "performSync: Sync failed")
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
    val existingMedia = mediaDao.getAll().associateBy { it.uri }

    // Prepare batch operations
    val toUpsert = items.map { item ->
      val existing = existingMedia[item.uri]
      existing?.// Preserve user metadata
      copy(
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
          ?: MediaEntity(
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

    val toDelete = existingMedia.keys.filterNot { it in currentUris }

    // Execute in transaction
    mediaDao.transaction {
      if (toUpsert.isNotEmpty()) {
        mediaDao.upsertAll(toUpsert)
      }
      if (toDelete.isNotEmpty()) {
        mediaDao.deleteByUris(toDelete)
      }
    }

    // Async cleanup
    launch { cleanupOrphanedFiles(toDelete) }
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

    timber.log.Timber.d(
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
    return@withContext mediaDao.getByFolder(folderPath).map { it.toDomain() }
  }

  override suspend fun getMediaByFolderPaged(folderPath: String, limit: Int, offset: Int): List<Media> =
    withContext(Dispatchers.IO) {
      return@withContext mediaDao.getByFolderPaged(folderPath, limit, offset).map { it.toDomain() }
    }

  override suspend fun getMediaByFolderPagedFiltered(
    folderPath: String,
    limit: Int,
    offset: Int,
    mediaTypeFilter: cloud.app.csplayer.media.model.MediaTypeFilter
  ): List<Media> = withContext(Dispatchers.IO) {
    // If ALL, use the non-filtered method for better performance
    if (mediaTypeFilter == cloud.app.csplayer.media.model.MediaTypeFilter.ALL) {
      return@withContext getMediaByFolderPaged(folderPath, limit, offset)
    }

    // Determine MIME type pattern based on filter
    val mimeTypePattern = when (mediaTypeFilter) {
      cloud.app.csplayer.media.model.MediaTypeFilter.VIDEO -> "video/%"
      cloud.app.csplayer.media.model.MediaTypeFilter.AUDIO -> "audio/%"
      cloud.app.csplayer.media.model.MediaTypeFilter.ALL -> "%"
    }

    return@withContext mediaDao.getByFolderPagedFiltered(folderPath, mimeTypePattern, limit, offset).map { it.toDomain() }
  }

  override suspend fun getAllMediaPaged(limit: Int, offset: Int): List<Media> = withContext(Dispatchers.IO) {
    return@withContext mediaDao.getAllPaged(limit, offset).map { it.toDomain() }
  }

  override suspend fun getAllMediaPagedFiltered(
    limit: Int,
    offset: Int,
    mediaTypeFilter: cloud.app.csplayer.media.model.MediaTypeFilter
  ): List<Media> = withContext(Dispatchers.IO) {
    // If ALL, use the non-filtered method for better performance
    if (mediaTypeFilter == cloud.app.csplayer.media.model.MediaTypeFilter.ALL) {
      return@withContext getAllMediaPaged(limit, offset)
    }

    // Determine MIME type pattern based on filter
    val mimeTypePattern = when (mediaTypeFilter) {
      cloud.app.csplayer.media.model.MediaTypeFilter.VIDEO -> "video/%"
      cloud.app.csplayer.media.model.MediaTypeFilter.AUDIO -> "audio/%"
      cloud.app.csplayer.media.model.MediaTypeFilter.ALL -> "%"
    }

    return@withContext mediaDao.getAllPagedFiltered(mimeTypePattern, limit, offset).map { it.toDomain() }
  }

  override suspend fun getFoldersPaged(limit: Int, offset: Int): List<Folder> =
    withContext(Dispatchers.IO) {
      // Get only root-level folders (folders with no parent or common root paths)
      val allFolders = folderDao.getAll().map { it.toDomain() }
      val rootFolders = allFolders.filter { folder ->
        folder.parentPath.isEmpty() ||
        folder.parentPath == "/" ||
        folder.parentPath == "/storage" ||
        folder.parentPath == "/storage/emulated" ||
        folder.parentPath == "/storage/emulated/0"
      }.sortedBy { it.name.lowercase() }

      // Apply pagination
      val start = offset.coerceAtMost(rootFolders.size)
      val end = (offset + limit).coerceAtMost(rootFolders.size)

      timber.log.Timber.d("getFoldersPaged: Found ${rootFolders.size} root folders, returning ${end - start}")
      return@withContext rootFolders.subList(start, end)
    }

  override suspend fun getSubfoldersPaged(parentPath: String, limit: Int, offset: Int): List<Folder> =
    withContext(Dispatchers.IO) {
      // Get only direct children of the specified parent
      val allFolders = folderDao.getAll().map { it.toDomain() }
      val subfolders = allFolders
        .filter { it.parentPath == parentPath }
        .sortedBy { it.name.lowercase() }

      // Apply pagination
      val start = offset.coerceAtMost(subfolders.size)
      val end = (offset + limit).coerceAtMost(subfolders.size)

      timber.log.Timber.d("getSubfoldersPaged: Found ${subfolders.size} subfolders in $parentPath, returning ${end - start}")
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

  override suspend fun search(query: String, limit: Int, offset: Int): List<Media> = withContext(Dispatchers.IO) {
    // Query MediaStore directly with pagination for real-time media search
    // This bypasses the database and queries MediaStore on-demand
    timber.log.Timber.d("search: Querying MediaStore directly with query='$query', limit=$limit, offset=$offset")

    try {
      // Use the new queryMedia method with pagination support
      val result = mediaStore.queryMedia(query, limit, offset)
      timber.log.Timber.d("search: Returning ${result.size} items from MediaStore")

      return@withContext result
    } catch (e: SecurityException) {
      timber.log.Timber.e(e, "search: Permission denied accessing MediaStore")
      throw e
    } catch (e: Exception) {
      timber.log.Timber.e(e, "search: Error querying MediaStore")
      emptyList()
    }
  }
  // Mapping helpers
  private fun MediaEntity.toDomain(): Media {
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

  private fun FolderEntity.toDomain(): Folder {
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
