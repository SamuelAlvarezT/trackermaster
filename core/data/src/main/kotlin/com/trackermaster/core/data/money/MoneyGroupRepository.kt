package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.MoneyGroupEntity
import com.trackermaster.core.database.MoneyGroupMemberEntity
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class MoneyGroupRepository @Inject constructor(private val db: TrackermasterDatabase) {
    val groups = db.moneyGroupDao().observeGroups().map { rows -> rows.map { row ->
        MoneyGroup(row.group.id, row.group.name, row.group.createdAt, row.members.map { it.memberKey })
    } }

    private data class Member(val key: String, val date: LocalDate?, val account: Long?)

    private suspend fun resolve(keys: List<String>): List<Member> {
        val movements = db.moneyDao().readLedger().moneyMovements()
        return keys.map { key ->
            when {
                key.startsWith("receipt:") -> {
                    val id = key.removePrefix("receipt:")
                    val row = requireNotNull(db.moneyReceiptDao().get(id))
                    val draft = ReceiptDraftCodec.decode(row.draftJson)
                    val movement = movements.firstOrNull { it.entryRequestId == id || (it.transferId == id && it.direction == MoneyDirection.EXPENSE) }
                    if (row.stage == "CONFIRMED") requireNotNull(movement)
                    Member(key, movement?.date ?: draft?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        movement?.accountId ?: draft?.accountId)
                }
                key.startsWith("movement:") || key.startsWith("transfer:") -> {
                    val movement = if (key.startsWith("movement:")) {
                        val id = requireNotNull(key.removePrefix("movement:").toLongOrNull())
                        movements.single { it.id == id }
                    } else movements.single { it.transferId == key.removePrefix("transfer:") && it.direction == MoneyDirection.EXPENSE }
                    val source = if (movement.transferId == null) movement else
                        movements.single { it.transferId == movement.transferId && it.direction == MoneyDirection.EXPENSE }
                    val receipt = (source.entryRequestId ?: source.transferId)?.let { db.moneyReceiptDao().get(it) }
                    val canonical = when {
                        receipt != null -> "receipt:${receipt.id}"
                        source.transferId != null -> "transfer:${source.transferId}"
                        else -> "movement:${source.id}"
                    }
                    Member(canonical, source.date, source.accountId)
                }
                else -> throw IllegalArgumentException("Unknown group member")
            }
        }
    }
    private fun validate(members: List<Member>, rules: MoneyGroupRules) {
        require(members.isNotEmpty() && members.map { it.key }.distinct().size == members.size)
        if (rules.sameDate) require(members.all { it.date != null } && members.map { it.date }.distinct().size == 1)
        if (rules.sameAccount) require(members.all { it.account != null } && members.map { it.account }.distinct().size == 1)
    }
    /** Rules apply on explicit changes only; existing groups are never silently modified. */
    suspend fun create(name: String, keys: List<String>, rules: MoneyGroupRules): String = db.withTransaction {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 120)
        val members = resolve(keys); validate(members, rules)
        val id = UUID.randomUUID().toString()
        db.moneyGroupDao().insert(MoneyGroupEntity(id, cleanName, System.currentTimeMillis()))
        db.moneyGroupDao().add(members.map { MoneyGroupMemberEntity(it.key, id) })
        id
    }
    suspend fun add(groupId: String, keys: List<String>, rules: MoneyGroupRules) = db.withTransaction {
        requireNotNull(db.moneyGroupDao().get(groupId)); require(keys.isNotEmpty())
        val existing = db.moneyGroupDao().members(groupId).map { it.memberKey }
        val combined = resolve(existing + keys); validate(combined, rules)
        db.moneyGroupDao().add(combined.drop(existing.size).map { MoneyGroupMemberEntity(it.key, groupId) })
    }

    /** Removing a group only deletes its index; the underlying receipts and movements remain. */
    suspend fun delete(id: String) = db.moneyGroupDao().delete(id)
}
