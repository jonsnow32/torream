package cloud.app.csplayer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import cloud.app.csplayer.model.PlaybackData

/**
 * Helper object for creating Intents and Bundles with PlaybackData
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

    /**
     * Create an Intent for inter-app communication with PlaybackData via FileProvider
     * Use this when launching another app with large playback data
     *
     * Example (from external app to CSPlayer):
     * ```
     * val intent = PlaybackDataHelper.createIntentForExternalApp(
     *     context = this,
     *     targetPackage = "cloud.app.csplayer",
     *     playbackData = playbackData
     * )
     * startActivity(intent)
     * ```
     */
    fun createIntentForExternalApp(
        context: Context,
        targetPackage: String,
        playbackData: PlaybackData
    ): Intent {
        // Write PlaybackData to file and get URI
        val uri = PlaybackData.writeToFileAndGetUri(context, playbackData)

        return Intent(Intent.ACTION_VIEW).apply {
            setPackage(targetPackage)
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Add metadata as extras for quick access
            putExtra("has_playback_data", true)
            putExtra("title", playbackData.title)
        }
    }

    /**
     * Create an Intent to open CSPlayer from another app
     * This is a convenience method for external apps
     */
    fun openCSPlayer(
        context: Context,
        playbackData: PlaybackData
    ): Intent {
        return createIntentForExternalApp(
            context = context,
            targetPackage = "cloud.app.csplayer",
            playbackData = playbackData
        )
    }
}

