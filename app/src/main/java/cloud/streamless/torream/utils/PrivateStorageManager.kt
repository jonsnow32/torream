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
   * Move [srcPath] (file or directory) into app-private storage, renaming files to .tpv.
   * Returns the new absolute path, or null on failure.
   */
  fun moveToPrivate(context: Context, srcPath: String, originalName: String): String? {
    return try {
      val src = File(srcPath)
      if (!src.exists()) return null
      val timestamp = System.currentTimeMillis()
      val dst = if (src.isDirectory) {
        File(privateDir(context), "${src.name}_$timestamp")
      } else {
        File(privateDir(context), "${src.nameWithoutExtension}_$timestamp$PRIVATE_EXT")
      }
      if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
      else src.copyTo(dst, overwrite = true)
      src.deleteRecursively()
      dst.absolutePath
    } catch (e: Exception) {
      Timber.e(e, "Failed to move to private storage: $srcPath")
      null
    }
  }

  /**
   * Restore a private file or directory back to [targetDir] using [originalName].
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
