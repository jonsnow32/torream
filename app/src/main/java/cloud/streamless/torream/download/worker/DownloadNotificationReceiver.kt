package cloud.streamless.torream.download.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cloud.streamless.torream.download.DownloadCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver for handling download notification actions
 * (Pause All, Cancel All)
 */
@AndroidEntryPoint
class DownloadNotificationReceiver : BroadcastReceiver() {

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) return

    // Use goAsync() to allow async operations in BroadcastReceiver
    val result = goAsync()

    when (intent.action) {
      DownloadNotificationHelper.ACTION_PAUSE_ALL -> {
        Timber.d("Pause All Downloads action received")
        handlePauseAll {
          result.finish()
        }
      }

      DownloadNotificationHelper.ACTION_CANCEL_ALL -> {
        Timber.d("Cancel All Downloads action received")
        handleCancelAll {
          result.finish()
        }
      }

      else -> {
        Timber.d("Unknown action: ${intent.action}")
        result.finish()
      }
    }
  }

  private fun handlePauseAll(onComplete: () -> Unit) {
    try {
      // Launch coroutine to handle suspend function
      @Suppress("GlobalScope")
      GlobalScope.launch {
        try {
          downloadCoordinator.pauseAllDownloads()
          Timber.d("✓ All downloads paused")
        } catch (e: Exception) {
          Timber.e(e, "Failed to pause all downloads")
        } finally {
          onComplete()
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Error in handlePauseAll")
      onComplete()
    }
  }

  private fun handleCancelAll(onComplete: () -> Unit) {
    try {
      // Launch coroutine to handle suspend function
      @Suppress("GlobalScope")
      GlobalScope.launch {
        try {
          downloadCoordinator.cancelAllDownloads()
          Timber.d("✓ All downloads cancelled")
        } catch (e: Exception) {
          Timber.e(e, "Failed to cancel all downloads")
        } finally {
          onComplete()
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Error in handleCancelAll")
      onComplete()
    }
  }
}

