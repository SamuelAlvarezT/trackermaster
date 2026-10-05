package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.MoneyReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyReceiptDao {
    @Query("SELECT * FROM money_receipts WHERE stage != 'CONFIRMED' ORDER BY createdAt DESC")
    fun observeDrafts(): Flow<List<MoneyReceiptEntity>>
    @Query("SELECT * FROM money_receipts WHERE id = :id") suspend fun get(id: String): MoneyReceiptEntity?
    @Query("SELECT * FROM money_receipts WHERE id = :id") fun observe(id: String): Flow<MoneyReceiptEntity?>
    @Insert suspend fun insert(row: MoneyReceiptEntity)
    @Update suspend fun update(row: MoneyReceiptEntity)
    @Query("DELETE FROM money_receipts WHERE id = :id AND stage != 'CONFIRMED'") suspend fun discard(id: String)
    @Query("SELECT r.* FROM money_receipts r WHERE r.stage = 'CONFIRMED' AND r.id != :excludeId AND EXISTS (SELECT 1 FROM transactions t WHERE t.entryRequestId = r.id OR t.transferId = r.id)")
    suspend fun activeSources(excludeId: String): List<MoneyReceiptEntity>
}
