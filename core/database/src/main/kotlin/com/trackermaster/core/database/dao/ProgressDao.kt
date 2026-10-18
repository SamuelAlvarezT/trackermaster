package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM habit_schedule_history ORDER BY habitId,effectiveFromEpochDay")
    fun observeHistory(): Flow<List<HabitScheduleEntity>>
    @Query("SELECT * FROM habit_schedule_history WHERE habitId=:habitId ORDER BY effectiveFromEpochDay")
    suspend fun history(habitId: Long): List<HabitScheduleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(value: HabitScheduleEntity)
    @Query("SELECT * FROM habit_logs ORDER BY dateEpoch,id")
    fun observeAllLogs(): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM focus_runtime WHERE id=1")
    fun observeRuntime(): Flow<FocusRuntimeEntity?>
    @Query("SELECT * FROM focus_runtime WHERE id=1")
    suspend fun runtime(): FocusRuntimeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRuntime(value: FocusRuntimeEntity)
    @Query("SELECT * FROM focus_sessions WHERE id=:id")
    suspend fun session(id: Long): FocusSessionEntity?
    @Query("SELECT * FROM focus_habit_contributions WHERE sessionId=:sessionId AND dateEpochDay=:day")
    suspend fun contribution(sessionId: Long, day: Long): FocusHabitContributionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContribution(value: FocusHabitContributionEntity)
    // One SQLite snapshot avoids briefly counting a newly contributed session twice.
    @Query("""SELECT COALESCE((SELECT SUM(durationMs) FROM focus_habit_contributions WHERE dateEpochDay=:day),0) +
        COALESCE((SELECT SUM(actualDurationMs) FROM focus_sessions s
            WHERE NOT EXISTS(SELECT 1 FROM focus_habit_contributions c WHERE c.sessionId=s.id)
            AND COALESCE(s.completedAtEpoch,s.startedAtEpoch)>=:fromEpoch
            AND COALESCE(s.completedAtEpoch,s.startedAtEpoch)<:throughEpoch),0)""")
    fun observeFocusTime(day: Long,fromEpoch: Long,throughEpoch: Long): Flow<Long>
}
