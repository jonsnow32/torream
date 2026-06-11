package cloud.streamless.torream

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import cloud.streamless.torream.ads.AdManager
import cloud.streamless.torream.ads.AdPreloadManager
import cloud.streamless.torream.utils.ContinueWatchingReminderWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import java.util.concurrent.TimeUnit
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
  lateinit var downloadRecoveryManager: cloud.streamless.torream.download.DownloadRecoveryManager

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

    scheduleContinueWatchingReminder()
  }

  private fun scheduleContinueWatchingReminder() {
    try {
      val workManager = WorkManager.getInstance(this)
      val request = PeriodicWorkRequestBuilder<ContinueWatchingReminderWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(1, TimeUnit.DAYS)
        .build()
      workManager.enqueueUniquePeriodicWork(
        ContinueWatchingReminderWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
      )

      if (BuildConfig.DEBUG) {
        // Test mode: also fire once shortly after each launch
        val testRequest = OneTimeWorkRequestBuilder<ContinueWatchingReminderWorker>()
          .setInitialDelay(30, TimeUnit.SECONDS)
          .build()
        workManager.enqueueUniqueWork(
          ContinueWatchingReminderWorker.WORK_NAME + "_test",
          ExistingWorkPolicy.REPLACE,
          testRequest
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to schedule continue watching reminder")
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
