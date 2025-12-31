package com.tv.apps.zippy.media.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tv.apps.zippy.media.converters.Converters
import com.tv.apps.zippy.media.dao.DownloadDao
import com.tv.apps.zippy.media.dao.FavoriteDao
import com.tv.apps.zippy.media.dao.FavoriteEntity
import com.tv.apps.zippy.media.dao.FolderDao
import com.tv.apps.zippy.media.dao.MediaDao
import com.tv.apps.zippy.media.dao.MediaPlaybackDao
import com.tv.apps.zippy.media.dao.PlaylistDao
import com.tv.apps.zippy.media.entities.FolderEntity
import com.tv.apps.zippy.media.entities.MediaEntity
import com.tv.apps.zippy.media.entities.MediaPlaybackEntity
import com.tv.apps.zippy.media.entities.HttpEntity
import com.tv.apps.zippy.media.entities.TorrentEntity
import com.tv.apps.zippy.media.entities.PlaylistEntity
import com.tv.apps.zippy.media.entities.PlaylistItemEntity

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
  version = 10,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
  abstract fun mediaDao(): MediaDao
  abstract fun folderDao(): FolderDao
  abstract fun mediaPlaybackDao(): MediaPlaybackDao
  abstract fun downloadDao(): DownloadDao
  abstract fun playlistDao(): PlaylistDao
  abstract fun favoriteDao(): FavoriteDao
}
