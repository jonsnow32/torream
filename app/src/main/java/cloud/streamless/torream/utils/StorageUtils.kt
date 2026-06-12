package cloud.streamless.torream.utils

import android.os.StatFs
import java.io.File

object StorageUtils {

    fun availableBytes(path: String): Long = try {
        StatFs(path).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    fun availableBytes(dir: File): Long = availableBytes(dir.path)

    /** Returns true if [path] has at least [requiredBytes] + 50 MB margin free. */
    fun hasEnoughSpace(path: String, requiredBytes: Long): Boolean =
        availableBytes(path) >= requiredBytes + 50 * 1024 * 1024

    fun hasEnoughSpace(dir: File, requiredBytes: Long): Boolean =
        hasEnoughSpace(dir.path, requiredBytes)

    /** Walk the full cause chain looking for ENOSPC / "No space left on device". */
    fun isNoSpaceError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val msg = cause.message ?: ""
            if (msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space left", ignoreCase = true) ||
                msg.contains("no free space", ignoreCase = true)) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun noSpaceMessage(path: String): String {
        val avail = availableBytes(path)
        return "Not enough storage space. Only ${formatBytes(avail)} available. Free up space and try again."
    }

    fun noSpaceMessage(dir: File): String = noSpaceMessage(dir.path)

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
