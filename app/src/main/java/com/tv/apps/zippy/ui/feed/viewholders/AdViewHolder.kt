package com.tv.apps.zippy.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.tv.apps.zippy.ads.AdManager
import com.tv.apps.zippy.databinding.ItemNativeAdHolderBinding
import com.tv.apps.zippy.ui.feed.FeedData
import com.tv.apps.zippy.ui.feed.FeedViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewHolder for displaying native ads in the feed
 * Uses AdManager waterfall system to show native ads
 */
class AdViewHolder(
  parent: ViewGroup,
  val binding: ItemNativeAdHolderBinding = ItemNativeAdHolderBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  ),
  private val adManager: AdManager? = null // Injected from adapter
) : FeedViewHolder<FeedData.AdItem>(binding.root) {

  companion object {
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 500L
  }

  private var loadJob: Job? = null
  private var isAdLoaded = false

  override fun bind(feed: FeedData.AdItem) {
    // Cancel previous load job if any
    loadJob?.cancel()

    // Clear previous ad
    binding.root.removeAllViews()
    isAdLoaded = false

    // Check if this is a native ad placeholder
    if (feed.type != FeedData.Type.Ad) {
      Timber.d("AdItem ${feed.id} is not a native ad placeholder")
      return
    }

    // Load ad using AdManager with retry logic - randomly choose between native and banner
    // If first choice fails, fallback to the other type
    loadJob = CoroutineScope(Dispatchers.Main).launch {
      try {
        // Randomly choose between native ad and banner ad
        val useNativeAdFirst = (0..1).random() == 0

        Timber.d("Attempting to load ${if (useNativeAdFirst) "native" else "banner"} ad first for AdItem ${feed.id}")

        // Try first ad type
        var success = if (useNativeAdFirst) {
          loadNativeAdWithRetry(feed.id)
        } else {
          loadBannerAdWithRetry(feed.id)
        }

        // If first attempt failed, try fallback
        if (!success) {
          val fallbackType = if (useNativeAdFirst) "banner" else "native"
          Timber.d("First ad type failed, falling back to $fallbackType ad for AdItem ${feed.id}")

          success = if (useNativeAdFirst) {
            loadBannerAdWithRetry(feed.id)
          } else {
            loadNativeAdWithRetry(feed.id)
          }
        }

        if (success) {
          isAdLoaded = true
          Timber.d("Ad loaded successfully for AdItem ${feed.id}")
        } else {
          Timber.w("Failed to load both ad types for AdItem ${feed.id}")
          // Hide the ad container if loading failed
          binding.root.layoutParams.height = 1
        }
      } catch (e: Exception) {
        Timber.e(e, "Error loading ad for AdItem ${feed.id}")
        binding.root.layoutParams.height = 1
      }
    }
  }

  /**
   * Load native ad with retry logic
   */
  private suspend fun loadNativeAdWithRetry(feedId: String, attempt: Int = 1): Boolean {
    // Check if AdManager is null
    if (adManager == null) {
      Timber.w("AdManager is null, cannot load native ad")
      return false
    }

    // Check if AdManager is initialized
    if (!adManager.isReady()) {
      if (attempt <= MAX_RETRY_ATTEMPTS) {
        Timber.d("AdManager not ready, retry attempt $attempt/$MAX_RETRY_ATTEMPTS")
        delay(RETRY_DELAY_MS * attempt) // Exponential backoff
        return loadNativeAdWithRetry(feedId, attempt + 1)
      } else {
        Timber.w("AdManager not ready after $MAX_RETRY_ATTEMPTS attempts")
        return false
      }
    }

    return adManager.createNativeAd(
      context = binding.root.context,
      container = binding.root
    )
  }

  private suspend fun loadBannerAdWithRetry(feedId: String, attempt: Int = 1): Boolean {
    // Check if AdManager is null
    if (adManager == null) {
      Timber.w("AdManager is null, cannot load banner ad")
      return false
    }

    // Check if AdManager is initialized
    if (!adManager.isReady()) {
      if (attempt <= MAX_RETRY_ATTEMPTS) {
        Timber.d("AdManager not ready, retry attempt $attempt/${MAX_RETRY_ATTEMPTS}")
        delay(RETRY_DELAY_MS * attempt) // Exponential backoff
        return loadBannerAdWithRetry(feedId, attempt + 1)
      } else {
        Timber.w("AdManager not ready after ${MAX_RETRY_ATTEMPTS} attempts")
        return false
      }
    }

    return adManager.createBannerAd(
      context = binding.root.context,
      container = binding.root
    )
  }

  /**
   * Clean up resources when ViewHolder is recycled
   */
  fun onRecycled() {
    loadJob?.cancel()
    binding.root.removeAllViews()
    isAdLoaded = false
    Timber.d("AdViewHolder recycled")
  }

  /**
   * Check if ad is currently loaded
   */
  fun isAdLoaded(): Boolean = isAdLoaded
}
