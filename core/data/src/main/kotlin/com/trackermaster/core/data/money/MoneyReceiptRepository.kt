package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.MoneyReceiptEntity
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyReceiptRepository @Inject constructor(private val db: TrackermasterDatabase, private val money: MoneyRepository) {
    private val dao get() = db.moneyReceiptDao()
    val drafts = dao.observeDrafts().map { rows -> rows.map { it.model() } }
    suspend fun get(id: String): MoneyReceipt = requireNotNull(dao.get(id)).model()
    suspend fun create(file: ReceiptFile): MoneyReceipt {
        val row = MoneyReceiptEntity(UUID.randomUUID().toString(), file.path, file.mime, file.sha256, file.name, createdAt = System.currentTimeMillis())
        dao.insert(row); return row.model()
    }
    suspend fun analyzed(id: String, text: String, preview: String?, unreadable: Boolean): MoneyReceipt = db.withTransaction {
        val old = requireNotNull(dao.get(id)); require(old.stage != "CONFIRMED")
        val body = text.take(ReceiptParser.MAX_TEXT)
        val updated = old.copy(text = body, previewPath = preview, stage = if (unreadable) "UNREADABLE" else "REVIEW",
            draftJson = old.draftJson.ifEmpty { ReceiptDraftCodec.encode(ReceiptDraft.from(ReceiptParser.parse(body))) })
        dao.update(updated); updated.model()
    }
    suspend fun updateDraft(id: String, draft: ReceiptDraft) = db.withTransaction {
        val row = requireNotNull(dao.get(id))
        val json = ReceiptDraftCodec.encode(draft)
        if (row.stage != "CONFIRMED" && row.draftJson != json) dao.update(row.copy(draftJson = json))
    }
    suspend fun discard(id: String) = dao.discard(id)
    private suspend fun workspace(): MoneyWorkspaceData {
        val rows = db.moneyDao().readLedger()
        return MoneyWorkspaceData(MoneyLedgerData(rows.moneySnapshot(), rows.moneyMovements()),
            db.moneyDao().categories().map { MoneyCategory(it.id, it.name, it.isIncome, it.builtinKey) }, emptyList(), emptyList())
    }
    suspend fun duplicates(id: String, draft: ReceiptDraft): List<ReceiptDuplicate> = db.withTransaction {
        matches(requireNotNull(dao.get(id)), draft, workspace())
    }
    private suspend fun matches(row: MoneyReceiptEntity, draft: ReceiptDraft, data: MoneyWorkspaceData): List<ReceiptDuplicate> {
        val movements = data.ledger.movements
        val result = mutableListOf<ReceiptDuplicate>()
        val reference = ReceiptParser.normalize(draft.reference).replace(Regex("[^a-z0-9]"), "")
        val provider = ReceiptParser.parse(row.text).provider
        dao.activeSources(row.id).forEach { old ->
            val match = movements.firstOrNull { it.entryRequestId == old.id || it.transferId == old.id } ?: return@forEach
            if (old.sha256 == row.sha256) result.add(ReceiptDuplicate(match.id, ReceiptDuplicateReason.FILE))
            val oldReference = ReceiptDraftCodec.decode(old.draftJson)?.reference.orEmpty()
            if (provider != null && provider == ReceiptParser.parse(old.text).provider && reference.isNotEmpty() &&
                reference == ReceiptParser.normalize(oldReference).replace(Regex("[^a-z0-9]"), "")) result.add(ReceiptDuplicate(match.id, ReceiptDuplicateReason.REFERENCE))
        }
        val candidate = runCatching { ReceiptValidation.movement(draft, data, row.id, row.path) }.getOrNull()
        if (candidate != null) movements.filter { it.accountId == candidate.accountId && it.amount == candidate.amount &&
            it.date == candidate.date && it.direction == candidate.direction && it.entryRequestId != row.id && it.transferId != row.id }
            .forEach { result.add(ReceiptDuplicate(it.id, ReceiptDuplicateReason.SIMILAR)) }
        return result.distinct()
    }
    /** Receipt link and ledger entry commit together. Recheck duplicates inside the same SQLite transaction. */
    suspend fun confirm(id: String, draft: ReceiptDraft): Long = db.withTransaction {
        val row = requireNotNull(dao.get(id))
        val json = ReceiptDraftCodec.encode(draft)
        if (row.stage == "CONFIRMED") {
            if (row.draftJson != json) throw MoneyWriteException(MoneyWriteFailure.REQUEST_CONFLICT)
            return@withTransaction db.moneyDao().requestedMovement(id)?.id ?: db.moneyDao().transferMovements(id).firstOrNull()?.id
                ?: throw MoneyWriteException(MoneyWriteFailure.STALE_ENTRY)
        }
        val status = ReceiptParser.parse(row.text).status
        if (!draft.confirmed || status == ReceiptBankStatus.PENDING || status == ReceiptBankStatus.FAILED)
            throw ReceiptException(ReceiptFailure.BANK_NOT_CONFIRMED)
        val data = workspace()
        val duplicates = matches(row, draft, data)
        if (duplicates.isNotEmpty() && draft.duplicateAcknowledgement != duplicates.fingerprint())
            throw ReceiptException(ReceiptFailure.DUPLICATE_CHANGED)
        val movement = ReceiptValidation.movement(draft, data, id, row.path)
        val movementId = if (draft.kind == ReceiptEntryKind.TRANSFER) {
            val target = data.ledger.snapshot.accounts.single { it.account.id == draft.targetAccountId }.account
            val received = if (target.initialBalance.currencyCode == draft.currency) movement.amount else
                ReceiptValidation.amount(draft.receivedAmount, target.initialBalance.currencyCode)
            money.transfer(movement.accountId, target.id, movement.amount, received, draft.pocketId, draft.targetPocketId,
                movement.date, draft.note, id, row.path)
            db.moneyDao().transferMovements(id).first().id
        } else money.record(movement)
        dao.update(row.copy(stage = "CONFIRMED", draftJson = json))
        movementId
    }
    private fun MoneyReceiptEntity.model() = MoneyReceipt(id,path,mimeType,sha256,name,text,previewPath,stage,draftJson,createdAt)
}
