package cloud.app.csplayer.download

import android.content.Context
import androidx.work.WorkManager
import cloud.app.csplayer.media.dao.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DownloadModule {

  @Provides
  @Singleton
  fun provideDownloadRepository(downloadDao: DownloadDao): DownloadRepository {
    return DownloadRepositoryImpl(downloadDao)
  }

  @Provides
  @Singleton
  fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
    return WorkManager.getInstance(context)
  }
}
