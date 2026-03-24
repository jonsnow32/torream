package com.tv.apps.zippy.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tv.apps.zippy.model.SubtitleData
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.utils.Utils.logError

/**
 * Helper class for Fcast (WebVideoCaster) integration
 * Allows casting videos to devices using the WebVideoCaster protocol
 */
object FcastHelper {

    private const val WEBVIDEO_CASTER_PACKAGE = "com.instantbits.cast.webvideo"
    private const val FCAST_ACTION = "com.instantbits.cast.webvideo.PLAY"

    /**
     * Check if WebVideoCaster app is installed
     */
    fun isWebVideoCasterInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WEBVIDEO_CASTER_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Cast a video using Fcast protocol
     * @param context Android context
     * @param videoLink The video link to cast
     * @param title Video title
     * @param poster Poster image URL
     * @param subtitles List of subtitle tracks
     * @param position Start position in milliseconds
     * @return true if cast was initiated successfully
     */
    fun castVideo(
        context: Context,
        videoLink: VideoLink,
        title: String?,
        poster: String?,
        subtitles: List<SubtitleData> = emptyList(),
        position: Long = 0
    ): Boolean {
        return try {
            if (!isWebVideoCasterInstalled(context)) {
                return false
            }

            val intent = Intent(FCAST_ACTION).apply {
                setPackage(WEBVIDEO_CASTER_PACKAGE)

                // Video URL
                putExtra("url", videoLink.url)

                // Title
                if (!title.isNullOrEmpty()) {
                    putExtra("title", title)
                }

                // Poster/thumbnail
                if (!poster.isNullOrEmpty()) {
                    putExtra("poster", poster)
                }

                // Headers (if any)
                if (videoLink.headers.isNotEmpty()) {
                    val headersBundle = android.os.Bundle()
                    videoLink.headers.forEach { (key, value) ->
                        headersBundle.putString(key, value)
                    }
                    putExtra("headers", headersBundle)
                }

                // Subtitles
                if (subtitles.isNotEmpty()) {
                    val subsArray = ArrayList<String>()
                    val subsNamesArray = ArrayList<String>()

                    subtitles.forEach { subtitle ->
                        subsArray.add(subtitle.url)
                        subsNamesArray.add(subtitle.name)
                    }

                    putStringArrayListExtra("subs", subsArray)
                    putStringArrayListExtra("subs_name", subsNamesArray)
                }

                // Start position
                if (position > 0) {
                    putExtra("position", position.toInt())
                }

                // Content type
                putExtra("content_type", "video/*")

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    /**
     * Cast multiple videos as a playlist using Fcast protocol
     * @param context Android context
     * @param videoLinks List of video links
     * @param title Playlist title
     * @param poster Poster image URL
     * @param subtitles List of subtitle tracks
     * @param startIndex Index of video to start with
     * @param position Start position in milliseconds
     * @return true if cast was initiated successfully
     */
    fun castPlaylist(
        context: Context,
        videoLinks: List<VideoLink>,
        title: String?,
        poster: String?,
        subtitles: List<SubtitleData> = emptyList(),
        startIndex: Int = 0,
        position: Long = 0
    ): Boolean {
        return try {
            if (!isWebVideoCasterInstalled(context) || videoLinks.isEmpty()) {
                return false
            }

            val intent = Intent(FCAST_ACTION).apply {
                setPackage(WEBVIDEO_CASTER_PACKAGE)

                // Playlist URLs
                val urlsArray = ArrayList<String>()
                videoLinks.forEach { urlsArray.add(it.url) }
                putStringArrayListExtra("playlist", urlsArray)

                // Start index
                putExtra("playlist_position", startIndex)

                // Title
                if (!title.isNullOrEmpty()) {
                    putExtra("title", title)
                }

                // Poster/thumbnail
                if (!poster.isNullOrEmpty()) {
                    putExtra("poster", poster)
                }

                // Subtitles
                if (subtitles.isNotEmpty()) {
                    val subsArray = ArrayList<String>()
                    val subsNamesArray = ArrayList<String>()

                    subtitles.forEach { subtitle ->
                        subsArray.add(subtitle.url)
                        subsNamesArray.add(subtitle.name)
                    }

                    putStringArrayListExtra("subs", subsArray)
                    putStringArrayListExtra("subs_name", subsNamesArray)
                }

                // Start position
                if (position > 0) {
                    putExtra("position", position.toInt())
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    /**
     * Open WebVideoCaster app in Play Store
     */
    fun openWebVideoCasterInStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$WEBVIDEO_CASTER_PACKAGE")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser if Play Store is not available
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$WEBVIDEO_CASTER_PACKAGE")
            }
            context.startActivity(intent)
        }
    }
}
