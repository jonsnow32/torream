package cloud.app.csplayer.ads.providers

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.vungle.ads.BannerAdListener
import com.vungle.ads.BaseAd
import com.vungle.ads.InitializationListener
import com.vungle.ads.InterstitialAd
import com.vungle.ads.InterstitialAdListener
import com.vungle.ads.RewardedAd
import com.vungle.ads.RewardedAdListener
import com.vungle.ads.VungleAdSize
import com.vungle.ads.VungleAds
import com.vungle.ads.VungleBannerView
import com.vungle.ads.VungleError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

class VungleProvider : AdProvider {
  override val providerType = AdProvider.ProviderType.VUNGLE
  override val priority = 2

  companion object {
    // Replace with your real Vungle placement IDs
    const val APP_ID = "68ad8710b74927f2ab2d5a61"
    const val INTERSTITIAL_PLACEMENT_ID = "DEFAULT-9620431"
    const val REWARDED_PLACEMENT_ID = "REWARDED-1759788"
    const val NATIVE_PLACEMENT_ID = "FEEDNATIVEAD-8890954"
    const val BANNER_PLACEMENT_ID = "BANNER-1793467"
  }

  private var isInitialized = false
  private var interstitial: InterstitialAd? = null
  private var rewarded: RewardedAd? = null
  // Banner not implemented

  override suspend fun initialize(context: Context): Boolean =
    suspendCancellableCoroutine { continuation ->

      VungleAds.init(context, APP_ID, object : InitializationListener {
        override fun onSuccess() {
          isInitialized = true
          Timber.d("Vungle initialized")
          continuation.resume(true)
        }

        override fun onError(vungleError: VungleError) {
          isInitialized = false
          Timber.e("Vungle init error: ${vungleError.errorMessage}")
          continuation.resume(false)
        }
      })

    }

  override fun isAdReady(adType: AdProvider.AdType): Boolean {
    return when (adType) {
      AdProvider.AdType.BANNER -> isInitialized
      AdProvider.AdType.INTERSTITIAL -> interstitial?.canPlayAd() == true
      AdProvider.AdType.REWARDED -> rewarded?.canPlayAd() == true
      AdProvider.AdType.NATIVE -> isInitialized // Vungle now supports native ads
    }
  }

  override suspend fun preloadAd(context: Context, adType: AdProvider.AdType): Boolean {
    if (!isInitialized) return false
    return when (adType) {
      AdProvider.AdType.INTERSTITIAL -> withContext(Dispatchers.Main) { preloadInterstitial(context) }
      AdProvider.AdType.REWARDED -> withContext(Dispatchers.Main) { preloadRewarded(context) }
      AdProvider.AdType.BANNER -> true // Banner loaded on demand
      AdProvider.AdType.NATIVE -> true // Native ads loaded on demand
    }
  }

  // Common listener for InterstitialAd
  class CommonInterstitialAdListener(
    private val onLoaded: (() -> Unit)? = null,
    private val onFailedToLoad: ((VungleError) -> Unit)? = null,
    private val onShown: (() -> Unit)? = null,
    private val onClosed: (() -> Unit)? = null,
    private val onFailedToShow: ((VungleError) -> Unit)? = null
  ) : InterstitialAdListener {
    override fun onAdLoaded(baseAd: BaseAd) {
      onLoaded?.invoke()
    }

    override fun onAdFailedToLoad(baseAd: BaseAd, adError: VungleError) {
      onFailedToLoad?.invoke(adError)
    }

    override fun onAdStart(baseAd: BaseAd) {
      onShown?.invoke()
    }

    override fun onAdEnd(baseAd: BaseAd) {
      onClosed?.invoke()
    }

    override fun onAdFailedToPlay(baseAd: BaseAd, adError: VungleError) {
      onFailedToShow?.invoke(adError)
    }

    override fun onAdClicked(baseAd: BaseAd) {}
    override fun onAdImpression(baseAd: BaseAd) {}
    override fun onAdLeftApplication(baseAd: BaseAd) {}
  }

  private suspend fun preloadInterstitial(context: Context): Boolean =
    suspendCancellableCoroutine { continuation ->
      val ad = InterstitialAd(context, INTERSTITIAL_PLACEMENT_ID)
      ad.adListener = CommonInterstitialAdListener(
        onLoaded = {
          interstitial = ad
          Timber.d("Vungle interstitial loaded")
          continuation.resume(true)
        },
        onFailedToLoad = { error ->
          interstitial = null
          Timber.w("Vungle interstitial failed: ${error.errorMessage}")
          continuation.resume(false)
        }
      )
      ad.load()
    }

  private suspend fun preloadRewarded(context: Context): Boolean =
    suspendCancellableCoroutine { continuation ->
      val ad = RewardedAd(context, REWARDED_PLACEMENT_ID).apply {
        adListener = object : RewardedAdListener {
          override fun onAdClicked(baseAd: BaseAd) {
          }

          override fun onAdEnd(baseAd: BaseAd) {
          }

          override fun onAdFailedToLoad(baseAd: BaseAd, adError: VungleError) {
            rewarded = null
            Timber.w("Vungle rewarded failed: ${adError.errorMessage}")
            continuation.resume(false)
          }

          override fun onAdFailedToPlay(baseAd: BaseAd, adError: VungleError) {
          }

          override fun onAdImpression(baseAd: BaseAd) {
          }

          override fun onAdLeftApplication(baseAd: BaseAd) {
          }

          override fun onAdLoaded(baseAd: BaseAd) {
            rewarded = baseAd as RewardedAd?
            Timber.d("Vungle rewarded loaded")
            continuation.resume(true)
          }

          override fun onAdStart(baseAd: BaseAd) {
          }

          override fun onAdRewarded(baseAd: BaseAd) {
          }
        }
        load()
      }
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

      suspendCancellableCoroutine { continuation ->
        val ad = VungleBannerView(context, BANNER_PLACEMENT_ID, VungleAdSize.BANNER).apply {
          adListener = object : BannerAdListener {

            override fun onAdClicked(baseAd: BaseAd) {
            }

            override fun onAdEnd(baseAd: BaseAd) {
            }

            override fun onAdFailedToLoad(
              baseAd: BaseAd,
              adError: VungleError
            ) {
              Timber.w("Vungle banner failed: ${adError.message}")
              onAdFailed(adError.message ?: "Load failed")
              if (continuation.isActive) {
                continuation.resume(false)
              }
            }

            override fun onAdFailedToPlay(
              baseAd: BaseAd,
              adError: VungleError
            ) {
            }

            override fun onAdImpression(baseAd: BaseAd) {
            }

            override fun onAdLeftApplication(baseAd: BaseAd) {
            }

            override fun onAdLoaded(baseAd: BaseAd) {
              Timber.d("Vungle banner loaded")
              onAdLoaded()
              if (continuation.isActive) {
                continuation.resume(true)
              }
            }

            override fun onAdStart(baseAd: BaseAd) {
            }
          }
          load()
        }.also {
          container.addView(it)
        }

        continuation.invokeOnCancellation {
          Timber.d("Banner ad load cancelled")
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Vungle banner error")
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
    val ad = interstitial
    if (ad == null || !ad.canPlayAd()) {
      onAdFailed("Vungle interstitial not ready")
      return@withContext false
    }
    ad.adListener = CommonInterstitialAdListener(
      onShown = { onAdShown() },
      onClosed = {
        interstitial = null
        onAdClosed()
      },
      onFailedToShow = { error ->
        interstitial = null
        onAdFailed(error.errorMessage)
      }
    )
    ad.play(activity)
    true
  }

  override suspend fun showRewardedAd(
    activity: Activity,
    onRewardEarned: (Int) -> Unit,
    onAdClosed: () -> Unit,
    onAdFailed: (String) -> Unit
  ): Boolean = withContext(Dispatchers.Main) {
    val ad = rewarded
    if (ad == null || !ad.canPlayAd()) {
      onAdFailed("Vungle rewarded not ready")
      return@withContext false
    }
    ad.adListener = object : RewardedAdListener {
      override fun onAdLoaded(baseAd: BaseAd) {}
      override fun onAdFailedToLoad(baseAd: BaseAd, adError: VungleError) {}
      override fun onAdStart(baseAd: BaseAd) {
        Timber.d("Vungle rewarded shown")
      }

      override fun onAdEnd(baseAd: BaseAd) {
        Timber.d("Vungle rewarded closed")
        rewarded = null
        onAdClosed()
      }

      override fun onAdRewarded(baseAd: BaseAd) {
        Timber.d("Vungle reward earned")
        onRewardEarned(1)
      }

      override fun onAdFailedToPlay(baseAd: BaseAd, adError: VungleError) {
        Timber.w("Vungle rewarded show failed: ${adError.errorMessage}")
        rewarded = null
        onAdFailed(adError.errorMessage)
      }

      override fun onAdClicked(baseAd: BaseAd) {}
      override fun onAdImpression(baseAd: BaseAd) {}
      override fun onAdLeftApplication(baseAd: BaseAd) {}
    }
    ad.play(activity)
    true
  }

  override suspend fun showNativeAd(
    context: Context,
    container: ViewGroup,
    onAdLoaded: () -> Unit,
    onAdFailed: (String) -> Unit
  ): Boolean = withContext(Dispatchers.Main) {
    try {
      if (!isInitialized) {
        onAdFailed("Vungle not initialized")
        return@withContext false
      }

      // Remove previous ad
      container.removeAllViews()

      // Load Vungle native ad and wait for result
      suspendCancellableCoroutine { continuation ->
        val nativeAd = com.vungle.ads.NativeAd(
          context,
          NATIVE_PLACEMENT_ID
        )

        nativeAd.adListener = object : com.vungle.ads.NativeAdListener {
          override fun onAdLoaded(baseAd: BaseAd) {
            // Get the native ad object
            val vungleNativeAd = baseAd as? com.vungle.ads.NativeAd
            vungleNativeAd?.let { nativeAd ->
              // Inflate custom layout
              val layoutInflater = android.view.LayoutInflater.from(context)
              val binding = cloud.app.csplayer.databinding.LayoutVungleNativeAdBinding.inflate(
                layoutInflater,
                container,
                false
              )

              with(binding) {
                // Populate ad content
                lbAdTitle.text = nativeAd.getAdTitle()
                lbAdBody.text = nativeAd.getAdBodyText()
                lbAdRating.text = "Rating: ${nativeAd.getAdStarRating()}"
                lbAdSponsor.text = nativeAd.getAdSponsoredText()
                btnAdCta.text = nativeAd.getAdCallToActionText()
                btnAdCta.isVisible = nativeAd.hasCallToAction()

                // Set clickable views
                val clickableViews = listOf(
                  imgAdIcon,
                  pnlVideoAd,
                  btnAdCta
                )

                // Register view for interaction
                nativeAd.registerViewForInteraction(
                  root,
                  pnlVideoAd,
                  imgAdIcon,
                  clickableViews
                )

                // Add to container
                container.addView(root)
              }

              Timber.d("Vungle native ad loaded successfully")
              onAdLoaded()
              if (continuation.isActive) {
                continuation.resume(true)
              }
            } ?: run {
              Timber.w("Failed to cast to NativeAd")
              onAdFailed("Failed to cast native ad")
              if (continuation.isActive) {
                continuation.resume(false)
              }
            }
          }

          override fun onAdFailedToLoad(baseAd: BaseAd, adError: VungleError) {
            Timber.w("Vungle native ad failed to load: ${adError.errorMessage}")
            onAdFailed(adError.errorMessage)
            if (continuation.isActive) {
              continuation.resume(false)
            }
          }

          override fun onAdImpression(baseAd: BaseAd) {
            Timber.d("Vungle native ad impression")
          }

          override fun onAdClicked(baseAd: BaseAd) {
            Timber.d("Vungle native ad clicked")
          }

          override fun onAdStart(baseAd: BaseAd) {}
          override fun onAdEnd(baseAd: BaseAd) {}
          override fun onAdFailedToPlay(baseAd: BaseAd, adError: VungleError) {}
          override fun onAdLeftApplication(baseAd: BaseAd) {}
        }

        // Load the native ad
        nativeAd.load()

        continuation.invokeOnCancellation {
          // Cleanup if coroutine is cancelled
          Timber.d("Native ad load cancelled")
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Vungle native ad error")
      onAdFailed(e.message ?: "Unknown error")
      false
    }
  }

  override fun cleanup() {
    interstitial = null
    rewarded = null
    isInitialized = false
  }
}
