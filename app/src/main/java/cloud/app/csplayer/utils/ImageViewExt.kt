package cloud.app.csplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.core.net.toUri
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
    loadThumbnailWithMediaMetadataRetriever(uriString, errorRes)
    return
  }

  val frameMillis = framePositions[currentIndex]

  try {
    val uri = uriString.toUri()

    val request = ImageRequest.Builder(context)
      .data(uri)
      .crossfade(true)
      // Coil automatically uses ImageView dimensions (no size() call needed)
      .scale(Scale.FIT)
      .parameters(Parameters.Builder().set("video_frame_micros", frameMillis * 1000).build())
      .apply {
        placeholderRes?.let { placeholder(it) }
        errorRes?.let { error(it) }
      }
      .target(
        onSuccess = { result ->
          // Check if bitmap is black/empty
          val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
          if (bitmap != null && isBitmapBlack(bitmap)) {
            Timber.w("Coil loaded black frame at ${frameMillis}ms for $uriString, trying next position")
            loadThumbnailWithCoil(uriString, placeholderRes, errorRes, framePositions, currentIndex + 1)
          } else {
            setImageDrawable(result)
            Timber.d("Successfully loaded thumbnail using Coil at ${frameMillis}ms for $uriString")
          }
        },
        onError = { error ->
          setImageDrawable(error)
        }
      )
      .listener(
        onError = { _, result ->
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
  errorRes: Int?
) {
  // Use FFmpeg-based thumbnail extraction as final fallback
  CoroutineScope(Dispatchers.IO).launch {
    try {
      // Get ImageView size
      val viewWidth = if (width > 0) width else 320
      val viewHeight = if (height > 0) height else 180

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

      val bitmap = cloud.app.csplayer.ui.player.mpv.MPVLib.extractVideoThumbnail(filePath, viewWidth)

      withContext(Dispatchers.Main) {
        if (bitmap != null && !isBitmapBlack(bitmap)) {
          setImageBitmap(bitmap)
          Timber.d("Successfully loaded thumbnail using FFmpeg for $uriString")
        } else {
          Timber.e("FFmpeg extraction failed or returned black bitmap for $uriString, all fallbacks exhausted")
          errorRes?.let { setImageResource(it) }
        }
      }
    } catch (e: UnsatisfiedLinkError) {
      Timber.e(e, "FFmpeg native method not available for $uriString, all fallbacks exhausted")
      withContext(Dispatchers.Main) {
        errorRes?.let { setImageResource(it) }
      }
    } catch (e: Exception) {
      Timber.e(e, "FFmpeg thumbnail extraction error for $uriString, all fallbacks exhausted")
      withContext(Dispatchers.Main) {
        errorRes?.let { setImageResource(it) }
      }
    }
  }
}

private fun ImageView.loadThumbnailWithMediaMetadataRetriever(
  uriString: String,
  errorRes: Int?
) {
  // Use MediaMetadataRetriever as second fallback
  CoroutineScope(Dispatchers.IO).launch {
    try {
      // Get ImageView size
      val viewWidth = if (width > 0) width else 320
      val viewHeight = if (height > 0) height else 180

      val uri = uriString.toUri()
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(context, uri)

      val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        retriever.getScaledFrameAtTime(
          1000000L,
          MediaMetadataRetriever.OPTION_CLOSEST,
          viewWidth,
          viewHeight
        )
      } else {
        @Suppress("DEPRECATION")
        retriever.frameAtTime
      }

      retriever.release()

      withContext(Dispatchers.Main) {
        if (bitmap != null && !isBitmapBlack(bitmap)) {
          setImageBitmap(bitmap)
          Timber.d("Successfully loaded thumbnail using MediaMetadataRetriever for $uriString")
        } else {
          Timber.w("MediaMetadataRetriever returned null or black bitmap for $uriString, trying FFmpeg")
          loadThumbnailWithFFmpeg(uriString, errorRes)
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "MediaMetadataRetriever failed for $uriString, trying FFmpeg")
      withContext(Dispatchers.Main) {
        loadThumbnailWithFFmpeg(uriString, errorRes)
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

