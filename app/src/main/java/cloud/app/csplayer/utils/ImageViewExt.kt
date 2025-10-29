package cloud.app.csplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.core.net.toUri
import cloud.app.csplayer.ui.player.mpv.MPVLib
import coil.request.ImageRequest
import coil.request.Parameters
import coil.size.Scale
import coil.Coil
import kotlinx.coroutines.*
import timber.log.Timber


/**
 * Load thumbnail from MediaStore URI with automatic fallback chain
 *
 * Fallback order:
 * 1. Coil (fast, cached)
 * 2. MediaMetadataRetriever (reliable)
 * 3. FFmpeg (last resort for problem videos)
 *
 * Features:
 * - Automatic ImageView size detection (like Glide's BaseGlideUrlLoader)
 * - Automatic memory + disk caching
 * - Lifecycle-aware (auto-cancel on view detach)
 * - Black frame detection and retry
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
  // Start with Coil first (fastest, has caching)
  loadThumbnailWithCoil(
    uriString,
    placeholderRes,
    errorRes,
    listOf(500L, 1000L, 2000L, 5000L)
  )
}


@OptIn(DelicateCoroutinesApi::class)
private fun ImageView.loadThumbnailWithCoil(
  uriString: String,
  placeholderRes: Int?,
  errorRes: Int?,
  framePositions: List<Long>,
  currentIndex: Int = 0
) {
  if (currentIndex >= framePositions.size) {
    // All Coil attempts failed, fallback to MediaMetadataRetriever
    Timber.w("All Coil frame positions failed for $uriString, trying MediaMetadataRetriever")
    // Get ImageView size
    val viewWidth = if (width > 0) width else 320
    val viewHeight = if (height > 0) height else 180
    loadThumbnailWithMediaMetadataRetriever(uriString, errorRes, viewWidth, viewHeight, framePositions)
    return
  }

  val frameMillis = framePositions[currentIndex]

  try {
    val uri = uriString.toUri()

    val request = ImageRequest.Builder(context)
      .data(uri)
      .crossfade(true)
      .target(this) // Set target to ImageView để Coil tự động lấy kích thước
      .scale(Scale.FIT)
      // Enable disk and memory caching
      .diskCachePolicy(coil.request.CachePolicy.ENABLED)
      .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
      .parameters(Parameters.Builder().set("video_frame_micros", frameMillis * 1000).build())
      .apply {
        placeholderRes?.let { placeholder(it) }
        errorRes?.let { error(it) }
      }
      .listener(
        onSuccess = { request, result ->
          // Check if bitmap is black/empty
          val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
          if (bitmap != null && isBitmapBlack(bitmap)) {
            Timber.w("Coil loaded black frame at ${frameMillis}ms for $uriString, trying next position")
            loadThumbnailWithCoil(uriString, placeholderRes, errorRes, framePositions, currentIndex + 1)
          } else {
            Timber.d("Successfully loaded thumbnail using Coil at ${frameMillis}ms for $uriString")
          }
        },
        onError = { request, result ->
          Timber.e(result.throwable, "Coil failed at ${frameMillis}ms for $uriString")
          // Try next frame position with Coil
          loadThumbnailWithCoil(
            uriString,
            placeholderRes,
            errorRes,
            framePositions,
            currentIndex + 1
          )
        }
      )
      .build()

    Coil.imageLoader(context).enqueue(request)
  } catch (e: Exception) {
    Timber.e(e, "Error setting up Coil thumbnail load for $uriString")
    loadThumbnailWithCoil(
      uriString,
      placeholderRes,
      errorRes,
      framePositions,
      currentIndex + 1
    )
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
        if (cachedBitmap != null && !isBitmapBlack(cachedBitmap)) {
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
        if (bitmap != null && !isBitmapBlack(bitmap)) {
          setImageBitmap(bitmap)
          Timber.d("Successfully loaded thumbnail using FFmpeg at ${frameMillis}ms (${atTimeSeconds}s) for $uriString (dimension: ${maxOf(viewWidth, viewHeight)} from ${viewWidth}x${viewHeight})")

          // Save to cache for next time
          launch(Dispatchers.IO) {
            ThumbnailCache.saveThumbnail(context, uriString, bitmap, viewWidth, viewHeight)
          }
        } else {
          Timber.w("FFmpeg returned null or black bitmap at ${frameMillis}ms for $uriString, trying next position")
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

private fun ImageView.loadThumbnailWithMediaMetadataRetriever(
  uriString: String,
  errorRes: Int?,
  viewWidth: Int,
  viewHeight: Int,
  framePositions: List<Long>,
  currentIndex: Int = 0
) {
  if (currentIndex >= framePositions.size) {
    // All MediaMetadataRetriever attempts failed, fallback to FFmpeg
    Timber.w("All MediaMetadataRetriever frame positions failed for $uriString, trying FFmpeg")
    loadThumbnailWithFFmpeg(uriString, errorRes, viewWidth, viewHeight, framePositions)
    return
  }

  val frameMillis = framePositions[currentIndex]

  // Use MediaMetadataRetriever as second fallback
  CoroutineScope(Dispatchers.IO).launch {
    try {
      // Check cache first (only for first attempt to avoid multiple cache checks)
      if (currentIndex == 0) {
        val cachedBitmap = ThumbnailCache.loadThumbnail(context, uriString, viewWidth, viewHeight)
        if (cachedBitmap != null && !isBitmapBlack(cachedBitmap)) {
          withContext(Dispatchers.Main) {
            setImageBitmap(cachedBitmap)
            Timber.d("Loaded thumbnail from disk cache for $uriString")
          }
          return@launch
        }
      }

      val uri = uriString.toUri()
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(context, uri)

      val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        retriever.getScaledFrameAtTime(
          frameMillis * 1000L, // Convert to microseconds
          MediaMetadataRetriever.OPTION_CLOSEST,
          viewWidth,
          viewHeight
        )
      } else {
        @Suppress("DEPRECATION")
        retriever.getFrameAtTime(frameMillis * 1000L) // Convert to microseconds
      }

      retriever.release()

      withContext(Dispatchers.Main) {
        if (bitmap != null && !isBitmapBlack(bitmap)) {
          setImageBitmap(bitmap)
          Timber.d("Successfully loaded thumbnail using MediaMetadataRetriever at ${frameMillis}ms for $uriString")

          // Save to cache for next time
          launch(Dispatchers.IO) {
            ThumbnailCache.saveThumbnail(context, uriString, bitmap, viewWidth, viewHeight)
          }
        } else {
          Timber.w("MediaMetadataRetriever returned null or black bitmap at ${frameMillis}ms for $uriString, trying next position")
          loadThumbnailWithMediaMetadataRetriever(uriString, errorRes, viewWidth, viewHeight, framePositions, currentIndex + 1)
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "MediaMetadataRetriever failed at ${frameMillis}ms for $uriString, trying next position")
      withContext(Dispatchers.Main) {
        loadThumbnailWithMediaMetadataRetriever(uriString, errorRes, viewWidth, viewHeight, framePositions, currentIndex + 1)
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
 * Check if a bitmap is mostly black (corrupted/empty frame)
 * Samples pixels to determine if bitmap is black
 */
private fun isBitmapBlack(bitmap: Bitmap): Boolean {
  return try {
    val width = bitmap.width
    val height = bitmap.height

    // Sample 25 pixels across the bitmap
    val sampleSize = 5
    var blackPixels = 0
    var totalSamples = 0

    for (x in 0 until sampleSize) {
      for (y in 0 until sampleSize) {
        val pixelX = (x * width) / sampleSize
        val pixelY = (y * height) / sampleSize

        if (pixelX < width && pixelY < height) {
          val pixel = bitmap.getPixel(pixelX, pixelY)
          val red = (pixel shr 16) and 0xFF
          val green = (pixel shr 8) and 0xFF
          val blue = pixel and 0xFF

          // Consider pixel black if all RGB values are below 30
          if (red < 30 && green < 30 && blue < 30) {
            blackPixels++
          }
          totalSamples++
        }
      }
    }

    // If more than 90% of sampled pixels are black, consider bitmap black
    blackPixels.toFloat() / totalSamples > 0.9f
  } catch (e: Exception) {
    Timber.e(e, "Error checking if bitmap is black")
    false // If we can't check, assume it's not black
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

