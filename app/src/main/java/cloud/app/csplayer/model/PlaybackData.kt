package cloud.app.csplayer.model

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.FileProvider
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Main data class for passing playback information between fragments and apps.
 * Use this instead of passing multiple arrays in Bundle.
 *
 * For inter-fragment communication: pass directly via Bundle with Parcelable
 * For inter-app communication: serialize to JSON, share via FileProvider
 */
@Parcelize
@Serializable
data class PlaybackData(
  val title: String? = null,
  val videoLinks: List<VideoLink> = emptyList(),
  val videoStartIndex: Int = 0,

  //Use for series playback to indicate if continuing same episode
  val isSameEpisode: Boolean = true,
  val subtitles: List<SubtitleData> = emptyList(),
  val subtitleStartIndex: Int = 0,

  val hasAd: Boolean = false
) : Parcelable {
  companion object {
    const val KEY_PLAYBACK_DATA = "playback_data"
    const val KEY_PLAYBACK_JSON_URI = "playback_json_uri"

    private val json = Json {
      prettyPrint = true
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

    /**
     * Serialize PlaybackData to JSON string
     */
    fun toJson(playbackData: PlaybackData): String {
      return json.encodeToString(PlaybackData.serializer(), playbackData)
    }

    /**
     * Deserialize JSON string to PlaybackData
     */
    fun fromJson(jsonString: String): PlaybackData {
      return json.decodeFromString(PlaybackData.serializer(), jsonString)
    }

    /**
     * Write PlaybackData to cache file and get content:// URI via FileProvider
     * Use this for sharing large data between apps
     */
    fun writeToFileAndGetUri(
      context: Context,
      playbackData: PlaybackData,
      filename: String = "playback_data.json"
    ): Uri {
      val file = File(context.cacheDir, filename)
      file.writeText(toJson(playbackData))
      return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
      )
    }

    /**
     * Read PlaybackData from content:// URI
     * Use this when receiving data from another app via FileProvider
     */
    fun readFromUri(context: Context, uri: Uri): PlaybackData? {
      return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          val jsonString = stream.bufferedReader().use { it.readText() }
          fromJson(jsonString)
        }
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }
  }
}

