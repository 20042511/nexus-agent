package ai.nexus.di

import ai.nexus.data.db.NexusDatabase
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexusDatabase =
        Room.databaseBuilder(context, NexusDatabase::class.java, "nexus.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideGoalDao(db: NexusDatabase) = db.goalDao()
    @Provides fun provideTaskDao(db: NexusDatabase) = db.taskDao()
    @Provides fun provideMemoryDao(db: NexusDatabase) = db.memoryDao()
    @Provides fun provideSensorDao(db: NexusDatabase) = db.sensorDao()
    @Provides fun provideSessionDao(db: NexusDatabase) = db.sessionDao()
    @Provides fun provideToolDao(db: NexusDatabase) = db.toolDao()
    @Provides fun provideMessageDao(db: NexusDatabase) = db.messageDao()
}
