package cloud.app.csplayer.media.model

enum class MediaTypeFilter(val value: String) {
  ALL("all"),
  VIDEO("video"),
  AUDIO("audio");

  companion object {
    fun fromValue(value: String?): MediaTypeFilter {
      return entries.find { it.value == value } ?: ALL
    }
  }
}

