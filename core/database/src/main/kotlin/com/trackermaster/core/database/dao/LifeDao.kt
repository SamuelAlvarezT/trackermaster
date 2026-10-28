package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeDao {
    @Query("SELECT * FROM life_goals ORDER BY id") fun goals(): Flow<List<LifeGoalEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveGoal(value: LifeGoalEntity)
    @Query("SELECT * FROM life_goal_versions ORDER BY savedAt,id") fun goalVersions(): Flow<List<LifeGoalVersionEntity>>
    @Insert suspend fun saveGoalVersion(value: LifeGoalVersionEntity)
    @Query("DELETE FROM life_goals WHERE id=:id") suspend fun deleteGoal(id: String)
    @Query("SELECT * FROM life_reviews ORDER BY createdAt DESC") fun reviews(): Flow<List<LifeReviewEntity>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveReview(value: LifeReviewEntity)
    @Query("SELECT * FROM journal_versions WHERE entryId=:id ORDER BY id DESC") fun versions(id: Long): Flow<List<JournalVersionEntity>>
    @Insert suspend fun saveVersion(value: JournalVersionEntity)
    @Query("SELECT * FROM journal_contributions WHERE entryId=:id") suspend fun contribution(id: Long): JournalContributionEntity?
    @Query("SELECT * FROM journal_contributions WHERE habitId=:habit AND day=:day") suspend fun contributions(habit: Long,day: Long): List<JournalContributionEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveContribution(value: JournalContributionEntity)
    @Query("DELETE FROM journal_contributions WHERE entryId=:id") suspend fun deleteContribution(id: Long)
    @Query("DELETE FROM journal_versions WHERE entryId=:id") suspend fun deleteVersions(id: Long)
    @Query("DELETE FROM attachments WHERE entryId=:id") suspend fun deleteAttachments(id: Long)
    @Query("SELECT * FROM focus_habit_contributions ORDER BY sessionId,dateEpochDay") fun focusContributions(): Flow<List<FocusHabitContributionEntity>>
    @Query("SELECT * FROM money_savings_goals") fun savings(): Flow<List<SavingsGoalEntity>>
}
