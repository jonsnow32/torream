package com.tv.apps.zippy.ui.library

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.TextView
import com.tv.apps.zippy.R
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * Helper class to calculate and display device storage statistics
 */
object StorageStatsHelper {

  data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val appBytes: Long,
    val freeBytes: Long
  ) {
    val usedPercent: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val appPercent: Float get() = if (totalBytes > 0) appBytes.toFloat() / totalBytes else 0f
    val freePercent: Float get() = if (totalBytes > 0) freeBytes.toFloat() / totalBytes else 0f
  }

  /**
   * Calculate storage statistics for internal storage
   */
  fun getStorageStats(context: Context): StorageStats {
    return try {
      // Get internal storage path
      val path = Environment.getDataDirectory()
      val stat = StatFs(path.path)

      val blockSize = stat.blockSizeLong
      val totalBlocks = stat.blockCountLong
      val availableBlocks = stat.availableBlocksLong

      val totalBytes = totalBlocks * blockSize
      val availableBytes = availableBlocks * blockSize
      val usedBytes = totalBytes - availableBytes

      // Calculate app data size (approximate)
      val appBytes = getAppDataSize(context)

      StorageStats(
        totalBytes = totalBytes,
        usedBytes = usedBytes - appBytes, // Exclude app data from "used"
        appBytes = appBytes,
        freeBytes = availableBytes
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to get storage stats")
      StorageStats(0, 0, 0, 0)
    }
  }

  /**
   * Calculate approximate app data size
   */
  private fun getAppDataSize(context: Context): Long {
    return try {
      val appDir = context.applicationInfo.dataDir

      var totalSize = 0L

      // Calculate data directory size
      appDir?.let { totalSize += calculateDirectorySize(File(it)) }


      totalSize
    } catch (e: Exception) {
      Timber.e(e, "Failed to calculate app data size")
      0L
    }
  }

  /**
   * Calculate directory size recursively
   */
  private fun calculateDirectorySize(directory: File): Long {
    return try {
      var size = 0L
      if (directory.exists()) {
        directory.listFiles()?.forEach { file ->
          size += if (file.isDirectory) {
            calculateDirectorySize(file)
          } else {
            file.length()
          }
        }
      }
      size
    } catch (e: Exception) {
      Timber.w(e, "Failed to calculate directory size: ${directory.path}")
      0L
    }
  }

  /**
   * Update storage stats UI
   */
  fun updateStorageUI(
    context: Context,
    containerView: View,
    usedView: View,
    appView: View,
    freeView: View,
    usedTextView: TextView,
    appTextView: TextView,
    freeTextView: TextView
  ) {
    try {
      val stats = getStorageStats(context)

      // Update progress bar weights
      val usedWeight = stats.usedPercent.coerceAtLeast(0.05f) // Minimum 5% to be visible
      val appWeight = stats.appPercent.coerceAtLeast(0.05f)
      val freeWeight = stats.freePercent.coerceAtLeast(0.05f)

      usedView.layoutParams = (usedView.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
        weight = usedWeight
      }
      appView.layoutParams = (appView.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
        weight = appWeight
      }
      freeView.layoutParams = (freeView.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
        weight = freeWeight
      }

      // Update text labels
      usedTextView.text = context.getString(
        R.string.storage_label_format,
        context.getString(R.string.storage_used),
        formatBytes(stats.usedBytes)
      )
      appTextView.text = context.getString(
        R.string.storage_label_format,
        context.getString(R.string.storage_app),
        formatBytes(stats.appBytes)
      )
      freeTextView.text = context.getString(
        R.string.storage_label_format,
        context.getString(R.string.storage_free),
        formatBytes(stats.freeBytes)
      )

      // Show the container
      containerView.visibility = View.VISIBLE
    } catch (e: Exception) {
      Timber.e(e, "Failed to update storage UI")
      containerView.visibility = View.GONE
    }
  }

  /**
   * Format bytes to human-readable format
   */
  fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.size - 1) {
      value /= 1024
      unitIndex++
    }

    return String.format(Locale.getDefault(), "%.2f %s", value, units[unitIndex])
  }
}
