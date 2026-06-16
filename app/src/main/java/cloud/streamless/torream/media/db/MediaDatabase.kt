package cloud.streamless.torream.media.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cloud.streamless.torream.media.converters.Converters
import cloud.streamless.torream.media.dao.DownloadDao
import cloud.streamless.torream.media.dao.FavoriteDao
import cloud.streamless.torream.media.dao.FavoriteEntity
import cloud.streamless.torream.media.dao.FolderDao
import cloud.streamless.torream.media.dao.MediaDao
import cloud.streamless.torream.media.dao.MediaPlaybackDao
import cloud.streamless.torream.media.dao.PlaylistDao
import cloud.streamless.torream.media.entities.FolderEntity
import cloud.streamless.torream.media.entities.MediaEntity
import cloud.streamless.torream.media.entities.MediaPlaybackEntity
import cloud.streamless.torream.media.entities.HttpEntity
import cloud.streamless.torream.media.entities.TorrentEntity
import cloud.streamless.torream.media.entities.PlaylistEntity
import cloud.streamless.torream.media.entities.PlaylistItemEntity

@Database(
  entities = [
    MediaEntity::class,
    FolderEntity::class,
    MediaPlaybackEntity::class,
    TorrentEntity::class,
    HttpEntity::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
    FavoriteEntity::class
  ],
  version = 4,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
  abstract fun mediaDao(): MediaDao
  abstract fun folderDao(): FolderDao
  abstract fun mediaPlaybackDao(): MediaPlaybackDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0")
      }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0")
      }
    }
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE http_downloads ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE torrents ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0")
      }
    }
  }
  abstract fun downloadDao(): DownloadDao
  abstract fun playlistDao(): PlaylistDao
  abstract fun favoriteDao(): FavoriteDao
}
