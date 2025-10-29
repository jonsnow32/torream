package cloud.app.csplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.core.net.toUri
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Optimized thumbnail loader with memory caching and proper coroutine management
 * Solves RecyclerView scroll performance issues
 */
object ThumbnailLoader {

  // Memory cache: 1/8 of available memory for thumbnail cache
  private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
  private val cacheSize = maxMemory / 8

  private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      return bitmap.byteCount / 1024
    }
  }

  /**
   * Load thumbnail with caching and cancellation support
   */
  suspend fun loadThumbnail(
    context: Context,
    uriString: String,
    size: Size = Size(320, 180)
  ): Bitmap? = withContext(Dispatchers.IO) {
    // Check memory cache first
    memoryCache.get(uriString)?.let {
      return@withContext it
    }

    try {
      val uri = uriString.toUri()
      val bitmap = loadThumbnailFromUri(context, uri, size)

      // Cache the result
      bitmap?.let {
        memoryCache.put(uriString, it)
      }

      bitmap
    } catch (e: CancellationException) {
      // Coroutine was cancelled (ViewHolder recycled)
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Failed to load thumbnail for $uriString")
      null
    }
  }

  /**
   * Load thumbnail from MediaStore URI
   */
  private fun loadThumbnailFromUri(context: Context, uri: Uri, size: Size): Bitmap? {
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

  /**
   * Clear memory cache
   */
  fun clearCache() {
    memoryCache.evictAll()
  }

  /**
   * Get cache statistics for debugging
   */
  fun getCacheStats(): String {
    return "Cache size: ${memoryCache.size()}KB / ${memoryCache.maxSize()}KB, " +
           "Hit count: ${memoryCache.hitCount()}, Miss count: ${memoryCache.missCount()}"
  }
}

