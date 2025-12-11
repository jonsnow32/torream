package cloud.app.csplayer.media.dataSource

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import cloud.app.csplayer.model.Media
import cloud.app.csplayer.utils.hasMediaPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreDataSourceImpl @Inject constructor(
  @param:ApplicationContext private val context: Context
) : MediaStoreDataSource {


  override fun observeMediaChanges(): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(null) {
      override fun onChange(selfChange: Boolean) {
        trySend(Unit)
      }
    }

    // Observe both video and audio changes
    context.contentResolver.registerContentObserver(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      true,
      observer
    )

    context.contentResolver.registerContentObserver(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      true,
      observer
    )

    trySend(Unit) // Initial value

    awaitClose {
      context.contentResolver.unregisterContentObserver(observer)
    }
  }.flowOn(Dispatchers.IO)

  override suspend fun queryMedia(query: String?, limit: Int, offset: Int): List<Media> = withContext(Dispatchers.IO) {
    // Check permissions before querying
    if (!context.hasMediaPermissions()) {
      timber.log.Timber.w("Media access permission not granted - throwing exception")
      throw MediaPermissionException()
    }

    timber.log.Timber.d("queryMedia: Searching with query='$query', limit=$limit, offset=$offset")

    val allItems = mutableListOf<Media>()

    // Build selection clause for search query
    val selection = if (!query.isNullOrBlank()) {
      "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
    } else {
      null
    }
    val selectionArgs = if (!query.isNullOrBlank()) {
      arrayOf("%$query%")
    } else {
      null
    }

    // Use Bundle-based query for API 30+ for proper LIMIT/OFFSET support
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Android 11+ (API 30+) - Use Bundle for pagination
      val queryArgs = android.os.Bundle().apply {
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
        putStringArray(
          android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
          arrayOf(MediaStore.Video.Media.DISPLAY_NAME)
        )
        putInt(
          android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
          android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
        )

        // Add selection if we have a search query
        if (!query.isNullOrBlank()) {
          putString(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
          )
          putStringArray(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            arrayOf("%$query%")
          )
        }
      }

      // Query VIDEO files
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        VIDEO_PROJECTION,
        queryArgs,
        null
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id
          )

          allItems.add(
            Media(
              id = id,
              uri = uri.toString(),
              path = cursor.getString(dataColumn),
              name = File(cursor.getString(dataColumn)).name,
              size = cursor.getLong(sizeColumn),
              duration = cursor.getLong(durationColumn),
              width = cursor.getInt(widthColumn),
              height = cursor.getInt(heightColumn),
              dateModified = cursor.getLong(dateModifiedColumn),
              mimeType = cursor.getString(mimeTypeColumn)
            )
          )
        }
      }

      // Query AUDIO files
      val audioQueryArgs = android.os.Bundle().apply {
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
        putStringArray(
          android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
          arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
        )
        putInt(
          android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
          android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
        )

        if (!query.isNullOrBlank()) {
          putString(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
          )
          putStringArray(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            arrayOf("%$query%")
          )
        }
      }

      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        AUDIO_PROJECTION,
        audioQueryArgs,
        null
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id
          )

          allItems.add(
            Media(
              id = id,
              uri = uri.toString(),
              path = cursor.getString(dataColumn),
              name = File(cursor.getString(dataColumn)).name,
              size = cursor.getLong(sizeColumn),
              duration = cursor.getLong(durationColumn),
              width = 0,
              height = 0,
              dateModified = cursor.getLong(dateModifiedColumn),
              mimeType = cursor.getString(mimeTypeColumn)
            )
          )
        }
      }
    } else {
      // Fallback for older Android versions - query all and paginate manually
      timber.log.Timber.d("queryMedia: Using manual pagination for Android < API 30")

      val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

      // Query VIDEO files
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        VIDEO_PROJECTION,
        selection,
        selectionArgs,
        sortOrder
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id
          )

          allItems.add(
            Media(
              id = id,
              uri = uri.toString(),
              path = cursor.getString(dataColumn),
              name = File(cursor.getString(dataColumn)).name,
              size = cursor.getLong(sizeColumn),
              duration = cursor.getLong(durationColumn),
              width = cursor.getInt(widthColumn),
              height = cursor.getInt(heightColumn),
              dateModified = cursor.getLong(dateModifiedColumn),
              mimeType = cursor.getString(mimeTypeColumn)
            )
          )
        }
      }

      // Query AUDIO files
      val audioSelection = if (!query.isNullOrBlank()) {
        "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
      } else {
        null
      }

      val audioSortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        AUDIO_PROJECTION,
        audioSelection,
        selectionArgs,
        audioSortOrder
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id
          )

          allItems.add(
            Media(
              id = id,
              uri = uri.toString(),
              path = cursor.getString(dataColumn),
              name = File(cursor.getString(dataColumn)).name,
              size = cursor.getLong(sizeColumn),
              duration = cursor.getLong(durationColumn),
              width = 0,
              height = 0,
              dateModified = cursor.getLong(dateModifiedColumn),
              mimeType = cursor.getString(mimeTypeColumn)
            )
          )
        }
      }

      // Apply manual pagination for older Android versions
      val start = offset.coerceAtMost(allItems.size)
      val end = (offset + limit).coerceAtMost(allItems.size)
      val paginatedItems = if (start < end) {
        allItems.subList(start, end)
      } else {
        emptyList()
      }

      timber.log.Timber.d("queryMedia: Returning ${paginatedItems.size} items from ${allItems.size} total (manual pagination)")
      return@withContext paginatedItems
    }

    timber.log.Timber.d("queryMedia: Returning ${allItems.size} items from MediaStore (Bundle-based pagination)")
    allItems
  }

  override suspend fun queryAllMedia(query: String?): List<Media> = withContext(Dispatchers.IO) {
    // Check permissions before querying
    if (!context.hasMediaPermissions()) {
      timber.log.Timber.w("Media access permission not granted - throwing exception")
      throw MediaPermissionException()
    }

    val items = mutableListOf<Media>()

    // Build selection clause for search query
    val selection = if (!query.isNullOrBlank()) {
      "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
    } else {
      null
    }
    val selectionArgs = if (!query.isNullOrBlank()) {
      arrayOf("%$query%")
    } else {
      null
    }

    timber.log.Timber.d("queryAllMedia: Searching with query='$query'")

    // Query VIDEO files
    context.contentResolver.query(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      VIDEO_PROJECTION,
      selection,
      selectionArgs,
      "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
      val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
      val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
      val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
      val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
      val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
      val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
      val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

      while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val uri = ContentUris.withAppendedId(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          id
        )

        items.add(
          Media(
            id = id,
            uri = uri.toString(),
            path = cursor.getString(dataColumn),
            name = File(cursor.getString(dataColumn)).name,
            size = cursor.getLong(sizeColumn),
            duration = cursor.getLong(durationColumn),
            width = cursor.getInt(widthColumn),
            height = cursor.getInt(heightColumn),
            dateModified = cursor.getLong(dateModifiedColumn),
            mimeType = cursor.getString(mimeTypeColumn)
          )
        )
      }
    }

    // Query AUDIO files
    val audioSelection = if (!query.isNullOrBlank()) {
      "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
    } else {
      null
    }

    context.contentResolver.query(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      AUDIO_PROJECTION,
      audioSelection,
      selectionArgs,
      "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
      val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
      val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
      val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
      val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

      while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val uri = ContentUris.withAppendedId(
          MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          id
        )

        items.add(
          Media(
            id = id,
            uri = uri.toString(),
            path = cursor.getString(dataColumn),
            name = File(cursor.getString(dataColumn)).name,
            size = cursor.getLong(sizeColumn),
            duration = cursor.getLong(durationColumn),
            width = 0, // Audio files don't have width
            height = 0, // Audio files don't have height
            dateModified = cursor.getLong(dateModifiedColumn),
            mimeType = cursor.getString(mimeTypeColumn)
          )
        )
      }
    }

    timber.log.Timber.d("queryAllMedia: Found ${items.size} total media items (video + audio)")
    items
  }

  @Suppress("DEPRECATION")
  override suspend fun scanMedia(path: String?): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
          data = if (path != null) {
            Uri.fromFile(File(path))
          } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
          }
        }
        context.sendBroadcast(intent)
        true
      } catch (_: Exception) {
        false
      }
    }
  }

  /**
   * Scan app's Download folder for media files
   * This handles files in /storage/emulated/0/Android/data/{package}/files/Download
   * which are not indexed by MediaStore
   */
  suspend fun scanAppDownloadFolder(): List<Media> = withContext(Dispatchers.IO) {
    val mediaItems = mutableListOf<Media>()

    try {
      val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
      if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
        timber.log.Timber.d("scanAppDownloadFolder: Scanning directory ${downloadDir.absolutePath}")
        scanDirectoryForMedia(downloadDir, mediaItems)
      } else {
        timber.log.Timber.w("scanAppDownloadFolder: Download directory not found or not accessible")
      }
    } catch (e: Exception) {
      timber.log.Timber.e(e, "scanAppDownloadFolder: Error scanning download folder")
    }

    timber.log.Timber.d("scanAppDownloadFolder: Found ${mediaItems.size} media items in download folder")
    mediaItems
  }

  /**
   * Recursively scan a directory for media files
   */
  private fun scanDirectoryForMedia(directory: File, mediaItems: MutableList<Media>) {
    try {
      directory.listFiles()?.forEach { file ->
        when {
          file.isDirectory -> {
            // Recursively scan subdirectories
            scanDirectoryForMedia(file, mediaItems)
          }
          file.isFile && isMediaFile(file) -> {
            // Add media file
            val mimeType = getMimeType(file)
            mediaItems.add(
              Media(
                id = file.absolutePath.hashCode().toLong(),
                uri = Uri.fromFile(file).toString(),
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                duration = 0L, // Duration would require MediaMetadataRetriever
                width = if (mimeType.startsWith("video/")) 0 else 0,
                height = if (mimeType.startsWith("video/")) 0 else 0,
                dateModified = file.lastModified(),
                mimeType = mimeType
              )
            )
          }
        }
      }
    } catch (e: Exception) {
      timber.log.Timber.w(e, "scanDirectoryForMedia: Error scanning directory ${directory.absolutePath}")
    }
  }

  /**
   * Check if a file is a media file (video or audio)
   */
  private fun isMediaFile(file: File): Boolean {
    val mimeType = getMimeType(file)
    return mimeType.startsWith("video/") || mimeType.startsWith("audio/")
  }

  /**
   * Get MIME type from file extension
   */
  private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
      // Video formats
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "webm" -> "video/webm"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "flv" -> "video/x-flv"
      "wmv" -> "video/x-ms-wmv"
      "3gp" -> "video/3gpp"
      "m3u8" -> "application/vnd.apple.mpegurl"
      "ts", "m2ts", "mts" -> "video/mp2t"
      // Audio formats
      "mp3" -> "audio/mpeg"
      "m4a" -> "audio/mp4"
      "aac" -> "audio/aac"
      "ogg", "oga" -> "audio/ogg"
      "flac" -> "audio/flac"
      "wav" -> "audio/wav"
      "wma" -> "audio/x-ms-wma"
      "aiff" -> "audio/x-aiff"
      else -> "application/octet-stream"
    }
  }

  companion object {
    private val VIDEO_PROJECTION = arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.HEIGHT,
      MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.MIME_TYPE
    )

    private val AUDIO_PROJECTION = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.SIZE,
      MediaStore.Audio.Media.DATE_MODIFIED,
      MediaStore.Audio.Media.MIME_TYPE
    )
  }
}

