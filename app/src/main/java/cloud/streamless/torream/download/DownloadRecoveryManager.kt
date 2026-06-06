package cloud.streamless.torream.download

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages recovery of downloads after app restart or crash
 * Checks for downloads that were in progress and resumes their workers
 */
@Singleton
class DownloadRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val workManager: WorkManager
) {

    /**
     * Check and resume all downloads that were interrupted by app kill/crash
     * Should be called during app startup
     */
    suspend fun recoverInterruptedDownloads() {
        try {
            Timber.i("🔄 Checking for interrupted downloads to recover...")

            // Get all download states from database
            val allStates = downloadRepository.observeAllStates().first()

            // Filter downloads that should be resumed
            val interruptedDownloads = allStates.filter { state ->
                when (state.status) {
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.QUEUED -> {
                        // Check if worker actually exists and is running
                        val workInfos = try {
                            workManager.getWorkInfosForUniqueWork("download_${state.task.id}").get()
                        } catch (e: Exception) {
                            Timber.w("Failed to get work info for ${state.task.id}: ${e.message}")
                            emptyList()
                        }

                        val hasRunningWorker = workInfos.any { workInfo ->
                            workInfo.state == WorkInfo.State.RUNNING ||
                            workInfo.state == WorkInfo.State.ENQUEUED
                        }

                        // Only recover if no worker is running
                        if (hasRunningWorker) {
                            Timber.d("Download ${state.task.id} has active worker, skipping recovery")
                            false
                        } else {
                            Timber.i("Download ${state.task.id} was ${state.status} but has no worker - needs recovery")
                            true
                        }
                    }

                    else -> false
                }
            }

            if (interruptedDownloads.isEmpty()) {
                Timber.i("✓ No interrupted downloads to recover")
                return
            }

            Timber.i("Found ${interruptedDownloads.size} interrupted downloads to recover:")
            interruptedDownloads.forEach { state ->
                Timber.i("  - ${state.task.fileName ?: state.task.id}: ${state.status}, ${state.progress}%")
            }

            // Resume each interrupted download
            var successCount = 0
            var failCount = 0

            for (state in interruptedDownloads) {
                try {
                    Timber.d("Recovering download: ${state.task.id}")

                    // Update status to QUEUED before resuming
                    downloadRepository.updateState(
                        state.copy(
                            status = DownloadStatus.QUEUED,
                            error = "Resuming after app restart..."
                        )
                    )

                    // Resume via coordinator
                    downloadCoordinator.startDownload(state.task)

                    successCount++
                    Timber.i("✓ Recovered download: ${state.task.fileName ?: state.task.id}")

                } catch (e: Exception) {
                    failCount++
                    Timber.e(e, "Failed to recover download: ${state.task.id}")

                    // Update to failed status
                    try {
                        downloadRepository.updateState(
                            state.copy(
                                status = DownloadStatus.FAILED,
                                error = "Recovery failed: ${e.message}"
                            )
                        )
                    } catch (updateError: Exception) {
                        Timber.e(updateError, "Failed to update failed status")
                    }
                }
            }

            Timber.i("✅ Download recovery complete: $successCount recovered, $failCount failed")

        } catch (e: Exception) {
            Timber.e(e, "Error during download recovery")
        }
    }

    /**
     * Clean up stuck downloads that have been in progress for too long
     * Useful for handling edge cases where downloads get stuck
     */
    suspend fun cleanupStuckDownloads(maxHoursStuck: Long = 24) {
        try {
            Timber.i("🧹 Checking for stuck downloads...")

            val allStates = downloadRepository.observeAllStates().first()
            val now = System.currentTimeMillis()
            val stuckThreshold = maxHoursStuck * 60 * 60 * 1000 // hours to milliseconds

            val stuckDownloads = allStates.filter { state ->
                val isStuckStatus = state.status == DownloadStatus.DOWNLOADING ||
                                   state.status == DownloadStatus.QUEUED

                if (!isStuckStatus) return@filter false

                // Check if it's been stuck for too long
                val timeSinceCreation = now - state.task.createdAt
                val isStuck = timeSinceCreation > stuckThreshold

                if (isStuck) {
                    Timber.w("Download ${state.task.id} stuck for ${timeSinceCreation / (60 * 60 * 1000)} hours")
                }

                isStuck
            }

            if (stuckDownloads.isEmpty()) {
                Timber.i("✓ No stuck downloads found")
                return
            }

            Timber.i("Found ${stuckDownloads.size} stuck downloads to clean up")

            for (state in stuckDownloads) {
                try {
                    // Cancel worker if exists
                    workManager.cancelUniqueWork("download_${state.task.id}")

                    // Update to failed status
                    downloadRepository.updateState(
                        state.copy(
                            status = DownloadStatus.FAILED,
                            error = "Download stuck for more than $maxHoursStuck hours. Please try again."
                        )
                    )

                    Timber.i("Cleaned up stuck download: ${state.task.id}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clean up stuck download: ${state.task.id}")
                }
            }

            Timber.i("✅ Stuck downloads cleanup complete")

        } catch (e: Exception) {
            Timber.e(e, "Error during stuck downloads cleanup")
        }
    }
}

