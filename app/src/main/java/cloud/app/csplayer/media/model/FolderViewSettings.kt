package cloud.app.csplayer.media.model

enum class FolderViewMode(val value: String) {
  HIERARCHICAL("hierarchical"),
  TREE("tree");

  companion object {
    fun fromValue(value: String?): FolderViewMode {
      return entries.find { it.value == value } ?: HIERARCHICAL
    }
  }
}

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

