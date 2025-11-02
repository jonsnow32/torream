package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.databinding.ItemBannerAdHolderBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewHolder for displaying ads in the feed
 * Uses AdManager waterfall system to show banner ads
 */
class BannerAdViewHolder(
  parent: ViewGroup,
  val binding: ItemBannerAdHolderBinding = ItemBannerAdHolderBinding.inflate(
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
    if (feed.type == FeedData.Type.NativeAd) {
      Timber.d("AdItem ${feed.id} is not a native ad placeholder")
      return
    }

    // Load banner ad using AdManager with retry logic
    loadJob = CoroutineScope(Dispatchers.Main).launch {
      try {
        Timber.d("Loading banner ad for AdItem ${feed.id}")

        val success = loadBannerAdWithRetry(feed.id)

        if (success) {
          isAdLoaded = true
          Timber.d("Banner ad loaded successfully for AdItem ${feed.id}")
        } else {
          Timber.w("Failed to load banner ad for AdItem ${feed.id}")
          // Hide the ad container if loading failed
          binding.root.layoutParams.height = 0
        }
      } catch (e: Exception) {
        Timber.e(e, "Error loading banner ad for AdItem ${feed.id}")
        binding.root.layoutParams.height = 0
      }
    }
  }

  /**
   * Load banner ad with retry logic
   */
  private suspend fun loadBannerAdWithRetry(feedId: String, attempt: Int = 1): Boolean {
    // Check if AdManager is null
    if (adManager == null) {
      Timber.w("AdManager is null, cannot load banner ad")
      return false
    }

    // Check if AdManager is initialized
    if (!adManager.isReady()) {
      if (attempt <= MAX_RETRY_ATTEMPTS) {
        Timber.d("AdManager not ready, retry attempt $attempt/$MAX_RETRY_ATTEMPTS")
        delay(RETRY_DELAY_MS * attempt) // Exponential backoff
        return loadBannerAdWithRetry(feedId, attempt + 1)
      } else {
        Timber.w("AdManager not ready after $MAX_RETRY_ATTEMPTS attempts")
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
