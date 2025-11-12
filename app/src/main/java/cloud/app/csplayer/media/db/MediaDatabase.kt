package cloud.app.csplayer.media.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cloud.app.csplayer.media.converters.Converters
import cloud.app.csplayer.media.dao.DownloadDao
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.dao.MediaPlaybackDao
import cloud.app.csplayer.media.entities.FolderEntity
import cloud.app.csplayer.media.entities.MediaEntity
import cloud.app.csplayer.media.entities.MediaPlaybackEntity
import cloud.app.csplayer.media.entities.HttpEntity
import cloud.app.csplayer.media.entities.TorrentEntity

@Database(
  entities = [
    MediaEntity::class,
    FolderEntity::class,
    MediaPlaybackEntity::class,
    TorrentEntity::class,
    HttpEntity::class
  ],
  version = 6,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
  abstract fun mediaDao(): MediaDao
  abstract fun folderDao(): FolderDao
  abstract fun mediaPlaybackDao(): MediaPlaybackDao
  abstract fun downloadDao(): DownloadDao
}

