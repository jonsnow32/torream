package cloud.app.csplayer.ui.feed

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.utils.KUniFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor() : ViewModel() {

  // sample observable data for the fragment
  val title = MutableLiveData("Feed")

  // PagingData flow for the adapter
  val feedData: Flow<PagingData<FeedData>> = Pager(
    config = PagingConfig(
      pageSize = 20,
      enablePlaceholders = false,
      initialLoadSize = 20
    ),
    pagingSourceFactory = { FeedPagingSource() }
  ).flow.cachedIn(viewModelScope)

  val displayType = MutableStateFlow(DisplayType.GRID)

//  init {
//    feedData.value = smallFeed + sampleFeed1
//  }

  // TODO: Refactor this to work with PagingData - may need to recreate Pager or use different approach
  fun changeDisplayType() {
    when (displayType.value) {
      DisplayType.GRID -> {
        displayType.value = DisplayType.LIST
      }

      DisplayType.LIST -> {
        displayType.value = DisplayType.GRID
      }
    }
  }

  // TODO: Refactor getAllFolders to work with PagingData architecture
  // Consider creating a separate PagingSource that can load from MediaStore/FileSystem
  fun getAllFolders(context: Context) {
    // Using KUniFile to load all available folders
    val folderItems = mutableListOf<FeedData.FolderItem>()

    try {
      Timber.d("Starting folder scan...")
      Timber.d("Android SDK: ${Build.VERSION.SDK_INT}")

      // For Android 10+ (API 29+), use MediaStore API due to scoped storage
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Timber.d("Using MediaStore API for Android 10+")
        getAllFoldersFromMediaStore(context, folderItems)
      } else {
        // For older versions, use direct file access
        Timber.d("Using direct file access for Android 9 and below")
        getAllFoldersFromFileSystem(context, folderItems)
      }

      Timber.d("Total folders found: ${folderItems.size}")

      // TODO: With PagingData, we can't just set the value
      // Need to create a custom PagingSource that provides these folders
      // Fallback to mock data if no folders found (for testing/demo purposes)
      if (folderItems.isEmpty()) {
        Timber.w("No folders found, using mock data as fallback")
        // feedData.value = smallFeed + sampleFeed1
      } else {
        // feedData.value = folderItems
        Timber.d("Folders loaded successfully but not yet integrated with PagingData")
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading folders")
      // Use mock data on error
      // feedData.value = smallFeed + sampleFeed1
    }
  }

  /**
   * Get folders using MediaStore API (Android 10+)
   */
  private fun getAllFoldersFromMediaStore(
    context: Context,
    folderItems: MutableList<FeedData.FolderItem>
  ) {
    try {
      // First, count total video files to verify MediaStore access
      var totalVideoCount = 0
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID),
        null,
        null,
        null
      )?.use { cursor ->
        totalVideoCount = cursor.count
      }
      Timber.d("Total video files in MediaStore: $totalVideoCount")

      // Count total audio files
      var totalAudioCount = 0
      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        null
      )?.use { cursor ->
        totalAudioCount = cursor.count
      }
      Timber.d("Total audio files in MediaStore: $totalAudioCount")

      if (totalVideoCount == 0 && totalAudioCount == 0) {
        Timber.w("No media files found in MediaStore - device may have no media or permissions not granted")
        return
      }

      // Query video files with folder info
      val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.BUCKET_ID
      )

      val videoFolders =
        mutableMapOf<String, Triple<String, String, Int>>() // bucketId -> (name, path, count)

      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        videoProjection,
        null,
        null,
        "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
      )?.use { cursor ->
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val bucketNameColumn =
          cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)

        while (cursor.moveToNext()) {
          val path = cursor.getString(dataColumn)
          val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
          val bucketId =
            cursor.getString(bucketIdColumn) ?: path?.substringBeforeLast('/') ?: "unknown"

          if (path != null) {
            val folderPath = path.substringBeforeLast('/')
            val current = videoFolders[bucketId]
            videoFolders[bucketId] = Triple(bucketName, folderPath, (current?.third ?: 0) + 1)

            // Log first few folders found
            if (current == null && videoFolders.size <= 5) {
              Timber.d("Found video folder: $bucketName at $folderPath")
            }
          }
        }
      }

      Timber.d("Found ${videoFolders.size} video folders from MediaStore")

      // Query audio files with folder info
      val audioProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Audio.Media.BUCKET_ID
      )

      val audioFolders =
        mutableMapOf<String, Triple<String, String, Int>>() // bucketId -> (name, path, count)

      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        audioProjection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.BUCKET_DISPLAY_NAME} ASC"
      )?.use { cursor ->
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val bucketNameColumn =
          cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
        val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_ID)

        while (cursor.moveToNext()) {
          val path = cursor.getString(dataColumn)
          val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
          val bucketId =
            cursor.getString(bucketIdColumn) ?: path?.substringBeforeLast('/') ?: "unknown"

          if (path != null) {
            val folderPath = path.substringBeforeLast('/')
            val current = audioFolders[bucketId]
            audioFolders[bucketId] = Triple(bucketName, folderPath, (current?.third ?: 0) + 1)

            // Log first few folders found
            if (current == null && audioFolders.size <= 5) {
              Timber.d("Found audio folder: $bucketName at $folderPath")
            }
          }
        }
      }

      Timber.d("Found ${audioFolders.size} audio folders from MediaStore")

      // Combine and convert to FolderItems
      val allFolders = mutableMapOf<String, Triple<String, String, Int>>()
      allFolders.putAll(videoFolders)

      // Merge audio folders (combine counts if same bucket ID)
      for ((bucketId, audioData) in audioFolders) {
        val existing = allFolders[bucketId]
        if (existing != null) {
          allFolders[bucketId] =
            Triple(existing.first, existing.second, existing.third + audioData.third)
        } else {
          allFolders[bucketId] = audioData
        }
      }

      for ((bucketId, data) in allFolders) {
        val (name, path, count) = data
        folderItems.add(
          FeedData.FolderItem(
            id = bucketId,
            title = name,
            folder = Folder(
              id = bucketId,
              title = name,
              path = path,
              subtitle = "$count items"
            ),
            type = FeedData.Type.FolderSmall
          )
        )
      }

      Timber.d("Created ${folderItems.size} FolderItem objects")
    } catch (e: Exception) {
      Timber.e(e, "Error querying MediaStore")
    }
  }

  /**
   * Get folders using direct file system access (Android 9 and below)
   */
  private fun getAllFoldersFromFileSystem(
    context: Context,
    folderItems: MutableList<FeedData.FolderItem>
  ) {
    // Common media directories to scan
    val mediaDirectories = listOf(
      Environment.DIRECTORY_MOVIES,
      Environment.DIRECTORY_MUSIC,
      Environment.DIRECTORY_DOWNLOADS,
      Environment.DIRECTORY_DCIM
    )

    // Scan each media directory
    for (dirType in mediaDirectories) {
      val baseDir = Environment.getExternalStoragePublicDirectory(dirType)
      Timber.d("Checking directory: $dirType at ${baseDir?.absolutePath}")
      Timber.d("  exists: ${baseDir?.exists()}, canRead: ${baseDir?.canRead()}")

      if (baseDir?.exists() == true && baseDir.canRead()) {
        val uniFile = KUniFile.fromFile(context, baseDir)
        Timber.d("  Created KUniFile, scanning...")
        uniFile?.let {
          val beforeCount = folderItems.size
          scanDirectory(it, folderItems)
          Timber.d("  Found ${folderItems.size - beforeCount} folders in $dirType")
        }
      }
    }

    // Also scan the root external storage
    val externalStorage = Environment.getExternalStorageDirectory()
    Timber.d("Checking root external storage: ${externalStorage.absolutePath}")
    Timber.d("  exists: ${externalStorage.exists()}, canRead: ${externalStorage.canRead()}")

    if (externalStorage.exists() && externalStorage.canRead()) {
      val uniFile = KUniFile.fromFile(context, externalStorage)
      Timber.d("  Created KUniFile for root, scanning with maxDepth=1...")
      uniFile?.let {
        val beforeCount = folderItems.size
        scanDirectory(it, folderItems, maxDepth = 1)
        Timber.d("  Found ${folderItems.size - beforeCount} folders in root")
      }
    }
  }

  /**
   * Recursively scan a directory and add folders containing media files to the list
   */
  private fun scanDirectory(
    directory: KUniFile,
    folderItems: MutableList<FeedData.FolderItem>,
    currentDepth: Int = 0,
    maxDepth: Int = 3
  ) {
    if (currentDepth > maxDepth || !directory.isDirectory) {
      Timber.d("Skipping ${directory.name}: depth=$currentDepth, maxDepth=$maxDepth, isDir=${directory.isDirectory}")
      return
    }

    try {
      Timber.d("Scanning directory: ${directory.name} at depth $currentDepth")
      val files = directory.listFiles()

      if (files == null) {
        Timber.w("listFiles() returned null for ${directory.name}")
        return
      }

      Timber.d("  Found ${files.size} files/folders in ${directory.name}")

      var hasMediaFiles = false
      var mediaCount = 0

      // Check if this directory contains media files
      for (file in files) {
        if (file.isFile && isMediaFile(file.name)) {
          hasMediaFiles = true
          mediaCount++
          if (mediaCount <= 3) {
            Timber.d("    Media file: ${file.name}")
          }
        }
      }

      // If this folder has media files, add it to the list
      if (hasMediaFiles && mediaCount > 0) {
        val path = directory.filePath ?: directory.uri.toString()
        val folderName = directory.name ?: "Unknown"

        Timber.d("  Adding folder: $folderName with $mediaCount media files")

        folderItems.add(
          FeedData.FolderItem(
            id = path,
            title = folderName,
            folder = Folder(
              id = path,
              title = folderName,
              path = path,
              subtitle = "$mediaCount items"
            ),
            type = FeedData.Type.FolderSmall
          )
        )
      }

      // Recursively scan subdirectories
      var subdirCount = 0
      for (file in files) {
        if (file.isDirectory) {
          // Skip hidden directories and system directories
          val name = file.name
          if (name != null && !name.startsWith(".") && !isSystemDirectory(name)) {
            subdirCount++
            scanDirectory(file, folderItems, currentDepth + 1, maxDepth)
          } else if (name != null) {
            Timber.v("  Skipping directory: $name")
          }
        }
      }

      Timber.d("  Scanned $subdirCount subdirectories in ${directory.name}")
    } catch (e: Exception) {
      Timber.w(e, "Error scanning directory: ${directory.name}")
    }
  }

  /**
   * Check if a file is a media file based on extension
   */
  private fun isMediaFile(fileName: String?): Boolean {
    if (fileName == null) return false
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in setOf(
      // Video formats
      "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg",
      // Audio formats
      "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape"
    )
  }

  /**
   * Check if a directory is a system directory that should be skipped
   */
  private fun isSystemDirectory(name: String): Boolean {
    return name.lowercase() in setOf(
      "android", "data", "obb", "cache", "thumbnails", ".trash"
    )
  }

  enum class DisplayType {
    GRID,
    LIST
  }
}



