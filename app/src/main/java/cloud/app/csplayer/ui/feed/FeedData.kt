package cloud.app.csplayer.ui.feed

import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.download.DownloadState
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.model.Media
import cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf.ShelfItem

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
    val downloadId: Long,
    val fileName: String,
    val progress: Int, // 0-100
    val status : DownloadStatus
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
}
