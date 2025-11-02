package cloud.app.csplayer.ads.providers

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

class AdMobProvider : AdProvider {

    override val providerType = AdProvider.ProviderType.ADMOB
    override val priority = 99 // Highest priority

    companion object {
        // Test Ad Unit IDs - replace with real IDs in production
        const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    override suspend fun initialize(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            MobileAds.initialize(context) { initializationStatus ->
                isInitialized = initializationStatus.adapterStatusMap.isNotEmpty()
                Timber.d("AdMob initialized: $isInitialized")
                continuation.resume(isInitialized)
            }
        } catch (e: Exception) {
            Timber.e(e, "AdMob initialization failed")
            continuation.resume(false)
        }
    }

    override fun isAdReady(adType: AdProvider.AdType): Boolean {
        return when (adType) {
            AdProvider.AdType.BANNER -> isInitialized
            AdProvider.AdType.INTERSTITIAL -> interstitialAd != null
            AdProvider.AdType.REWARDED -> rewardedAd != null
            AdProvider.AdType.NATIVE -> isInitialized // Native ads loaded on demand
        }
    }

    override suspend fun preloadAd(context: Context, adType: AdProvider.AdType): Boolean {
        if (!isInitialized) return false

        return when (adType) {
            AdProvider.AdType.INTERSTITIAL -> withContext(Dispatchers.Main) { preloadInterstitial(context) }
            AdProvider.AdType.REWARDED -> withContext(Dispatchers.Main) { preloadRewarded(context) }
            AdProvider.AdType.BANNER -> true // Banner is loaded on demand
            AdProvider.AdType.NATIVE -> true // Native ads loaded on demand
        }
    }

    private suspend fun preloadInterstitial(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
        if (interstitialAd != null) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Timber.d("AdMob interstitial loaded")
                continuation.resume(true)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Timber.w("AdMob interstitial failed to load: ${error.message}")
                continuation.resume(false)
            }
        })
    }

    private suspend fun preloadRewarded(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
        if (rewardedAd != null) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Timber.d("AdMob rewarded loaded")
                continuation.resume(true)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Timber.w("AdMob rewarded failed to load: ${error.message}")
                continuation.resume(false)
            }
        })
    }

    override suspend fun showBannerAd(
        context: Context,
        container: ViewGroup,
        onAdLoaded: () -> Unit,
        onAdFailed: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            // Remove any previous ad views
            container.removeAllViews()
            // Calculate the container width in dp for adaptive banner
            val displayMetrics = context.resources.displayMetrics
            val containerWidthPx = container.width
            val containerWidthDp = if (containerWidthPx > 0) {
                (containerWidthPx / displayMetrics.density).toInt()
            } else {
                // fallback to screen width if container width is not measured yet
                (displayMetrics.widthPixels / displayMetrics.density).toInt()
            }
            val adView = AdView(context).apply {
                adUnitId = BANNER_AD_UNIT_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, containerWidthDp))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Timber.d("AdMob adaptive banner loaded")
                        onAdLoaded()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Timber.w("AdMob banner failed: ${error.message}")
                        onAdFailed(error.message)
                    }
                }
            }

            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
            true
        } catch (e: Exception) {
            Timber.e(e, "AdMob banner error")
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
        val ad = interstitialAd
        if (ad == null) {
            onAdFailed("Interstitial not ready")
            return@withContext false
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Timber.d("AdMob interstitial shown")
                    onAdShown()
                }

                override fun onAdDismissedFullScreenContent() {
                    Timber.d("AdMob interstitial dismissed")
                    interstitialAd = null
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Timber.w("AdMob interstitial show failed: ${error.message}")
                    interstitialAd = null
                    onAdFailed(error.message)
                }
            }

            ad.show(activity)
            true
        } catch (e: Exception) {
            Timber.e(e, "AdMob interstitial show error")
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
        val ad = rewardedAd
        if (ad == null) {
            onAdFailed("Rewarded ad not ready")
            return@withContext false
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Timber.d("AdMob rewarded shown")
                }

                override fun onAdDismissedFullScreenContent() {
                    Timber.d("AdMob rewarded dismissed")
                    rewardedAd = null
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Timber.w("AdMob rewarded show failed: ${error.message}")
                    rewardedAd = null
                    onAdFailed(error.message)
                }
            }

            ad.show(activity) { rewardItem ->
                Timber.d("AdMob reward earned: ${rewardItem.amount}")
                onRewardEarned(rewardItem.amount)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "AdMob rewarded show error")
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
            // Clear previous ad
            container.removeAllViews()

            // Load AdMob native ad
            val adLoader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID) // Test native ad unit
                .forNativeAd { nativeAd ->
                    try {
                        // Inflate custom native ad layout
                        val layoutInflater = android.view.LayoutInflater.from(context)
                        val binding = cloud.app.csplayer.databinding.LayoutAdmobNativeAdBinding.inflate(
                            layoutInflater,
                            container,
                            false
                        )

                        // Populate native ad view
                        populateNativeAdView(nativeAd, binding)

                        // Add to container
                        container.addView(binding.root)

                        Timber.d("AdMob native ad loaded and populated successfully")
                        onAdLoaded()
                    } catch (e: Exception) {
                        Timber.e(e, "Error inflating AdMob native ad layout")
                        onAdFailed(e.message ?: "Layout error")
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Timber.w("AdMob native ad failed: ${error.message}")
                        onAdFailed(error.message)
                    }
                })
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
            true // Request initiated successfully
        } catch (e: Exception) {
            Timber.e(e, "AdMob native ad error")
            onAdFailed(e.message ?: "Unknown error")
            false
        }
    }

    private fun populateNativeAdView(
        nativeAd: com.google.android.gms.ads.nativead.NativeAd,
        binding: cloud.app.csplayer.databinding.LayoutAdmobNativeAdBinding
    ) {
        val nativeAdView = binding.root

        with(binding) {
            // Set the headline view (Title - Required)
            adHeadline.text = nativeAd.headline
            nativeAdView.headlineView = adHeadline

            // Set the app icon (Policy requirement)
            nativeAd.icon?.let {
                adAppIcon.setImageDrawable(it.drawable)
                adAppIcon.visibility = android.view.View.VISIBLE
                nativeAdView.iconView = adAppIcon
            } ?: run {
                adAppIcon.visibility = android.view.View.GONE
            }

            // Set the advertiser (Subtitle line 1)
            nativeAd.advertiser?.let {
                adAdvertiser.text = it
                adAdvertiser.visibility = android.view.View.VISIBLE
                nativeAdView.advertiserView = adAdvertiser
            } ?: run {
                adAdvertiser.visibility = android.view.View.GONE
            }

            // Set the body view (Subtitle line 2)
            nativeAd.body?.let {
                adBody.text = it
                adBody.visibility = android.view.View.VISIBLE
                nativeAdView.bodyView = adBody
            } ?: run {
                adBody.visibility = android.view.View.GONE
            }

            // Set the call to action view (Button)
            nativeAd.callToAction?.let {
                adCallToAction.text = it
                adCallToAction.visibility = android.view.View.VISIBLE
                nativeAdView.callToActionView = adCallToAction
            } ?: run {
                adCallToAction.visibility = android.view.View.GONE
            }

            // Set the media view (Thumbnail)
            nativeAdView.mediaView = adMedia

            // Set AdChoices view (Required by AdMob policy)
           // nativeAdView.adChoicesView = adChoices
        }

        // Register the native ad with the native ad view
        nativeAdView.setNativeAd(nativeAd)

        Timber.d("AdMob native ad view populated (video item style, policy compliant)")
    }


    override fun cleanup() {
        interstitialAd = null
        rewardedAd = null
        isInitialized = false
    }
}
