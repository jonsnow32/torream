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

  private suspend fun performSync() {
    _syncState.value = SyncState.Syncing(0f)

    try {
      val mediaItems = mediaStore.queryAllMedia()

      // Parallel processing
      coroutineScope {
        val mediaJob = launch { syncMedia(mediaItems) }
        val folderJob = launch { syncFolders(mediaItems) }

        mediaJob.join()
        folderJob.join()
      }

      _syncState.value = SyncState.Completed
    } catch (e: Exception) {
      _syncState.value = SyncState.Error(e.message ?: "Unknown error")
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
      if (existing != null) {
        // Preserve user metadata
        existing.copy(
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
      } else {
        MediaEntity(
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
    val folders = buildFolderTree(items)

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

  private suspend fun buildFolderTree(items: List<Media>): List<FolderEntity> = withContext(Dispatchers.IO) {
    val folders = mutableListOf<FolderEntity>()
    val processedPaths = mutableSetOf<String>()

    // Build folder structure first
    items.forEach { item ->
      val file = File(item.path)
      var current = file.parentFile

      while (current != null && current.path !in processedPaths) {
        folders.add(
          FolderEntity(
            path = current.path,
            name = current.name,
            parentPath = current.parent ?: "/",
            modified = current.lastModified(),
            mediaCount = 0, // Will calculate below
            childCount = 0  // Will calculate below
          )
        )
        processedPaths.add(current.path)
        current = current.parentFile
      }
    }

    // Calculate media count for each folder
    val mediaCountByFolder = items
      .groupBy { File(it.path).parent ?: "/" }
      .mapValues { it.value.size }

    // Calculate child folder count
    val childCountByFolder = folders
      .groupBy { it.parentPath }
      .mapValues { it.value.size }

    // Update folders with actual counts
    return@withContext folders.map { folder ->
      folder.copy(
        mediaCount = mediaCountByFolder[folder.path] ?: 0,
        childCount = childCountByFolder[folder.path] ?: 0
      )
    }
  }

  override suspend fun refreshMedia(path: String?): Boolean {
    return mediaStore.scanMedia(path)
  }

  override suspend fun getMediaByFolder(folderPath: String): List<Media> = withContext(Dispatchers.IO) {
    return@withContext mediaDao.getByFolder(folderPath).map { it.toDomain() }
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
