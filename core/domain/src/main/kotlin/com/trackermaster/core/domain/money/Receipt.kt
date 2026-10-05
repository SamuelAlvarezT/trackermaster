package com.trackermaster.core.domain.money

import java.time.LocalDate

enum class ReceiptBankStatus { SUCCESS, PENDING, FAILED, UNKNOWN }
enum class ReceiptEntryKind { INCOME, EXPENSE, TRANSFER }
data class ReceiptAnalysis(val amount: Money? = null, val date: LocalDate? = null,
    val direction: MoneyDirection? = null, val status: ReceiptBankStatus = ReceiptBankStatus.UNKNOWN,
    val reference: String? = null, val provider: String? = null, val sender: String? = null, val recipient: String? = null)
/** A suggestion is never a ledger write; missing fields need an explicit choice. */
data class ReceiptDraft(val kind: ReceiptEntryKind? = null, val amount: String = "", val currency: String = "COP",
    val date: String = "", val accountId: Long? = null, val pocketId: Long? = null, val categoryId: Long? = null,
    val targetAccountId: Long? = null, val targetPocketId: Long? = null, val receivedAmount: String = "",
    val note: String = "", val reference: String = "", val confirmed: Boolean = false, val duplicateAcknowledgement: String? = null) {
    companion object {
        fun from(analysis: ReceiptAnalysis) = ReceiptDraft(kind = analysis.direction?.let {
            if (it == MoneyDirection.INCOME) ReceiptEntryKind.INCOME else ReceiptEntryKind.EXPENSE },
            amount = analysis.amount?.decimal?.stripTrailingZeros()?.toPlainString().orEmpty(),
            currency = analysis.amount?.currencyCode ?: "COP", date = analysis.date?.toString().orEmpty(), reference = analysis.reference.orEmpty())
    }
}
enum class ReceiptDuplicateReason { FILE, REFERENCE, SIMILAR }
data class ReceiptDuplicate(val movementId: Long, val reason: ReceiptDuplicateReason)
fun List<ReceiptDuplicate>.fingerprint(): String = distinct().sortedWith(compareBy<ReceiptDuplicate> { it.movementId }.thenBy { it.reason.name })
    .joinToString("|") { "${it.movementId}:${it.reason.name}" }
data class MoneyReceipt(val id: String, val path: String, val mimeType: String, val sha256: String,
    val name: String, val text: String = "", val previewPath: String? = null,
    val stage: String = "READING", val draftJson: String = "", val createdAt: Long = 0)
enum class ReceiptFailure { UNSUPPORTED, TOO_LARGE, UNREADABLE, MULTIPLE_FILES, DUPLICATE_CHANGED, BANK_NOT_CONFIRMED, INVALID_DRAFT }
class ReceiptException(val reason: ReceiptFailure) : IllegalArgumentException(reason.name)
object ReceiptValidation {
    fun amount(text: String, currency: String): Money {
        require(currency in listOf("COP", "USD") && Regex("\\d+(?:[.,]\\d{1,2})?").matches(text.trim()))
        return Money.fromDecimal(text.trim().replace(',', '.'), currency).also { require(it.minorUnits > 0) }
    }
    fun movement(draft: ReceiptDraft, workspace: MoneyWorkspaceData, requestId: String, path: String): MoneyMovement {
        val kind = requireNotNull(draft.kind)
        val account = workspace.ledger.snapshot.accounts.single { it.account.id == draft.accountId }.account
        require(!account.archived && account.initialBalance.currencyCode == draft.currency)
        val amount = amount(draft.amount, draft.currency)
        require(amount.minorUnits > 0)
        val pocket = draft.pocketId?.let { id -> workspace.ledger.snapshot.pockets.single { it.pocket.id == id }.pocket }
        require(pocket == null || pocket.accountId == account.id)
        val direction = if (kind == ReceiptEntryKind.INCOME) MoneyDirection.INCOME else MoneyDirection.EXPENSE
        if (kind != ReceiptEntryKind.TRANSFER) require(workspace.categories.any { it.id == draft.categoryId && it.isIncome == (direction == MoneyDirection.INCOME) })
        return MoneyMovement(accountId = account.id, amount = amount, direction = direction, date = LocalDate.parse(draft.date),
            spaceId = pocket?.spaceId ?: account.spaceId, categoryId = draft.categoryId, pocketId = pocket?.id,
            note = draft.note.take(2000), attachmentPath = path, entryRequestId = requestId)
    }
}
