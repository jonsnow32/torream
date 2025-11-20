package cloud.app.csplayer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.ads.AdPreloadManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CSApplication : Application(), ImageLoaderFactory, Configuration.Provider {
  @Inject
  lateinit var adManager: AdManager

  @Inject
  lateinit var adPreloadManager: AdPreloadManager

  @Inject
  lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()

  private val scope = MainScope() + CoroutineName("Application")

  override fun onCreate() {
    super.onCreate()

    // Initialize theme before anything else
    initializeTheme()

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }

    scope.launch {
      try {
        adManager.initialize(this@CSApplication)

        // Initialize preload manager after AdManager is ready
        adPreloadManager.initialize(this@CSApplication)

        // Start intelligent preload
        adPreloadManager.startIntelligentPreload(isAppInForeground = true)

        Timber.i("Ad system with enhanced preload initialized successfully")
      } catch (e: Exception) {
        Timber.e(e, "Failed to initialize ad system")
      }
    }
  }

  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
      .components {
        add(VideoFrameDecoder.Factory())
      }
      .crossfade(true)
      .respectCacheHeaders(false)
      // Enable disk cache for video thumbnails
      .diskCachePolicy(coil.request.CachePolicy.ENABLED)
      .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
      .build()
  }

  private fun initializeTheme() {
    try {
      val preferences = PreferenceManager.getDefaultSharedPreferences(this)
      val themeKey = getString(R.string.app_theme_key)
      val savedTheme = preferences.getString(themeKey, "Dark")

      when (savedTheme) {
        "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Default to dark
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to initialize theme")
      // Fallback to dark theme
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
  }
}
