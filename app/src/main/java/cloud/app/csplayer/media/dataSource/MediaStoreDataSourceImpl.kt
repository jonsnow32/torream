package cloud.app.csplayer.media.dataSource

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import cloud.app.csplayer.media.model.Media
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

  /**
   * Check if required media permissions are granted
   */
  private fun hasMediaPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ (API 33+)
      val hasVideoPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_VIDEO
      ) == PackageManager.PERMISSION_GRANTED

      val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_AUDIO
      ) == PackageManager.PERMISSION_GRANTED

      hasVideoPermission && hasAudioPermission
    } else {
      // Android 12 and below
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  override fun observeMediaChanges(): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(null) {
      override fun onChange(selfChange: Boolean) {
        trySend(Unit)
      }
    }

    context.contentResolver.registerContentObserver(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      true,
      observer
    )

    trySend(Unit) // Initial value

    awaitClose {
      context.contentResolver.unregisterContentObserver(observer)
    }
  }.flowOn(Dispatchers.IO)

  override suspend fun queryAllMedia(): List<Media> = withContext(Dispatchers.IO) {
    // Check permissions before querying
    if (!hasMediaPermissions()) {
      throw SecurityException("Media access permission is required to load videos and audio files")
    }

    val items = mutableListOf<Media>()

    context.contentResolver.query(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      PROJECTION,
      null,
      null,
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

  companion object {
    private val PROJECTION = arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.HEIGHT,
      MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.MIME_TYPE
    )
  }
}

