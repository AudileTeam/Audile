package com.mrsep.musicrecognizer.core.database.di

import android.content.Context
import androidx.room.Room
import com.mrsep.musicrecognizer.core.database.ApplicationDatabase
import com.mrsep.musicrecognizer.core.database.ApplicationDatabase.Companion.DATABASE_NAME
import com.mrsep.musicrecognizer.core.database.migration.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class RoomModule {

    @Provides
    @Singleton
    fun provideRoomDatabase(@ApplicationContext appContext: Context): ApplicationDatabase {
        return Room.databaseBuilder(
            appContext,
            ApplicationDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                Migration5To6,
                Migration6To7,
                Migration7To8,
                Migration8To9,
                Migration7To9,
                Migration9To10(appContext),
                Migration10To11,
                Migration11To12,
                Migration12To13,
                Migration13To14,
            )
            .build()
    }
}
