package com.trackermaster.core.domain.money

import java.io.Writer
import java.time.LocalDate
import java.time.YearMonth

data class MoneyReportScope(val currency: String, val from: LocalDate, val through: LocalDate,
    val spaceId: Long? = null, val accountId: Long? = null, val categoryId: Long? = null,
    val direction: MoneyDirection? = null, val transfersOnly: Boolean = false, val query: String = "")
data class MoneyReportEntry(val movement: MoneyMovement, val account: String, val pocket: String?, val category: MoneyCategory?)
data class MoneyReportMonth(val month: YearMonth, val totals: MoneyPeriodTotals)
data class MoneyReport(val scope: MoneyReportScope, val entries: List<MoneyReportEntry>, val totals: MoneyPeriodTotals,
    val months: List<MoneyReportMonth>, val categories: Map<Long?, Money>)

object MoneyReportRules {
    fun build(data: MoneyWorkspaceData, scope: MoneyReportScope): MoneyReport {
        require(!scope.through.isBefore(scope.from))
        val accounts = data.ledger.snapshot.accounts.associate { it.account.id to it.account.name }
        val pockets = data.ledger.snapshot.pockets.associate { it.pocket.id to it.pocket.name }
        val categories = data.categories.associateBy { it.id }
        val entries = data.ledger.movements.asSequence().filter { m ->
            m.amount.currencyCode == scope.currency && !m.date.isBefore(scope.from) && !m.date.isAfter(scope.through) &&
                (scope.spaceId == null || m.spaceId == scope.spaceId) && (scope.accountId == null || m.accountId == scope.accountId) &&
                (scope.categoryId == null || m.categoryId == scope.categoryId) &&
                (!scope.transfersOnly || m.transferId != null) && (scope.direction == null || (m.transferId == null && m.direction == scope.direction))
        }.map { MoneyReportEntry(it,accounts.getValue(it.accountId),pockets[it.pocketId],categories[it.categoryId]) }
            .filter { row -> scope.query.isBlank() || listOfNotNull(row.account,row.pocket,row.movement.note,row.category?.name).any { it.contains(scope.query.trim(),true) } }
            .sortedWith(compareBy<MoneyReportEntry> { it.movement.date }.thenBy { it.movement.id }).toList()
        val movements = entries.map { it.movement }
        val totals = MoneyLedger.periodTotals(movements,scope.currency,scope.from,scope.through)
        val first = YearMonth.from(scope.from)
        val last = YearMonth.from(scope.through)
        require(java.time.temporal.ChronoUnit.MONTHS.between(first,last) <= 2400) { "Report range exceeds 200 years" }
        val grouped = movements.groupBy { YearMonth.from(it.date) }
        val months = generateSequence(first) { it.plusMonths(1).takeIf { next -> next <= last } }.map { month ->
            MoneyReportMonth(month,MoneyLedger.periodTotals(grouped[month].orEmpty(),scope.currency,month.atDay(1),month.atEndOfMonth()))
        }.toList()
        val spending = movements.filter { it.transferId == null && !it.principal && !it.investmentCapital && it.direction == MoneyDirection.EXPENSE }
            .groupBy { it.categoryId }.mapValues { (_, rows) -> rows.fold(Money(0,scope.currency)) { total,m -> total + m.amount } }
        return MoneyReport(scope,entries,totals,months,spending)
    }
}

/** Stable machine-readable schema: ISO dates, exact minor units, explicit currency and classification. */
object MoneyCsv {
    fun write(report: MoneyReport, writer: Writer) {
        writer.write("schema_version,id,date,currency,amount_minor,amount_decimal,direction,classification,account_id,account,pocket,category,space_id,note\r\n")
        report.entries.forEach { row ->
            val m = row.movement
            val type = if(m.transferId != null) "TRANSFER" else if(m.principal) "LOAN_CAPITAL" else if(m.investmentCapital) "INVESTMENT_CAPITAL" else if(m.investmentId != null) "INVESTMENT_RETURN" else "OPERATING"
            val fields = listOf("1",m.id.toString(),m.date.toString(),m.amount.currencyCode,m.amount.minorUnits.toString(),m.amount.decimal.toPlainString(),m.direction.name,type,
                m.accountId.toString(),safe(row.account),safe(row.pocket.orEmpty()),safe(row.category?.builtinKey ?: row.category?.name.orEmpty()),m.spaceId.toString(),safe(m.note))
            writer.write(fields.joinToString(",") { "\"${it.replace("\"","\"\"")}\"" } + "\r\n")
        }
    }
    private fun safe(value: String): String = if(value.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r','\n')) "'$value" else value
}
