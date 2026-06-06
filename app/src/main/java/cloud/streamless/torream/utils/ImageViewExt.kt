package cloud.streamless.torream.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.core.net.toUri
import cloud.streamless.torream.ui.player.mpv.MPVLib
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.FileInputStream


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
      // Use multiple frame position fallbacks: try beginning, then various positions
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

      // Get file path using SAF (Storage Access Framework) - NO COPY needed
      val filePath = when {
        uriString.startsWith("content://") -> {
          // Use SAF with ParcelFileDescriptor for efficient access
          try {
            getFilePathFromContentUriSAF(context, uriString.toUri())
          } catch (e: Exception) {
            Timber.w(e, "Could not get file path from content URI: $uriString")
            null
          }
        }
        uriString.startsWith("file://") -> {
          uriString.substring(7) // Remove "file://" prefix
        }
        else -> {
          uriString // Already a file path
        }
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
 * Convert content:// URI to file path using SAF (Storage Access Framework)
 *
 * Uses ParcelFileDescriptor for efficient access to large files WITHOUT copying.
 * This approach:
 * - Works with scoped storage (Android 10+)
 * - Handles large files efficiently (no RAM copy)
 * - Supports both MediaStore and SAF URIs (including document provider URIs)
 * - Falls back to legacy DATA column for MediaStore URIs
 * - Uses /proc/self/fd/ trick for SAF document URIs
 *
 * @param context Application context
 * @param uri The content:// URI to resolve
 * @return File path if available, null otherwise
 */
private fun getFilePathFromContentUriSAF(context: Context, uri: Uri): String? {
  // Check if this is a SAF document URI (from document picker or tree)
  val authority = uri.authority
  if (authority != null && (
      authority.contains("externalstorage.documents") ||
      authority.contains("downloads.documents") ||
      authority.contains("media.documents")
    )) {
    Timber.d("Detected SAF document URI: $uri")
    return getFilePathFromSAFDocumentUri(context, uri)
  }

  // For MediaStore URIs, try the DATA column
  return try {
    context.contentResolver.query(
      uri,
      arrayOf(android.provider.MediaStore.Video.Media.DATA),
      null,
      null,
      null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
        if (columnIndex >= 0) {
          val path = cursor.getString(columnIndex)
          if (!path.isNullOrEmpty()) {
            Timber.d("✓ Got file path from MediaStore DATA column: $path")
            return@use path
          }
        }
      }
      null
    }
  } catch (e: Exception) {
    Timber.w(e, "Failed to get file path from MediaStore DATA column for URI: $uri")
    null
  }
}

/**
 * Get file path from SAF document URI
 *
 * For document URIs like:
 * content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2Fhttp/BigBuckBunny.mp4
 *
 * This method:
 * 1. Tries to parse the document ID to get real path (for external storage)
 * 2. Falls back to /proc/self/fd/ trick using ParcelFileDescriptor
 *
 * The /proc/self/fd/ approach allows FFmpeg to access the file without copying:
 * - Open ParcelFileDescriptor
 * - Get file descriptor number
 * - Pass /proc/self/fd/{fd} path to FFmpeg
 * - FFmpeg reads directly from the file descriptor
 */
private fun getFilePathFromSAFDocumentUri(context: Context, uri: Uri): String? {
  // Try to parse document ID for external storage URIs
  if (uri.authority?.contains("externalstorage.documents") == true) {
    try {
      val documentId = android.provider.DocumentsContract.getDocumentId(uri)
      Timber.d("Document ID: $documentId")

      // Document ID format: "primary:Movies/http/BigBuckBunny.mp4"
      if (documentId.startsWith("primary:")) {
        val path = documentId.substring("primary:".length)
        val externalStoragePath = android.os.Environment.getExternalStorageDirectory()
        val fullPath = "$externalStoragePath/$path"

        // Verify file exists
        if (java.io.File(fullPath).exists()) {
          Timber.d("✓ Resolved SAF document URI to real path: $fullPath")
          return fullPath
        } else {
          Timber.w("Parsed path doesn't exist: $fullPath")
        }
      }
    } catch (e: Exception) {
      Timber.w(e, "Failed to parse document ID for URI: $uri")
    }
  }

  // Fallback: Use /proc/self/fd/ trick with ParcelFileDescriptor
  // This allows direct file descriptor access without copying
  return try {
    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: run {
      Timber.e("Could not open ParcelFileDescriptor for URI: $uri")
      return null
    }

    // Detach FD to keep it alive for FFmpeg access (intentional resource "leak")
    // The OS will clean it up when no longer referenced by any process
    val fd = pfd.detachFd()
    val fdPath = "/proc/self/fd/$fd"
    Timber.d("✓ Using file descriptor path: $fdPath (fd=$fd)")

    // Note: detachFd() transfers ownership to native code (FFmpeg)
    // The FD remains valid until FFmpeg closes it or the process exits
    fdPath
  } catch (e: Exception) {
    Timber.e(e, "Failed to get file descriptor for URI: $uri")
    null
  }
}

/**
 * Read large file efficiently using SAF without copying to memory
 *
 * Example usage for processing large video files:
 * ```
 * val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
 * val inputStream = FileInputStream(pfd.fileDescriptor)
 *
 * val buffer = ByteArray(1024 * 1024) // 1MB buffer
 * var bytesRead: Int
 *
 * while (inputStream.read(buffer).also { bytesRead = it } != -1) {
 *     // Process chunk of data
 * }
 *
 * inputStream.close()
 * pfd.close()
 * ```
 *
 * This approach allows you to:
 * - Pass FileDescriptor directly to FFmpeg
 * - Use with ExoPlayer
 * - Use with Okio
 * - Wrap in RandomAccessFile
 * - Stream data in chunks without loading entire file to RAM
 */
@Suppress("unused")
private fun readLargeFileWithSAF(uri: Uri, context: Context, onChunkRead: (ByteArray, Int) -> Unit) {
  val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: run {
    Timber.e("Cannot open ParcelFileDescriptor for URI: $uri")
    return
  }

  try {
    val inputStream = FileInputStream(pfd.fileDescriptor)
    val buffer = ByteArray(1024 * 1024) // 1MB buffer
    var bytesRead: Int

    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
      onChunkRead(buffer, bytesRead)
    }

    inputStream.close()
  } catch (e: Exception) {
    Timber.e(e, "Error reading large file with SAF: $uri")
  } finally {
    pfd.close()
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

