package com.tv.apps.zippy

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.tv.apps.zippy.ads.AdManager
import com.tv.apps.zippy.ads.AdPreloadManager
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

  @Inject
  lateinit var downloadRecoveryManager: com.tv.apps.zippy.download.DownloadRecoveryManager

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
        // Recover interrupted downloads first
        try {
          downloadRecoveryManager.recoverInterruptedDownloads()
          Timber.i("Download recovery check completed")
        } catch (e: Exception) {
          Timber.e(e, "Failed to recover downloads")
        }

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
      val savedTheme = preferences.getString(themeKey, "System")

      when (savedTheme) {
        "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // Default to system
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to initialize theme")
      // Fallback to system theme
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }
}
