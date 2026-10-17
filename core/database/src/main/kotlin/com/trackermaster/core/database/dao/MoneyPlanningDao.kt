package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyPlanningDao {
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE id=:id") suspend fun budget(id: Long): BudgetEntity?
    @Insert suspend fun insertBudget(value: BudgetEntity): Long
    @Update suspend fun updateBudget(value: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id=:id") suspend fun deleteBudget(id: Long)
    @Query("SELECT * FROM money_plans WHERE archived=0 ORDER BY nextDueEpochDay,id") fun observePlans(): Flow<List<MoneyPlanEntity>>
    @Query("SELECT * FROM money_plans WHERE archived=0 ORDER BY nextDueEpochDay,id") suspend fun plans(): List<MoneyPlanEntity>
    @Query("SELECT * FROM money_plans WHERE id=:id") suspend fun plan(id: String): MoneyPlanEntity?
    @Insert suspend fun insertPlan(value: MoneyPlanEntity)
    @Update suspend fun updatePlan(value: MoneyPlanEntity)
    @Query("SELECT * FROM money_plan_occurrences ORDER BY dueEpochDay DESC") fun observeOccurrences(): Flow<List<MoneyPlanOccurrenceEntity>>
    @Query("SELECT * FROM money_plan_occurrences WHERE planId=:id AND dueEpochDay=:due") suspend fun occurrence(id: String,due: Long): MoneyPlanOccurrenceEntity?
    @Query("SELECT * FROM money_plan_occurrences WHERE movementId=:id") suspend fun linkedMovement(id: Long): MoneyPlanOccurrenceEntity?
    @Insert suspend fun insertOccurrence(value: MoneyPlanOccurrenceEntity)
    @Query("SELECT * FROM money_planning_alerts WHERE eventKey=:key") suspend fun alert(key: String): MoneyPlanningAlertEntity?
    @Upsert suspend fun saveAlert(value: MoneyPlanningAlertEntity)
    @Query("UPDATE money_planning_alerts SET delivered=1 WHERE eventKey=:key AND leaseToken=:token") suspend fun deliverAlert(key: String,token: String)
    @Query("DELETE FROM money_planning_alerts WHERE eventKey=:key AND leaseToken=:token AND delivered=0") suspend fun releaseAlert(key: String,token: String)
}
