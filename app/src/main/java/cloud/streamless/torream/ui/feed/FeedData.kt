package cloud.streamless.torream.ui.feed

import cloud.streamless.torream.download.DownloadState
import cloud.streamless.torream.model.Folder
import cloud.streamless.torream.model.Media
import cloud.streamless.torream.ui.feed.viewholders.horizontal.shelf.ShelfItem

/**
 * Feed item sealed hierarchy used by the Feed screen.
 */
sealed class FeedData {

  enum class Type {
    Video,
    VideoSmall,
    Audio,
    AudioSmall,
    Folder,
    FolderSmall,
    PlayList,
    PlayListSmall,
    Ad,
    HorizontalList,

    HTTPDownload,
    TorrentDownload,
  }

  abstract val id: String
  abstract val title: String
  abstract val type: Type

  data class AdItem(
    override val id: String,
    override val title: String = "",
  ) : FeedData() {
    override var type = Type.Ad
  }

  data class HorizontalList(
    override val id: String,
    override val title: String,
    val items: List<ShelfItem>
  ) : FeedData() {
    override val type: Type = Type.HorizontalList
  }

  data class MediaItem(
    override val id: String,
    override val title: String,
    override var type: Type,
    val media: Media
  ) : FeedData() {

    init {
      require(
        type == Type.Video ||
          type == Type.VideoSmall ||
          type == Type.Audio ||
          type == Type.AudioSmall
      ) {
        "MediaItem.type must be Video, VideoSmall, Audio or AudioSmall, but was $type"
      }
    }
  }

  data class FolderItem(
    override val id: String,
    override val title: String,
    override var type: Type = Type.Folder,
    val folder: Folder,
  ) : FeedData() {

    init {
      require(type == Type.Folder || type == Type.FolderSmall) {
        "FolderItem.type must be Folder or FolderSmall, but was $type"
      }
    }
  }

  data class HttpDownloadItem(
    override val id: String,
    override val title: String,
    val downloadState: DownloadState
  ) : FeedData() {
    override var type: Type = Type.HTTPDownload
  }

  data class TorrentDownloadItem(
    override val id: String,
    override val title: String,
    val downloadState: DownloadState
  ) : FeedData() {
    override var type: Type = Type.TorrentDownload
  }

  data class PlaylistItem(
    override val id: String,
    override val title: String,
    override var type: Type = Type.PlayList,
    val description: String? = null,
    val itemCount: Int = 0,
    val thumbnailPath: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
  ) : FeedData() {

    init {
      require(type == Type.PlayList || type == Type.PlayListSmall) {
        "PlaylistItem.type must be PlayList or PlayListSmall, but was $type"
      }
    }
  }
}
