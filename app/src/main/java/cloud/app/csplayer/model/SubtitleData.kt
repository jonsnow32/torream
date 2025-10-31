package cloud.app.csplayer.model

enum class SubtitleStatus {
  IS_ACTIVE,
  REQUIRES_RELOAD,
  NOT_FOUND,
}

enum class SubtitleOrigin {
  URL,
  DOWNLOADED_FILE,
  EMBEDDED_IN_VIDEO
}

/**
 * @param name To be displayed in the player
 * @param url Url for the subtitle, when EMBEDDED_IN_VIDEO this variable is used as the real backend id
 * @param headers if empty it will use the base onlineDataSource headers else only the specified headers
 * @param languageCode Not guaranteed to follow any standard. Could be something like "English 4" or "en".
 * */
data class SubtitleData(
  val name: String,
  val url: String,
  val origin: SubtitleOrigin,
  val mimeType: String,
  val headers: Map<String, String>,
  val languageCode: String?
) {
  /** Internal ID for exoplayer, unique for each link*/
  fun getId(): String {
    return if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url
    else "$url|$name"
  }
}
