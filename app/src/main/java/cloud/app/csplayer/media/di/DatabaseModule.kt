package cloud.app.csplayer.media.di

import android.content.Context
import androidx.room.Room
import cloud.app.csplayer.BuildConfig
import cloud.app.csplayer.media.dao.DownloadDao
import cloud.app.csplayer.media.dao.FavoriteDao
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.dao.MediaPlaybackDao
import cloud.app.csplayer.media.dao.PlaylistDao
import cloud.app.csplayer.media.db.MediaDatabase
import cloud.app.csplayer.media.db.migrations.Migration_8_9
import cloud.app.csplayer.media.db.migrations.Migration_9_10
import cloud.app.csplayer.media.dataSource.MediaStoreDataSource
import cloud.app.csplayer.media.dataSource.MediaStoreDataSourceImpl
import cloud.app.csplayer.media.repository.MediaRepository
import cloud.app.csplayer.media.repository.MediaRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideMediaDatabase(
    @ApplicationContext context: Context
  ): MediaDatabase {
    return Room.databaseBuilder(
      context,
      MediaDatabase::class.java,
      "media_database"
    )
      .addMigrations(Migration_8_9, Migration_9_10)
      .fallbackToDestructiveMigration(BuildConfig.DEBUG) // For development - recreate DB on schema changes
      .build()
  }

  @Provides
  @Singleton
  fun provideMediaDao(database: MediaDatabase): MediaDao {
    return database.mediaDao()
  }

  @Provides
  @Singleton
  fun provideFolderDao(database: MediaDatabase): FolderDao {
    return database.folderDao()
  }

  @Provides
  @Singleton
  fun provideMediaPlaybackDao(database: MediaDatabase): MediaPlaybackDao {
    return database.mediaPlaybackDao()
  }

  @Provides
  @Singleton
  fun provideDownloadDao(database: MediaDatabase): DownloadDao {
    return database.downloadDao()
  }

  @Provides
  @Singleton
  fun providePlaylistDao(database: MediaDatabase): PlaylistDao {
    return database.playlistDao()
  }

  @Provides
  @Singleton
  fun provideFavoriteDao(database: MediaDatabase): FavoriteDao {
    return database.favoriteDao()
  }


  @Provides
  @Singleton
  fun provideMediaStoreDataSource(
    @ApplicationContext context: Context,
  ): MediaStoreDataSource {
    return MediaStoreDataSourceImpl(context)
  }

  @Provides
  @Singleton
  fun provideMediaRepository(
    mediaDao: MediaDao,
    folderDao: FolderDao,
    downloadDao: DownloadDao,
    mediaStore: MediaStoreDataSource,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
  ): MediaRepository {
    return MediaRepositoryImpl(mediaDao, folderDao, downloadDao, mediaStore, scope, context)
  }
}
