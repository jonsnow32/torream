package cloud.streamless.torream.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.lagradost.safefile.SafeFile
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Wrapper to use SafeFile library with UnifiedFile interface
 * This allows safe file operations across different storage backends
 *
 * @param cachedUri Optional URI to use instead of safeFile.uri() - used for document tree URIs
 */
class SafeFileWrapper(
  private val context: Context,
  private val safeFile: SafeFile,
  private val cachedUri: Uri? = null
) : UnifiedFile {

  override fun createFile(displayName: String, mimeType: String): UnifiedFile? {
    return try {
      val newFile = safeFile.createFile(displayName)
      if (newFile != null) SafeFileWrapper(context, newFile) else null
    } catch (e: Exception) {
      Timber.e(e, "Failed to create file: $displayName")
      null
    }
  }

  override fun createDirectory(displayName: String): UnifiedFile? {
    return try {
      val newDir = safeFile.createDirectory(displayName)
      if (newDir != null) SafeFileWrapper(context, newDir) else null
    } catch (e: Exception) {
      Timber.e(e, "Failed to create directory: $displayName")
      null
    }
  }

  override val uri: Uri get() = cachedUri ?: safeFile.uri() ?: throw IllegalStateException("URI is null")

  override val name: String get() = safeFile.name() ?: "unknown"

  override val type: String? get() = safeFile.type()

  override val filePath: String? get() = safeFile.filePath()

  override val isDirectory: Boolean get() = safeFile.isDirectory() ?: false

  override val isFile: Boolean get() = safeFile.isFile() ?: false

  override fun lastModified(): Long = safeFile.lastModified() ?: 0L

  override fun length(): Long = safeFile.length() ?: 0L

  override fun canRead(): Boolean = safeFile.canRead() ?: false

  override fun canWrite(): Boolean = safeFile.canWrite() ?: false

  override fun delete(): Boolean {
    return try {
      safeFile.delete() ?: false
    } catch (e: Exception) {
      Timber.e(e, "Failed to delete: $name")
      false
    }
  }

  override fun exists(): Boolean = safeFile.exists() ?: false

  override fun listFiles(): Array<UnifiedFile>? {
    return try {
      safeFile.listFiles()?.map { SafeFileWrapper(context, it) }?.toTypedArray()
    } catch (e: Exception) {
      Timber.e(e, "Failed to list files in: $name")
      null
    }
  }

  override fun findFile(displayName: String, ignoreCase: Boolean): UnifiedFile? {
    return try {
      val file = safeFile.findFile(displayName)
      file?.let { SafeFileWrapper(context, it) }
    } catch (e: Exception) {
      Timber.e(e, "Failed to find file: $displayName")
      null
    }
  }

  override fun renameTo(displayName: String): Boolean {
    return try {
      safeFile.renameTo(displayName) ?: false
    } catch (e: Exception) {
      Timber.e(e, "Failed to rename to: $displayName")
      false
    }
  }

  override fun openInputStream(): InputStream {
    return safeFile.openInputStream() ?: throw IllegalStateException("Cannot open input stream")
  }

  override fun openOutputStream(append: Boolean): OutputStream {
    return safeFile.openOutputStream(append) ?: throw IllegalStateException("Cannot open output stream")
  }
}

/**
 * Factory object to create UnifiedFile instances
 */
object UnifiedFileFactory {
  /**
   * Create UnifiedFile from a path string that can be either:
   * - A file system path: /storage/emulated/0/Download/file.txt
   * - A content URI: content://...
   * - A file URI: file:///storage/...
   */
  fun fromPath(context: Context, path: String): UnifiedFile? {
    return try {
      when {
        // Content URI
        path.startsWith("content://", ignoreCase = true) -> {
          fromUri(context, Uri.parse(path))
        }
        // File URI
        path.startsWith("file://", ignoreCase = true) -> {
          fromUri(context, Uri.parse(path))
        }
        // File system path
        else -> {
          fromFile(context, File(path))
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to create UnifiedFile from path: $path")
      null
    }
  }

  fun fromUri(context: Context, uri: Uri): UnifiedFile? {
    return try {
      // Check if uri has no scheme - likely a file system path string
      if (uri.scheme.isNullOrBlank()) {
        // This is probably a file system path that was incorrectly converted to Uri
        val pathString = uri.toString()
        Timber.d("No URI scheme detected, treating as file path: $pathString")
        val file = File(pathString)
        return fromFile(context, file)
      }

      // Special handling for document tree URIs from downloads provider
      // Format: content://com.android.providers.downloads.documents/tree/raw:/storage/emulated/0/Download
      if (uri.scheme == "content" &&
          uri.authority == "com.android.providers.downloads.documents" &&
          uri.pathSegments.isNotEmpty() && uri.pathSegments[0] == "tree") {

        try {
          // For document tree URIs, the path is usually encoded as:
          // /tree/raw:/path/to/directory
          val fullPath = uri.path ?: return null

          // Extract the actual file path from the URI path
          // Pattern: /tree/raw:/storage/emulated/0/Download
          val treePrefix = "/tree/raw:"
          if (fullPath.startsWith(treePrefix)) {
            val actualPath = fullPath.substring(treePrefix.length)
            Timber.d("Detected document tree URI, converting to path: $actualPath")

            val file = File(actualPath)
            if (file.exists() && file.isDirectory) {
              // Create file-based SafeFile but preserve the original URI
              val safeFile = SafeFile.fromFile(context, file)
              if (safeFile != null) {
                return SafeFileWrapper(context, safeFile, cachedUri = uri)
              } else {
                Timber.w("Failed to create SafeFile from extracted path: $actualPath")
              }
            } else {
              Timber.w("Document tree path doesn't exist or not a directory: $actualPath")
            }
          }
        } catch (e: Exception) {
          Timber.w(e, "Failed to parse document tree URI: $uri")
        }
      }

      // Standard SafeFile handling for other URI types
      val safeFile = SafeFile.fromUri(context, uri) ?: return null
      SafeFileWrapper(context, safeFile)
    } catch (e: Exception) {
      Timber.e(e, "Failed to create UnifiedFile from URI: $uri")
      null
    }
  }

  fun fromFile(context: Context, file: File): UnifiedFile? {
    return try {
      val safeFile = SafeFile.fromFile(context, file) ?: return null
      SafeFileWrapper(context, safeFile)
    } catch (e: Exception) {
      Timber.e(e, "Failed to create UnifiedFile from file: ${file.absolutePath}")
      null
    }
  }

  fun getDownloadsDirectory(context: Context): UnifiedFile? {
    return try {
      val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: return null
      // Ensure directory exists
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      if (!downloadsDir.exists()) {
        Timber.e("Failed to create downloads directory: ${downloadsDir.absolutePath}")
        return null
      }
      fromFile(context, downloadsDir)
    } catch (e: Exception) {
      Timber.e(e, "Failed to get downloads directory")
      null
    }
  }

  fun getDownloadsDirectoryPublic(context: Context): UnifiedFile? {
    return try {
      val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      // Ensure directory exists
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      if (!downloadsDir.exists()) {
        Timber.e("Failed to create public downloads directory: ${downloadsDir.absolutePath}")
        return null
      }
      fromFile(context, downloadsDir)
    } catch (e: Exception) {
      Timber.e(e, "Failed to get public downloads directory")
      null
    }
  }
}

