package cloud.streamless.torream.ui.browse.network

/** One file/directory entry returned when listing a network share path. */
data class BrowseEntry(
  val name: String,
  val isDirectory: Boolean,
  val size: Long,
  val lastModified: Long,
  /** Path relative to the share's base path, used for the next listing call or to build a playback URL. */
  val relativePath: String
)
