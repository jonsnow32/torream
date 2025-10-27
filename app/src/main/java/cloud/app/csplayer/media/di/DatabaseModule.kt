package cloud.app.csplayer.media.di

import android.content.Context
import androidx.room.Room
import cloud.app.csplayer.BuildConfig
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
import cloud.app.csplayer.media.db.MediaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}

