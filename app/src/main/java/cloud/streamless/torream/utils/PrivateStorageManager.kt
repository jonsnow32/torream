package cloud.streamless.torream.utils

import android.content.Context
import timber.log.Timber
import java.io.File

object PrivateStorageManager {
  private const val PRIVATE_DIR = "private"
  private const val PRIVATE_EXT = ".tpv"

  fun privateDir(context: Context): File =
    File(context.filesDir, PRIVATE_DIR).also { it.mkdirs() }

  /**
   * Copy [srcPath] into app-private storage with a .tpv extension, then delete the original.
   * Returns the new absolute path, or null on failure.
   */
  fun moveToPrivate(context: Context, srcPath: String, originalName: String): String? {
    return try {
      val src = File(srcPath)
      if (!src.exists()) return null
      val dstName = "${src.nameWithoutExtension}_${System.currentTimeMillis()}$PRIVATE_EXT"
      val dst = File(privateDir(context), dstName)
      src.copyTo(dst, overwrite = true)
      src.delete()
      dst.absolutePath
    } catch (e: Exception) {
      Timber.e(e, "Failed to move file to private storage: $srcPath")
      null
    }
  }

  /**
   * Restore a .tpv file from private storage to [targetDir] using [originalName].
   * Returns the restored absolute path, or null on failure.
   */
  fun moveFromPrivate(context: Context, privatePath: String, originalName: String, targetDir: File): String? {
    return try {
      val src = File(privatePath)
      if (!src.exists()) return null
      targetDir.mkdirs()
      val dst = File(targetDir, originalName)
      src.copyTo(dst, overwrite = true)
      src.delete()
      dst.absolutePath
    } catch (e: Exception) {
      Timber.e(e, "Failed to restore file from private storage: $privatePath")
      null
    }
  }
}
