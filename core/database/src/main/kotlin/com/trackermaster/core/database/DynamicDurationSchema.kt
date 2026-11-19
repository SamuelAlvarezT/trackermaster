package com.trackermaster.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habit_dynamic_goals", primaryKeys = ["habitId", "epochDay"])
data class HabitDynamicGoalEntity(
    val habitId: Long,
    val epochDay: Long,
    val targetMinutes: Long,
    val previousTargetMinutes: Long?,
    val previousProgressMs: Long?,
    val reason: String,
)

@Dao
interface HabitDynamicGoalDao {
    @Query("SELECT * FROM habit_dynamic_goals WHERE habitId=:habitId ORDER BY epochDay")
    suspend fun forHabit(habitId: Long): List<HabitDynamicGoalEntity>
    @Query("SELECT * FROM habit_dynamic_goals WHERE habitId=:habitId AND epochDay=:day")
    fun observe(habitId: Long, day: Long): Flow<HabitDynamicGoalEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(goal: HabitDynamicGoalEntity)
    @Query("DELETE FROM habit_dynamic_goals WHERE habitId=:habitId")
    suspend fun deleteForHabit(habitId: Long)
}

object DynamicDurationMigration {
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicDurationEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicInitialMinutes INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicMinimumMinutes INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicMaximumMinutes INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicAdjustmentPercent INTEGER NOT NULL DEFAULT 100")
            db.execSQL("ALTER TABLE habits ADD COLUMN dynamicStartEpochDay INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS habit_dynamic_goals (habitId INTEGER NOT NULL, epochDay INTEGER NOT NULL, targetMinutes INTEGER NOT NULL, previousTargetMinutes INTEGER, previousProgressMs INTEGER, reason TEXT NOT NULL, PRIMARY KEY(habitId, epochDay))")
        }
    }
}
