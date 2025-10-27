package cloud.app.csplayer.media.db

import androidx.room.Database
import androidx.room.RoomDatabase
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.entities.FolderEntity
import cloud.app.csplayer.media.entities.MediaEntity

@Database(
  entities = [
    MediaEntity::class,
    FolderEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {
  abstract fun mediaDao(): MediaDao
  abstract fun folderDao(): FolderDao
}

