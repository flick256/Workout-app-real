package app.forge.fitness.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.forge.fitness.data.db.ForgeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ForgeDatabase =
        Room.databaseBuilder(context, ForgeDatabase::class.java, ForgeDatabase.NAME)
            .build()

    @Provides
    fun provideExerciseDao(db: ForgeDatabase) = db.exerciseDao()

    @Provides
    fun provideWorkoutDao(db: ForgeDatabase) = db.workoutDao()

    @Provides
    fun provideMetaDao(db: ForgeDatabase) = db.metaDao()

    @Provides
    fun provideBodyMetricDao(db: ForgeDatabase) = db.bodyMetricDao()

    @Provides
    fun provideRoutineDao(db: ForgeDatabase) = db.routineDao()

    @Provides
    fun providePhotoDao(db: ForgeDatabase) = db.photoDao()

    @Provides
    fun provideDemoDao(db: ForgeDatabase) = db.demoDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideTimeSource(): TimeSource = TimeSource { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_prefs") }
}
