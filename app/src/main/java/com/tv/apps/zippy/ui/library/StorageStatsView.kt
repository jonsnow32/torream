package com.tv.apps.zippy.ui.library

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.tv.apps.zippy.databinding.ViewStorageStatsBinding
import timber.log.Timber

/**
 * Custom view to display storage statistics with a visual progress bar
 */
class StorageStatsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  private val binding: ViewStorageStatsBinding

  init {
    binding = ViewStorageStatsBinding.inflate(LayoutInflater.from(context), this, true)
    orientation = VERTICAL
  }

  /**
   * Update the storage stats display
   */
  fun updateStats(stats: StorageStatsHelper.StorageStats) {
    try {
      // Update progress bar weights
      val usedWeight = stats.usedPercent.coerceAtLeast(0.05f) // Minimum 5% to be visible
      val appWeight = stats.appPercent.coerceAtLeast(0.05f)
      val freeWeight = stats.freePercent.coerceAtLeast(0.05f)

      binding.storageUsedBar.layoutParams = (binding.storageUsedBar.layoutParams as LayoutParams).apply {
        weight = usedWeight
      }
      binding.storageAppBar.layoutParams = (binding.storageAppBar.layoutParams as LayoutParams).apply {
        weight = appWeight
      }
      binding.storageFreeBar.layoutParams = (binding.storageFreeBar.layoutParams as LayoutParams).apply {
        weight = freeWeight
      }

      // Update text labels
      binding.storageUsedText.text = context.getString(
        com.tv.apps.zippy.R.string.storage_label_format,
        context.getString(com.tv.apps.zippy.R.string.storage_used),
        StorageStatsHelper.formatBytes(stats.usedBytes)
      )
      binding.storageAppText.text = context.getString(
        com.tv.apps.zippy.R.string.storage_label_format,
        context.getString(com.tv.apps.zippy.R.string.storage_app),
        StorageStatsHelper.formatBytes(stats.appBytes)
      )
      binding.storageFreeText.text = context.getString(
        com.tv.apps.zippy.R.string.storage_label_format,
        context.getString(com.tv.apps.zippy.R.string.storage_free),
        StorageStatsHelper.formatBytes(stats.freeBytes)
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to update storage stats")
    }
  }

  /**
   * Load and display storage stats from the system
   */
  fun loadAndDisplay() {
    try {
      val stats = StorageStatsHelper.getStorageStats(context)
      updateStats(stats)
    } catch (e: Exception) {
      Timber.e(e, "Failed to load storage stats")
    }
  }
}
