package cloud.streamless.torream.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Disk cache for video thumbnails
 * Used as fallback cache for MediaMetadataRetriever and FFmpeg thumbnails
 * (Coil has its own built-in cache)
 */
object ThumbnailCache {
  private const val CACHE_DIR_NAME = "video_thumbnails"
  private const val MAX_CACHE_SIZE_MB = 100L
  private const val BITMAP_QUALITY = 85

  private fun getCacheDir(context: Context): File {
    val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
    if (!cacheDir.exists()) {
      cacheDir.mkdirs()
    }
    return cacheDir
  }

  /**
   * Generate cache key from URI string
   */
  private fun generateCacheKey(uriString: String, width: Int, height: Int): String {
    val input = "$uriString-$width-$height"
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) } + ".jpg"
  }

  /**
   * Save bitmap to disk cache
   */
  suspend fun saveThumbnail(
    context: Context,
    uriString: String,
    bitmap: Bitmap,
    width: Int,
    height: Int
  ) = withContext(Dispatchers.IO) {
    try {
      val cacheDir = getCacheDir(context)
      val cacheKey = generateCacheKey(uriString, width, height)
      val cacheFile = File(cacheDir, cacheKey)

      FileOutputStream(cacheFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, BITMAP_QUALITY, out)
        out.flush()
      }

      Timber.d("Saved thumbnail to cache: $cacheKey (${cacheFile.length() / 1024}KB)")

      // Clean up old cache if needed
      cleanupCacheIfNeeded(cacheDir)
    } catch (e: Exception) {
      Timber.e(e, "Failed to save thumbnail to cache for $uriString")
    }
  }

  /**
   * Load bitmap from disk cache
   */
  suspend fun loadThumbnail(
    context: Context,
    uriString: String,
    width: Int,
    height: Int
  ): Bitmap? = withContext(Dispatchers.IO) {
    try {
      val cacheDir = getCacheDir(context)
      val cacheKey = generateCacheKey(uriString, width, height)
      val cacheFile = File(cacheDir, cacheKey)

      if (cacheFile.exists()) {
        val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
        if (bitmap != null) {
          Timber.d("Loaded thumbnail from cache: $cacheKey")
          // Update last modified time for LRU
          cacheFile.setLastModified(System.currentTimeMillis())
          return@withContext bitmap
        }
      }
      null
    } catch (e: Exception) {
      Timber.e(e, "Failed to load thumbnail from cache for $uriString")
      null
    }
  }

  /**
   * Check if thumbnail exists in cache
   */
  fun hasThumbnail(context: Context, uriString: String, width: Int, height: Int): Boolean {
    return try {
      val cacheDir = getCacheDir(context)
      val cacheKey = generateCacheKey(uriString, width, height)
      val cacheFile = File(cacheDir, cacheKey)
      cacheFile.exists()
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Clean up cache if it exceeds max size
   * Uses LRU (Least Recently Used) strategy
   */
  private fun cleanupCacheIfNeeded(cacheDir: File) {
    try {
      val files = cacheDir.listFiles() ?: return
      val totalSize = files.sumOf { it.length() }
      val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024

      if (totalSize > maxSize) {
        Timber.d("Cache size ${totalSize / 1024 / 1024}MB exceeds limit ${MAX_CACHE_SIZE_MB}MB, cleaning up...")

        // Sort by last modified (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }

        var currentSize = totalSize
        for (file in sortedFiles) {
          if (currentSize <= maxSize) break

          val fileSize = file.length()
          if (file.delete()) {
            currentSize -= fileSize
            Timber.d("Deleted old cache file: ${file.name} (${fileSize / 1024}KB)")
          }
        }

        Timber.d("Cache cleanup complete, new size: ${currentSize / 1024 / 1024}MB")
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to cleanup cache")
    }
  }

  /**
   * Clear all cached thumbnails
   */
  fun clearCache(context: Context) {
    try {
      val cacheDir = getCacheDir(context)
      cacheDir.listFiles()?.forEach { it.delete() }
      Timber.d("Cleared all thumbnail cache")
    } catch (e: Exception) {
      Timber.e(e, "Failed to clear cache")
    }
  }

  /**
   * Get cache statistics
   */
  fun getCacheStats(context: Context): CacheStats {
    return try {
      val cacheDir = getCacheDir(context)
      val files = cacheDir.listFiles() ?: emptyArray()
      val totalSize = files.sumOf { it.length() }
      CacheStats(
        fileCount = files.size,
        totalSizeBytes = totalSize,
        totalSizeMB = totalSize / 1024.0 / 1024.0
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to get cache stats")
      CacheStats(0, 0, 0.0)
    }
  }

  data class CacheStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val totalSizeMB: Double
  )
}

