package cloud.app.csplayer.ui.feed

import cloud.app.csplayer.model.Audio
import cloud.app.csplayer.model.Folder
import cloud.app.csplayer.model.Video

object MockData {
  val smallFeed = listOf(
    FeedData.FolderItem(
      id = "folder_1",
      title = "Sample Folder",
      folder = Folder(
        id = "folder_1",
        title = "Sample Folder",
        path = "folder_1"
      ),
      type = FeedData.Type.FolderSmall
    ),
    FeedData.FolderItem(
      id = "folder_2",
      title = "Sample Folder",
      folder = Folder(
        id = "folder_2",
        title = "Sample Folder",
        path = "folder_2"
      ),
      type = FeedData.Type.FolderSmall
    ),
    FeedData.FolderItem(
      id = "folder_3",
      title = "Sample Folder",
      folder = Folder(
        id = "folder_3",
        title = "Sample Folder",
        path = "folder_3"
      ),
      type = FeedData.Type.FolderSmall
    ),
    FeedData.VideoItem(
      id = "folder_4",
      title = "Sample Folder",
      video = Video(
        id = "folder_4",
        title = "Sample Folder",
      ),
      type = FeedData.Type.VideoSmall
    ),
    FeedData.VideoItem(
      id = "folder_5",
      title = "Sample Folder",
      video = Video(
        id = "folder_5",
        title = "Sample Folder",
      ),
      type = FeedData.Type.VideoSmall
    ),
    FeedData.VideoItem(
      id = "folder_6",
      title = "Sample Folder",
      video = Video(
        id = "folder_6",
        title = "Sample Folder",
      ),
      type = FeedData.Type.VideoSmall
    ),
    FeedData.AudioItem(
      id = "folder_7",
      title = "Sample Folder",
      audio = Audio(
        id = "folder_7",
        title = "Sample Folder",
      ),
      type = FeedData.Type.AudioSmall
    ),
    FeedData.AudioItem(
      id = "folder_8",
      title = "Sample Folder",
      audio = Audio(
        id = "folder_8",
        title = "Sample Folder",
      ),
      type = FeedData.Type.AudioSmall
    ),
    FeedData.AudioItem(
      id = "folder_9",
      title = "Sample Folder",
      audio = Audio(
        id = "folder_9",
        title = "Sample Folder",
      ),
      type = FeedData.Type.AudioSmall
    )
  )

  val sampleFeed1 = listOf(
    FeedData.AdItem(
      id = "ad_1",
      title = "Sample Ad",
      isNativeAdPlaceholder = true
    ),
    FeedData.VideoItem(
      id = "1",
      title = "Sample Video 1",
      video = Video(
        id = "1",
        title = "Sample Video 1",
        description = "This is a sample video description.",
      )
    ),
    FeedData.VideoItem(
      id = "12",
      title = "Sample Video 1",
      video = Video(
        id = "12",
        title = "Sample Video 1",
        description = "This is a sample video description.",
      )
    ),
    FeedData.VideoItem(
      id = "13",
      title = "Sample Video 1",
      video = Video(
        id = "14",
        title = "Sample Video 1",
        description = "This is a sample video description.",
      )
    ),
    FeedData.AudioItem(
      id = "2",
      title = "Sample Audio 1",
      audio = Audio(
        id = "2",
        title = "Sample Audio 1",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "2",
      title = "Sample Audio 1",
      audio = Audio(
        id = "2",
        title = "Sample Audio 1",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "2",
      title = "Sample Audio 1",
      audio = Audio(
        id = "2",
        title = "Sample Audio 1",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "3",
      title = "Sample Audio 1",
      audio = Audio(
        id = "3",
        title = "Sample Audio2",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "3",
      title = "Sample Audio 1",
      audio = Audio(
        id = "3",
        title = "Sample Audio2",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "3",
      title = "Sample Audio 1",
      audio = Audio(
        id = "3",
        title = "Sample Audio2",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "3",
      title = "Sample Audio 1",
      audio = Audio(
        id = "3",
        title = "Sample Audio2",
        subtitle = "Sample Artist"
      )
    ),
    FeedData.AudioItem(
      id = "3",
      title = "Sample Audio 1",
      audio = Audio(
        id = "3",
        title = "Sample Audio2",
        subtitle = "Sample Artist"
      )
    )
  )
}
