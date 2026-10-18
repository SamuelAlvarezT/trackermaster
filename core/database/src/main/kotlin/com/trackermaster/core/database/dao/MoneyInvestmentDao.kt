package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

data class MoneyInvestmentLedger(@Embedded val position: MoneyInvestmentEntity,
    @Relation(parentColumn = "id", entityColumn = "investmentId") val events: List<MoneyInvestmentEventEntity>)

@Dao
interface MoneyInvestmentDao {
    @Transaction @Query("SELECT * FROM money_investments ORDER BY name,id") fun observeBook(): Flow<List<MoneyInvestmentLedger>>
    @Query("SELECT * FROM money_investments ORDER BY name,id") fun observePositions(): Flow<List<MoneyInvestmentEntity>>
    @Query("SELECT * FROM money_investment_events ORDER BY dateEpochDay,sequence") fun observeEvents(): Flow<List<MoneyInvestmentEventEntity>>
    @Query("SELECT * FROM money_investments WHERE id=:id") suspend fun position(id: String): MoneyInvestmentEntity?
    @Query("SELECT * FROM money_investment_events WHERE investmentId=:id ORDER BY dateEpochDay,sequence") suspend fun events(id: String): List<MoneyInvestmentEventEntity>
    @Query("SELECT * FROM money_investment_events WHERE requestId=:id") suspend fun request(id: String): MoneyInvestmentEventEntity?
    @Query("SELECT * FROM money_investment_events WHERE sequence=:sequence") suspend fun event(sequence: Long): MoneyInvestmentEventEntity?
    @Insert suspend fun insert(value: MoneyInvestmentEntity)
    @Update suspend fun update(value: MoneyInvestmentEntity)
    @Insert suspend fun insertEvent(value: MoneyInvestmentEventEntity): Long
    @Query("DELETE FROM money_investment_events WHERE sequence=:sequence") suspend fun deleteEvent(sequence: Long)
}
