package cloud.streamless.torream.media.di

import android.content.Context
import androidx.room.Room
import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.media.dao.DownloadDao
import cloud.streamless.torream.media.dao.FavoriteDao
import cloud.streamless.torream.media.dao.FolderDao
import cloud.streamless.torream.media.dao.MediaDao
import cloud.streamless.torream.media.dao.MediaPlaybackDao
import cloud.streamless.torream.media.dao.PlaylistDao
import cloud.streamless.torream.media.db.MediaDatabase
import cloud.streamless.torream.media.dataSource.MediaStoreDataSource
import cloud.streamless.torream.media.dataSource.MediaStoreDataSourceImpl
import cloud.streamless.torream.media.repository.MediaRepository
import cloud.streamless.torream.media.repository.MediaRepositoryImpl
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
//      .addMigrations(Migration_1_2)
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
