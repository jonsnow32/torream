package cloud.streamless.torream.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
   * Restore a private file/directory to [preferredTargetDir].
   * Falls back to app-specific external Movies dir if the preferred dir is not writable
   * (e.g., shared storage on Android 11+ without MANAGE_EXTERNAL_STORAGE).
   *
   * Returns restored absolute path, null on failure, or "" if the source file no longer exists
   * (caller should still clean up the DB record in that case).
   */
  fun moveFromPrivate(context: Context, privatePath: String, originalName: String, preferredTargetDir: File): String? {
    val src = File(privatePath)
    if (!src.exists()) return ""  // signal: source gone, caller should update DB anyway

    val candidates = listOfNotNull(
      preferredTargetDir,
      context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
      context.getExternalFilesDir(null)
    )

    for (targetDir in candidates) {
      try {
        targetDir.mkdirs()
        val dst = File(targetDir, originalName)
        if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
        else src.copyTo(dst, overwrite = true)
        src.deleteRecursively()
        // Notify MediaStore so the file appears in gallery/library
        try {
          context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
            data = Uri.fromFile(dst)
          })
        } catch (_: Exception) {}
        return dst.absolutePath
      } catch (e: Exception) {
        Timber.w(e, "Cannot restore to ${targetDir.absolutePath}, trying next fallback")
      }
    }
    return null
  }
}
