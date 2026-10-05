package com.trackermaster.core.data.money

import com.trackermaster.core.domain.money.*
import org.json.JSONObject

object ReceiptDraftCodec {
    fun encode(d: ReceiptDraft) = JSONObject().apply {
        put("kind", d.kind?.name); put("amount", d.amount); put("currency", d.currency); put("date", d.date)
        put("account", d.accountId); put("pocket", d.pocketId); put("category", d.categoryId)
        put("target", d.targetAccountId); put("targetPocket", d.targetPocketId); put("received", d.receivedAmount)
        put("note", d.note); put("reference", d.reference); put("confirmed", d.confirmed); put("duplicate", d.duplicateAcknowledgement)
    }.toString()
    fun decode(json: String): ReceiptDraft? = runCatching {
        val o = JSONObject(json)
        fun id(key: String) = if (o.has(key) && !o.isNull(key)) o.getLong(key) else null
        ReceiptDraft(kind = o.optString("kind").takeIf(String::isNotEmpty)?.let(ReceiptEntryKind::valueOf),
            amount = o.optString("amount"), currency = o.optString("currency", "COP"), date = o.optString("date"),
            accountId = id("account"), pocketId = id("pocket"), categoryId = id("category"), targetAccountId = id("target"),
            targetPocketId = id("targetPocket"), receivedAmount = o.optString("received"), note = o.optString("note"),
            reference = o.optString("reference"), confirmed = o.optBoolean("confirmed"),
            duplicateAcknowledgement = o.optString("duplicate").takeIf(String::isNotEmpty))
    }.getOrNull()
}
