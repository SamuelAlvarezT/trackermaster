package com.trackermaster.feature.expense

import com.trackermaster.core.domain.money.DebtKind
import org.json.JSONObject

internal object CommitmentDrafts {
    fun encode(c: CommitmentControls): String = JSONObject().apply {
        put("tab",c.tab); put("currency",c.currency); put("space",c.space); put("archived",c.archived); put("detail",c.detail)
        c.draft?.let { f -> put("draft",JSONObject().apply {
            put("editor",f.editor.name);put("id",f.id);put("version",f.version);put("request",f.request);put("name",f.name)
            put("kind",f.kind.name);put("currency",f.currency);put("space",f.space);put("amount",f.amount);put("due",f.due);put("date",f.date)
            put("account",f.account);put("pocket",f.pocket);put("note",f.note);put("disburse",f.disburse);put("withdraw",f.withdraw);put("linked",f.linked);put("existing",f.existing)
        }) }
    }.toString()
    fun decode(value: String?): CommitmentControls = runCatching {
        val c=JSONObject(requireNotNull(value)); val f=c.optJSONObject("draft")
        CommitmentControls(tab=c.getString("tab"),currency=c.getString("currency"),space=c.long("space"),archived=c.optBoolean("archived"),detail=c.optString("detail").takeIf{it.isNotBlank()},draft=f?.let {
            CommitmentDraft(CommitmentEditor.valueOf(it.getString("editor")),it.getString("id"),it.getLong("version"),it.getString("request"),it.getString("name"),DebtKind.valueOf(it.getString("kind")),it.getString("currency"),it.getLong("space"),it.getString("amount"),it.getString("due"),it.getString("date"),it.long("account"),it.long("pocket"),it.getString("note"),it.getBoolean("disburse"),it.getBoolean("withdraw"),it.long("linked"),it.getBoolean("existing"))
        })
    }.getOrDefault(CommitmentControls())
    private fun JSONObject.long(key: String): Long? = if(isNull(key)) null else getLong(key)
}
