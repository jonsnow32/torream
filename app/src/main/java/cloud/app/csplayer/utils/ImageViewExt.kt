package cloud.app.csplayer.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import cloud.app.csplayer.ui.player.mpv.MPVLib
import kotlinx.coroutines.*
import timber.log.Timber


/**
 * Load thumbnail from MediaStore URI using FFmpeg
 *
 * Features:
 * - Automatic ImageView size detection
 * - Automatic disk caching via ThumbnailCache
 * - Multiple frame position fallbacks
 * - Direct FFmpeg extraction for reliable results
 *
 * @param uriString The MediaStore URI string
 * @param placeholderRes Optional placeholder drawable while loading
 * @param errorRes Optional error drawable if loading fails
 */

fun ImageView.loadThumbnail(
  uriString: String,
  placeholderRes: Int? = null,
  errorRes: Int? = null
) {
  // Set placeholder immediately if provided
  placeholderRes?.let { setImageResource(it) }

  // Get ImageView size
  val viewWidth = if (width > 0) width else 320
  val viewHeight = if (height > 0) height else 180

  // Check ThumbnailCache first
  CoroutineScope(Dispatchers.IO).launch {
    val cachedBitmap = ThumbnailCache.loadThumbnail(context, uriString, viewWidth, viewHeight)
    if (cachedBitmap != null) {
      withContext(Dispatchers.Main) {
        setImageBitmap(cachedBitmap)
        Timber.d("✓ Loaded from ThumbnailCache (${viewWidth}x${viewHeight})")
      }
    } else {
      // Cache miss, load with FFmpeg
      loadThumbnailWithFFmpeg(
        uriString,
        errorRes,
        viewWidth,
        viewHeight,
        listOf(30000L, 40000L)
      )
    }
  }
}

private fun ImageView.loadThumbnailWithFFmpeg(
  uriString: String,
  errorRes: Int?,
  viewWidth: Int,
  viewHeight: Int,
  framePositions: List<Long>,
  currentIndex: Int = 0
) {
  if (currentIndex >= framePositions.size) {
    // All FFmpeg attempts failed, show error
    Timber.e("All FFmpeg frame positions failed for $uriString, all fallbacks exhausted")
    CoroutineScope(Dispatchers.Main).launch {
      errorRes?.let { setImageResource(it) }
    }
    return
  }

  val frameMillis = framePositions[currentIndex]

  // Use FFmpeg-based thumbnail extraction as final fallback
  CoroutineScope(Dispatchers.IO).launch {
    try {
      // Check cache first (only for first attempt to avoid multiple cache checks)
      if (currentIndex == 0) {
        val cachedBitmap = ThumbnailCache.loadThumbnail(context, uriString, viewWidth, viewHeight)
        if (cachedBitmap != null) {
          withContext(Dispatchers.Main) {
            setImageBitmap(cachedBitmap)
            Timber.d("Loaded thumbnail from disk cache for $uriString")
          }
          return@launch
        }
      }

      // Convert content URI to file path if needed
      val filePath = if (uriString.startsWith("content://")) {
        try {
          getFilePathFromContentUri(context, uriString.toUri())
        } catch (e: Exception) {
          Timber.w(e, "Could not convert content URI to file path: $uriString")
          null
        }
      } else if (uriString.startsWith("file://")) {
        uriString.substring(7) // Remove "file://" prefix
      } else {
        uriString // Already a file path
      }

      if (filePath == null) {
        Timber.e("Could not get file path for URI: $uriString, all fallbacks exhausted")
        withContext(Dispatchers.Main) {
          errorRes?.let { setImageResource(it) }
        }
        return@launch
      }

      // Convert milliseconds to seconds for FFmpeg
      val atTimeSeconds = frameMillis / 1000.0
      val bitmap = MPVLib.extractVideoThumbnail(filePath, viewWidth, viewHeight, atTimeSeconds)

      withContext(Dispatchers.Main) {
        if (bitmap != null) {
          setImageBitmap(bitmap)
          Timber.d("Successfully loaded thumbnail using FFmpeg at ${frameMillis}ms (${atTimeSeconds}s) for $uriString (dimension: ${maxOf(viewWidth, viewHeight)} from ${viewWidth}x${viewHeight})")

          // Save to cache for next time
          launch(Dispatchers.IO) {
            ThumbnailCache.saveThumbnail(context, uriString, bitmap, viewWidth, viewHeight)
          }
        } else {
          Timber.w("FFmpeg returned null bitmap at ${frameMillis}ms for $uriString, trying next position")
          loadThumbnailWithFFmpeg(uriString, errorRes, viewWidth, viewHeight, framePositions, currentIndex + 1)
        }
      }
    } catch (e: UnsatisfiedLinkError) {
      Timber.e(e, "FFmpeg native method not available for $uriString at ${frameMillis}ms, trying next position")
      withContext(Dispatchers.Main) {
        loadThumbnailWithFFmpeg(uriString, errorRes, viewWidth, viewHeight, framePositions, currentIndex + 1)
      }
    } catch (e: Exception) {
      Timber.e(e, "FFmpeg thumbnail extraction error for $uriString at ${frameMillis}ms, trying next position")
      withContext(Dispatchers.Main) {
        loadThumbnailWithFFmpeg(uriString, errorRes, viewWidth, viewHeight, framePositions, currentIndex + 1)
      }
    }
  }
}


/**
 * Convert content:// URI to file path
 * Tries to get the actual file path from the content URI
 */
private fun getFilePathFromContentUri(context: Context, uri: Uri): String? {
  return try {
    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
        cursor.getString(columnIndex)
      } else {
        null
      }
    }
  } catch (e: Exception) {
    Timber.e(e, "Failed to get file path from content URI: $uri")
    null
  }
}


/**
 * Clear all thumbnail cache
 * Useful for clearing cache when storage is low
 */
fun Context.clearThumbnailCache() {
  ThumbnailCache.clearCache(this)
}

/**
 * Get thumbnail cache statistics
 * Useful for displaying cache info in settings
 */
fun Context.getThumbnailCacheStats(): ThumbnailCache.CacheStats {
  return ThumbnailCache.getCacheStats(this)
}

