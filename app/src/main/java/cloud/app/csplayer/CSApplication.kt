package cloud.app.csplayer

import android.app.Application
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.ads.AdPreloadManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CSApplication : Application() {
  @Inject
  lateinit var adManager: AdManager

  @Inject
  lateinit var adPreloadManager: AdPreloadManager

  private val scope = MainScope() + CoroutineName("Application")

  override fun onCreate() {
    super.onCreate()

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
}
