package cloud.streamless.torream.utils

import android.os.Bundle
import cloud.streamless.torream.model.PlaybackData

/**
 * Helper object for creating Bundles with PlaybackData
 */
object PlaybackDataHelper {
    /**
     * Create a Bundle for inter-fragment navigation with PlaybackData
     * Use this when navigating between fragments in the same app
     *
     * Example:
     * ```
     * val bundle = PlaybackDataHelper.createBundle(playbackData)
     * findNavController().navigate(R.id.playerFragment, bundle)
     * ```
     */
    fun createBundle(playbackData: PlaybackData): Bundle {
        return Bundle().apply {
            putParcelable(PlaybackData.KEY_PLAYBACK_DATA, playbackData)
        }
    }
}

