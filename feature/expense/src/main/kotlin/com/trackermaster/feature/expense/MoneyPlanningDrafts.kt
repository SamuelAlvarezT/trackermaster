package com.trackermaster.feature.expense

import com.trackermaster.core.domain.money.*
import org.json.JSONObject

/** A process-safe editor draft, independent of Room's committed financial state. */
internal object PlanningDrafts {
    private fun JSONObject.long(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    fun encode(c: PlanningControls): String = JSONObject().apply {
        put("tab",c.tab);put("currency",c.currency);put("space",c.space)
        c.budget?.let { b -> put("budget",JSONObject().apply {
            put("id",b.id);put("amount",b.amount);put("currency",b.currency);put("category",b.category);put("space",b.space)
            put("period",b.period);put("threshold",b.threshold);put("from",b.from);put("through",b.through)
        }) }
        c.plan?.let { p -> put("plan",JSONObject().apply {
            put("id",p.id);put("name",p.name);put("kind",p.kind.name);put("direction",p.direction.name);put("amount",p.amount)
            put("currency",p.currency);put("account",p.account);put("pocket",p.pocket);put("category",p.category);put("due",p.due)
            put("frequency",p.frequency.name);put("days",p.reminderDays);put("note",p.note);put("version",p.version)
            put("anchor",p.anchor);put("originalDue",p.originalDue);put("active",p.active);put("space",p.space)
        }) }
        c.settlement?.let { f -> put("settlement",JSONObject().apply {
            put("id",f.planId);put("due",f.due);put("version",f.version);put("name",f.name);put("currency",f.currency)
            put("direction",f.direction.name);put("amount",f.amount);put("account",f.account);put("pocket",f.pocket)
            put("category",f.category);put("date",f.date);put("note",f.note);put("linked",f.linked);put("space",f.space)
        }) }
    }.toString()
    fun decode(json: String?): PlanningControls = runCatching {
        val o=JSONObject(requireNotNull(json))
        val b=o.optJSONObject("budget")?.let { BudgetForm(it.getLong("id"),it.getString("amount"),it.getString("currency"),it.getLong("category"),
            it.long("space"),it.getString("period"),it.getString("threshold"),it.getString("from"),it.getString("through")) }
        val p=o.optJSONObject("plan")?.let { PlanForm(it.getString("id"),it.getString("name"),MoneyPlanKind.valueOf(it.getString("kind")),
            MoneyDirection.valueOf(it.getString("direction")),it.getString("amount"),it.getString("currency"),it.long("account"),it.long("pocket"),it.long("category"),
            it.getString("due"),MoneyFrequency.valueOf(it.getString("frequency")),it.getString("days"),it.getString("note"),it.getLong("version"),
            it.optString("anchor").takeIf { value -> value.isNotBlank() },it.optString("originalDue").takeIf { value -> value.isNotBlank() },it.getBoolean("active"),it.long("space")) }
        val f=o.optJSONObject("settlement")?.let { SettlementForm(it.getString("id"),it.getString("due"),it.getLong("version"),it.getString("name"),it.getString("currency"),
            MoneyDirection.valueOf(it.getString("direction")),it.getString("amount"),it.long("account"),it.long("pocket"),it.long("category"),it.getString("date"),it.getString("note"),it.long("linked"),it.long("space")) }
        PlanningControls(tab=o.getString("tab"),currency=o.getString("currency"),space=o.long("space"),budget=b,plan=p,settlement=f)
    }.getOrDefault(PlanningControls())
}
