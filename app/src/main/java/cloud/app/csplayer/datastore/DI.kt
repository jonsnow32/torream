package cloud.app.csplayer.datastore

import android.content.Context
import android.content.SharedPreferences
import cloud.app.csplayer.utils.DataStore.getDefaultSharedPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class DI {
  @Provides
  @Singleton
  fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
    return context.getDefaultSharedPrefs()
  }
}
