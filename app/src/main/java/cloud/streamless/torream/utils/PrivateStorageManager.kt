package cloud.streamless.torream.utils

import android.content.Context
import timber.log.Timber
import java.io.File

object PrivateStorageManager {
  private const val PRIVATE_DIR = "private"
  const val PRIVATE_EXT = ".tpv"

  fun privateDir(context: Context): File =
    File(context.filesDir, PRIVATE_DIR).also { it.mkdirs() }

  /**
   * Move [srcPath] (plain path, file:// or content:// URI) into app-private storage.
   * Files are renamed to .tpv; directories are moved as-is.
   * Returns the new absolute path, or null on failure.
   */
  fun moveToPrivate(context: Context, srcPath: String, originalName: String): String? {
    return try {
      val timestamp = System.currentTimeMillis()
      val plainFile = File(srcPath)

      if (plainFile.exists()) {
        // Plain filesystem path
        val dst = if (plainFile.isDirectory) {
          File(privateDir(context), "${plainFile.name}_$timestamp")
        } else {
          File(privateDir(context), "${plainFile.nameWithoutExtension}_$timestamp$PRIVATE_EXT")
        }
        if (plainFile.isDirectory) plainFile.copyRecursively(dst, overwrite = true)
        else plainFile.copyTo(dst, overwrite = true)
        plainFile.deleteRecursively()
        dst.absolutePath
      } else {
        // content:// or file:// URI — use UnifiedFileFactory
        val unifiedSrc = UnifiedFileFactory.fromPath(context, srcPath) ?: return null
        if (!unifiedSrc.exists()) return null
        val baseName = originalName.substringBeforeLast('.')
        val dst = File(privateDir(context), "${baseName}_$timestamp$PRIVATE_EXT")
        unifiedSrc.openInputStream().use { input ->
          dst.outputStream().use { output -> input.copyTo(output) }
        }
        unifiedSrc.delete()
        dst.absolutePath
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to move to private storage: $srcPath")
      null
    }
  }

  /**
   * Restore a private file or directory back to [targetDir] using [originalName].
   * Private files are always in filesDir so plain File works.
   * Returns the restored absolute path, or null on failure.
   */
  fun moveFromPrivate(context: Context, privatePath: String, originalName: String, targetDir: File): String? {
    return try {
      val src = File(privatePath)
      if (!src.exists()) return null
      targetDir.mkdirs()
      val dst = File(targetDir, originalName)
      if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
      else src.copyTo(dst, overwrite = true)
      src.deleteRecursively()
      dst.absolutePath
    } catch (e: Exception) {
      Timber.e(e, "Failed to restore from private storage: $privatePath")
      null
    }
  }
}
