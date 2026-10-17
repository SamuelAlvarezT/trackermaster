package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyCommitmentDao {
    @Query("SELECT * FROM money_debts ORDER BY id") suspend fun debts(): List<MoneyDebtEntity>
    @Query("SELECT * FROM money_debts ORDER BY person, id") fun observeDebts(): Flow<List<MoneyDebtEntity>>
    @Query("SELECT * FROM money_debt_payments ORDER BY id") fun observePayments(): Flow<List<DebtPaymentEntity>>
    @Query("SELECT * FROM money_savings_goals ORDER BY name, id") fun observeGoals(): Flow<List<SavingsGoalEntity>>
    @Query("SELECT * FROM money_debts WHERE id = :id") suspend fun debt(id: String): MoneyDebtEntity?
    @Query("SELECT * FROM money_savings_goals WHERE id = :id") suspend fun goal(id: String): SavingsGoalEntity?
    @Query("SELECT * FROM money_debt_payments WHERE debtId = :id") suspend fun payments(id: String): List<DebtPaymentEntity>
    @Query("SELECT * FROM money_debt_payments WHERE id = :id") suspend fun payment(id: String): DebtPaymentEntity?
    @Query("SELECT * FROM money_debt_payments WHERE movementId = :id") suspend fun linked(id: Long): DebtPaymentEntity?
    @Insert suspend fun insertDebt(value: MoneyDebtEntity)
    @Update suspend fun updateDebt(value: MoneyDebtEntity)
    @Insert suspend fun insertGoal(value: SavingsGoalEntity)
    @Update suspend fun updateGoal(value: SavingsGoalEntity)
    @Insert suspend fun insertPayment(value: DebtPaymentEntity)
    @Query("DELETE FROM money_debt_payments WHERE id = :id") suspend fun deletePayment(id: String)
}
