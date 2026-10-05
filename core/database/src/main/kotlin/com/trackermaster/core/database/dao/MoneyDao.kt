package com.trackermaster.core.database.dao

import androidx.room.*
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

data class MoneyAccountLedger(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId") val movements: List<TransactionEntity>,
    @Relation(parentColumn = "id", entityColumn = "accountId") val pockets: List<MoneyPocketEntity>,
)

@Dao
interface MoneyDao {
    @Transaction @Query("SELECT * FROM accounts ORDER BY id")
    fun observeLedger(): Flow<List<MoneyAccountLedger>>
    @Transaction @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun readLedger(): List<MoneyAccountLedger>
    @Query("SELECT * FROM money_spaces ORDER BY id") fun observeSpaces(): Flow<List<MoneySpaceEntity>>
    @Query("SELECT * FROM expense_categories ORDER BY id") fun observeCategories(): Flow<List<ExpenseCategoryEntity>>
    @Query("SELECT * FROM expense_categories ORDER BY id") suspend fun categories(): List<ExpenseCategoryEntity>
    @Query("SELECT * FROM budgets ORDER BY id") fun observeBudgets(): Flow<List<BudgetEntity>>
    @Insert suspend fun insertCategory(category: ExpenseCategoryEntity): Long
    @Query("SELECT * FROM money_spaces WHERE id = :id") suspend fun space(id: Long): MoneySpaceEntity?
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: Long): AccountEntity?
    @Query("SELECT * FROM money_pockets WHERE id = :id") suspend fun pocket(id: Long): MoneyPocketEntity?
    @Query("SELECT * FROM expense_categories WHERE id = :id") suspend fun category(id: Long): ExpenseCategoryEntity?
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun movement(id: Long): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE entryRequestId = :requestId") suspend fun requestedMovement(requestId: String): TransactionEntity?
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteMovement(id: Long)
    @Query("SELECT * FROM transactions WHERE transferId = :id ORDER BY id") suspend fun transferMovements(id: String): List<TransactionEntity>
    @Query("SELECT * FROM money_transfers WHERE id = :id") suspend fun transfer(id: String): MoneyTransferEntity?
    @Insert suspend fun insertSpace(space: MoneySpaceEntity): Long
    @Insert suspend fun insertAccount(account: AccountEntity): Long
    @Update suspend fun updateAccount(account: AccountEntity)
    @Insert suspend fun insertPocket(pocket: MoneyPocketEntity): Long
    @Insert suspend fun insertMovement(movement: TransactionEntity): Long
    @Update suspend fun updateMovement(movement: TransactionEntity)
    @Insert suspend fun insertTransfer(transfer: MoneyTransferEntity)
    @Query("DELETE FROM transactions WHERE transferId = :id") suspend fun deleteTransferMovements(id: String)
    @Query("DELETE FROM money_transfers WHERE id = :id") suspend fun deleteTransfer(id: String)
    @Query("UPDATE transactions SET spaceId = :spaceId WHERE accountId = :accountId AND pocketId IS NULL")
    suspend fun classifyMovements(accountId: Long, spaceId: Long)
}
