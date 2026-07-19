package com.trackermaster.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
            .addMigrations(MIGRATION_3_4)
            .build()

    @Provides fun provideHabitDao(db: TrackermasterDatabase): HabitDao = db.habitDao()
    @Provides fun provideHabitLogDao(db: TrackermasterDatabase): HabitLogDao = db.habitLogDao()
    @Provides fun provideMoodDao(db: TrackermasterDatabase): MoodDao = db.moodDao()
    @Provides fun provideExpenseDao(db: TrackermasterDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideFocusDao(db: TrackermasterDatabase): FocusDao = db.focusDao()
    @Provides fun provideJournalDao(db: TrackermasterDatabase): JournalDao = db.journalDao()
    @Provides fun provideAchievementDao(db: TrackermasterDatabase): AchievementDao = db.achievementDao()
    @Provides fun provideTaskDao(db: TrackermasterDatabase): TaskDao = db.taskDao()

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns to tasks table
            database.execSQL("ALTER TABLE tasks ADD COLUMN createdAtEpoch INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE tasks ADD COLUMN subtasksJson TEXT NOT NULL DEFAULT ''")
            
            // Add new column to journal_entries table
            database.execSQL("ALTER TABLE journal_entries ADD COLUMN moodLevel INTEGER")
            
            // Add new column to transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN imageUri TEXT")
            
            // Create attachments table if it doesn't exist
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS attachments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entryId INTEGER NOT NULL,
                    path TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    FOREIGN KEY (entryId) REFERENCES journal_entries(id) ON DELETE CASCADE
                )
            """.trimIndent())
        }
    }
}
