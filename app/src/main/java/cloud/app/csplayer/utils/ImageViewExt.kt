package cloud.app.csplayer.utils

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.ImageView
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Load thumbnail from MediaStore URI into this ImageView
 * @param uriString The MediaStore URI string
 * @param size Thumbnail size (default 320x180 for 16:9 aspect ratio)
 */
fun ImageView.loadThumbnail(uriString: String, size: Size = Size(320, 180)) {
  CoroutineScope(Dispatchers.Main).launch {
    try {
      val uri = uriString.toUri()
      val thumbnail = withContext(Dispatchers.IO) {
        loadThumbnailFromUri(uri, size)
      }
      setImageBitmap(thumbnail)
    } catch (e: Exception) {
      Timber.e(e, "Failed to load thumbnail for $uriString")
      // Keep default background if thumbnail fails to load
    }
  }
}

/**
 * Load thumbnail from MediaStore URI
 */
private fun ImageView.loadThumbnailFromUri(uri: Uri, size: Size): Bitmap? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // Android 10+ (API 29+) - Use loadThumbnail
    context.contentResolver.loadThumbnail(uri, size, null)
  } else {
    // Android 9 and below - Use deprecated getThumbnail
    @Suppress("DEPRECATION")
    android.provider.MediaStore.Video.Thumbnails.getThumbnail(
      context.contentResolver,
      uri.lastPathSegment?.toLongOrNull() ?: 0L,
      android.provider.MediaStore.Video.Thumbnails.MINI_KIND,
      null
    )
  }
}

