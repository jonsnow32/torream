package cloud.app.csplayer.ads.providers

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import cloud.app.csplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * HouseAd Provider - Self-promotion ads
 *
 * This provider shows internal promotional content for:
 * - App features
 * - Premium upgrades
 * - Other apps from the same developer
 * - Special offers
 *
 * Benefits:
 * - 100% fill rate (always available)
 * - No revenue share with ad networks
 * - Full control over content
 * - Instant display (no network requests)
 */
class HouseAdProvider : AdProvider {

    override val providerType = AdProvider.ProviderType.HOUSE_AD
    override val priority = 99 // Lowest priority - fallback when other ads fail

    private var isInitialized = false
    private var currentBannerView: View? = null

    companion object {
        // House ad configurations
        data class HouseAdConfig(
            val id: String,
            val title: String,
            val description: String,
            val imageRes: Int? = null,
            val ctaText: String = "Learn More",
            val actionUrl: String? = null
        )

        // Define your house ads here
        private val HOUSE_ADS = listOf(
            HouseAdConfig(
                id = "premium_upgrade",
                title = "Upgrade to Premium",
                description = "Remove all ads and unlock premium features!",
                imageRes = R.drawable.ic_launcher_foreground,
                ctaText = "Upgrade Now",
                actionUrl = "app://premium"
            ),
            HouseAdConfig(
                id = "rate_app",
                title = "Enjoying CSPlayer?",
                description = "Rate us 5 stars on Play Store!",
                imageRes = R.drawable.ic_launcher_foreground,
                ctaText = "Rate Now",
                actionUrl = "market://details?id=cloud.app.csplayer"
            ),
            HouseAdConfig(
                id = "share_app",
                title = "Share CSPlayer",
                description = "Share with your friends and family",
                imageRes = R.drawable.ic_launcher_foreground,
                ctaText = "Share",
                actionUrl = "app://share"
            )
        )

        private var currentAdIndex = 0

        fun getNextAd(): HouseAdConfig {
            val ad = HOUSE_ADS[currentAdIndex]
            currentAdIndex = (currentAdIndex + 1) % HOUSE_ADS.size
            return ad
        }
    }

    override suspend fun initialize(context: Context): Boolean {
        return try {
            isInitialized = true
            Timber.d("HouseAd initialized - ${HOUSE_ADS.size} ads available")
            true
        } catch (e: Exception) {
            Timber.e(e, "HouseAd initialization failed")
            false
        }
    }

    override fun isAdReady(adType: AdProvider.AdType): Boolean {
        // House ads are always ready
        return isInitialized
    }

    override suspend fun preloadAd(context: Context, adType: AdProvider.AdType): Boolean {
        // No preloading needed for house ads
        return isInitialized
    }

    override suspend fun showBannerAd(
        context: Context,
        container: ViewGroup,
        onAdLoaded: () -> Unit,
        onAdFailed: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isInitialized) {
                onAdFailed("HouseAd not initialized")
                return@withContext false
            }

            // Remove previous banner if any
            container.removeAllViews()

            // Get next house ad
            val ad = getNextAd()

            // Inflate banner layout
            val bannerView = LayoutInflater.from(context)
                .inflate(R.layout.house_ad_banner, container, false)

            // Set ad content
            bannerView.findViewById<TextView>(R.id.ad_title).text = ad.title
            bannerView.findViewById<TextView>(R.id.ad_description).text = ad.description
            bannerView.findViewById<Button>(R.id.ad_cta).text = ad.ctaText

            // Set image if available
            ad.imageRes?.let { imageRes: Int ->
                bannerView.findViewById<ImageView>(R.id.ad_image).setImageResource(imageRes)
            }

            // Set click listener
            bannerView.findViewById<Button>(R.id.ad_cta).setOnClickListener {
                handleAdClick(context, ad)
            }

            // Set full width layout params with FrameLayout.LayoutParams for proper gravity
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.gravity = android.view.Gravity.CENTER
            bannerView.layoutParams = layoutParams

            // Add to container
            container.addView(bannerView)
            currentBannerView = bannerView

            Timber.d("HouseAd banner shown: ${ad.title}")
            onAdLoaded()
            true
        } catch (e: Exception) {
            Timber.e(e, "HouseAd banner error")
            onAdFailed(e.message ?: "Unknown error")
            false
        }
    }

    override suspend fun showInterstitialAd(
        activity: Activity,
        onAdShown: () -> Unit,
        onAdClosed: () -> Unit,
        onAdFailed: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isInitialized) {
                onAdFailed("HouseAd not initialized")
                return@withContext false
            }

            // Get next house ad
            val ad = getNextAd()

            // Create custom dialog for interstitial
            val dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.house_ad_interstitial, null)

            // Set ad content
            dialogView.findViewById<TextView>(R.id.ad_title).text = ad.title
            dialogView.findViewById<TextView>(R.id.ad_description).text = ad.description
            dialogView.findViewById<Button>(R.id.ad_cta).text = ad.ctaText

            // Set image if available
            ad.imageRes?.let { imageRes: Int ->
                dialogView.findViewById<ImageView>(R.id.ad_image).setImageResource(imageRes)
            }

            val dialog = AlertDialog.Builder(activity, R.style.BaseMaterialDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // Set click listeners
            dialogView.findViewById<Button>(R.id.ad_cta).setOnClickListener {
                handleAdClick(activity, ad)
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.ad_close).setOnClickListener {
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                Timber.d("HouseAd interstitial closed: ${ad.title}")
                onAdClosed()
            }

            dialog.show()
            onAdShown()

            Timber.d("HouseAd interstitial shown: ${ad.title}")
            true
        } catch (e: Exception) {
            Timber.e(e, "HouseAd interstitial error")
            onAdFailed(e.message ?: "Unknown error")
            false
        }
    }

    override suspend fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (Int) -> Unit,
        onAdClosed: () -> Unit,
        onAdFailed: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isInitialized) {
                onAdFailed("HouseAd not initialized")
                return@withContext false
            }

            // Get next house ad
            val ad = getNextAd()

            // Create custom dialog for rewarded ad
            val dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.house_ad_rewarded, null)

            // Set ad content
            dialogView.findViewById<TextView>(R.id.ad_title).text = ad.title
            dialogView.findViewById<TextView>(R.id.ad_description).text = ad.description
            dialogView.findViewById<Button>(R.id.ad_cta).text = ad.ctaText

            // Set image if available
            ad.imageRes?.let { imageRes: Int ->
                dialogView.findViewById<ImageView>(R.id.ad_image).setImageResource(imageRes)
            }

            val dialog = AlertDialog.Builder(activity, R.style.BaseMaterialDialogTheme)
                .setView(dialogView)
                .setCancelable(false) // Rewarded ads shouldn't be dismissible
                .create()

            var rewardGranted = false

            // Set click listeners
            dialogView.findViewById<Button>(R.id.ad_cta).setOnClickListener {
                handleAdClick(activity, ad)

                // Grant reward after action
                if (!rewardGranted) {
                    rewardGranted = true
                    onRewardEarned(1)
                    Timber.d("HouseAd reward granted: ${ad.title}")
                }

                dialog.dismiss()
            }

            // Show timer and then enable close button
            val closeButton = dialogView.findViewById<Button>(R.id.ad_close)
            val timerText = dialogView.findViewById<TextView>(R.id.ad_timer)
            closeButton.isEnabled = false

            // 5 second countdown
            withContext(Dispatchers.IO) {
                for (i in 5 downTo 1) {
                    withContext(Dispatchers.Main) {
                        timerText.text = "Close in ${i}s"
                    }
                    delay(1000)
                }
                withContext(Dispatchers.Main) {
                    timerText.text = "You can close now"
                    closeButton.isEnabled = true
                }
            }

            closeButton.setOnClickListener {
                if (!rewardGranted) {
                    rewardGranted = true
                    onRewardEarned(1)
                    Timber.d("HouseAd reward granted (closed): ${ad.title}")
                }
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                Timber.d("HouseAd rewarded closed: ${ad.title}")
                onAdClosed()
            }

            dialog.show()

            Timber.d("HouseAd rewarded shown: ${ad.title}")
            true
        } catch (e: Exception) {
            Timber.e(e, "HouseAd rewarded error")
            onAdFailed(e.message ?: "Unknown error")
            false
        }
    }

    override suspend fun showNativeAd(
        context: Context,
        container: ViewGroup,
        onAdLoaded: () -> Unit,
        onAdFailed: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isInitialized) {
                onAdFailed("HouseAd not initialized")
                return@withContext false
            }

            // Remove previous native ad if any
            container.removeAllViews()

            // Get next house ad
            val ad = getNextAd()

            // Inflate native ad layout (same as banner for house ads)
            val nativeAdView = LayoutInflater.from(context)
                .inflate(R.layout.house_ad_native, container, false)

            // Set ad content
            nativeAdView.findViewById<TextView>(R.id.ad_title).text = ad.title
            nativeAdView.findViewById<TextView>(R.id.ad_description).text = ad.description
            nativeAdView.findViewById<Button>(R.id.ad_cta).text = ad.ctaText

            // Set image if available
            ad.imageRes?.let { imageRes: Int ->
                nativeAdView.findViewById<ImageView>(R.id.ad_image).setImageResource(imageRes)
            }

            // Set click listener
            nativeAdView.findViewById<Button>(R.id.ad_cta).setOnClickListener {
                handleAdClick(context, ad)
            }

            // Set full width layout params
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.gravity = android.view.Gravity.CENTER
            nativeAdView.layoutParams = layoutParams

            // Add to container
            container.addView(nativeAdView)

            Timber.d("HouseAd native ad shown: ${ad.title}")
            onAdLoaded()
            true
        } catch (e: Exception) {
            Timber.e(e, "HouseAd native ad error")
            onAdFailed(e.message ?: "Unknown error")
            false
        }
    }

    private fun handleAdClick(context: Context, ad: HouseAdConfig) {
        Timber.d("HouseAd clicked: ${ad.title} -> ${ad.actionUrl}")

        // Handle different action types
        when {
            ad.actionUrl?.startsWith("app://") == true -> {
                // Internal app actions
                when (ad.actionUrl) {
                    "app://premium" -> {
                        // TODO: Open premium upgrade screen
                        Timber.d("Navigate to premium upgrade")
                    }
                    "app://share" -> {
                        // TODO: Open share dialog
                        Timber.d("Open share dialog")
                    }
                }
            }
            ad.actionUrl?.startsWith("market://") == true -> {
                // Open Play Store
                try {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(ad.actionUrl)
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open Play Store")
                }
            }
            ad.actionUrl?.startsWith("http") == true -> {
                // Open web browser
                try {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(ad.actionUrl)
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open browser")
                }
            }
        }
    }

    override fun cleanup() {
        currentBannerView = null
        isInitialized = false
        Timber.d("HouseAd cleaned up")
    }
}
