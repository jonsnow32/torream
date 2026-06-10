package cloud.streamless.torream.utils

import android.net.Uri
import androidx.media3.common.MimeTypes
import cloud.streamless.torream.MetadataHolder
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.VideoLink
import cloud.streamless.torream.utils.Coroutines.main
import cloud.streamless.torream.utils.Utils.logError
import cloud.streamless.torream.utils.Utils.toJson
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object CastHelper {
    fun getMediaInfo(
        holder: MetadataHolder,
        index: Int,
        data: JSONObject?,
        subtitles: List<SubtitleData>
    ): MediaInfo {
        val link = holder.currentLinks[index]
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        movieMetadata.putString(
            MediaMetadata.KEY_SUBTITLE,
            link.name
        )

        holder.title?.let {
            movieMetadata.putString(MediaMetadata.KEY_TITLE, it)
        }

        val srcPoster = holder.poster
        if (srcPoster != null) {
            movieMetadata.addImage(WebImage(Uri.parse(srcPoster)))
        }

        var subIndex = 0
        val tracks = subtitles.map {
            MediaTrack.Builder(subIndex++.toLong(), MediaTrack.TYPE_TEXT)
                .setName(it.name)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(it.url)
                .build()
        }

        val contentType = when {
            link.url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            link.url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }
        android.util.Log.i("CastHelper", "Loading url=${link.url} contentType=$contentType")

        val builder = MediaInfo.Builder(link.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(movieMetadata)
            .setMediaTracks(tracks)

        data?.let {
            builder.setCustomData(it)
        }

        return builder.build()
    }

    fun awaitLinks(
        pending: PendingResult<RemoteMediaClient.MediaChannelResult>?,
        callback: (Boolean) -> Unit
    ) {
        if (pending == null) return
        main {
            val res = withContext(Dispatchers.IO) { pending.await() }
            android.util.Log.i("CastHelper", "load result statusCode=${res.status.statusCode} msg=${res.status.statusMessage}")
            when (res.status.statusCode) {
                CastStatusCodes.FAILED -> {
                    callback.invoke(true)
                    android.util.Log.e("CastHelper", "FAILED AND LOAD NEXT")
                }
                else -> Unit
            }
        }
    }

    fun CastSession?.startCast(
        apiName: String,
        isMovie: Boolean,
        title: String?,
        poster: String?,
        currentLinks: List<VideoLink>,
        subtitles: List<SubtitleData>,
        startIndex: Int? = null,
        startTime: Long? = null,
    ): Boolean {
        try {
            if (this == null) return false

            val holder =
                MetadataHolder(
                    apiName,
                    title,
                    poster,
                    currentLinks,
                    subtitles
                )

            val index = if (startIndex == null || startIndex < 0) 0 else startIndex

            val mediaItem =
                getMediaInfo(holder, index, JSONObject(holder.toJson()), subtitles)

            awaitLinks(
                this.remoteMediaClient?.load(
                    MediaLoadRequestData.Builder().setMediaInfo(mediaItem)
                        .setCurrentTime(startTime ?: 0L).build()
                )
            ) {
                if (currentLinks.size > index + 1)
                    startCast(
                        apiName,
                        isMovie,
                        title,
                        poster,
                        currentLinks,
                        subtitles,
                        index + 1,
                        startTime
                    )
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("CastHelper", "startCast failed: ${e::class.simpleName}: ${e.message}", e)
            logError(e)
            return false
        }
    }
}

