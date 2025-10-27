package cloud.app.csplayer.media.di

import android.content.Context
import cloud.app.csplayer.media.dao.FolderDao
import cloud.app.csplayer.media.dao.MediaDao
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
object MediaModule {

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
    mediaStore: MediaStoreDataSource,
    @ApplicationScope scope: CoroutineScope,
  ): MediaRepository {
    return MediaRepositoryImpl(mediaDao, folderDao, mediaStore, scope)
  }
}
