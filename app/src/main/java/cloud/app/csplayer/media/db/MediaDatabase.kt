package cloud.app.csplayer.media.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.dao.MediaPlaybackDao
import cloud.app.csplayer.media.entities.FolderEntity
import cloud.app.csplayer.media.entities.MediaEntity
import cloud.app.csplayer.media.entities.MediaPlaybackEntity
import cloud.app.csplayer.media.dao.TorrentDao
import cloud.app.csplayer.media.entities.TorrentEntity

@Database(
  entities = [
    MediaEntity::class,
    FolderEntity::class,
    MediaPlaybackEntity::class,
    TorrentEntity::class
  ],
  version = 4,
  exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {
  abstract fun mediaDao(): MediaDao
  abstract fun folderDao(): FolderDao
  abstract fun mediaPlaybackDao(): MediaPlaybackDao
  abstract fun torrentDao(): TorrentDao
}

