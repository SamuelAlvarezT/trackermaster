package com.trackermaster.core.data.di

import android.content.Context
import androidx.room.Room
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackermasterDatabase =
        Room.databaseBuilder(context, TrackermasterDatabase::class.java, "trackermaster.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideHabitDao(db: TrackermasterDatabase): HabitDao = db.habitDao()
    @Provides fun provideHabitLogDao(db: TrackermasterDatabase): HabitLogDao = db.habitLogDao()
    @Provides fun provideMoodDao(db: TrackermasterDatabase): MoodDao = db.moodDao()
    @Provides fun provideExpenseDao(db: TrackermasterDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideFocusDao(db: TrackermasterDatabase): FocusDao = db.focusDao()
    @Provides fun provideJournalDao(db: TrackermasterDatabase): JournalDao = db.journalDao()
    @Provides fun provideAchievementDao(db: TrackermasterDatabase): AchievementDao = db.achievementDao()
}
