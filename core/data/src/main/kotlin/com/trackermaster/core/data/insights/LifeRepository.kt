package com.trackermaster.core.data.insights

import androidx.room.withTransaction
import com.trackermaster.core.data.mapper.*
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.database.*
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.habits.HabitScheduleVersion
import com.trackermaster.core.domain.money.Money
import com.trackermaster.core.domain.money.BudgetRules
import com.trackermaster.core.data.money.moneyBudget
import com.trackermaster.core.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.*

data class LifeMetric(val key: String,val title: String,val value: Double,val unit: String="",val detail: String="",val previous: Double=0.0)
data class LifeReport(val comparison: PeriodComparison,val metrics: List<LifeMetric>,val habitDaily: Map<LocalDate,Double>,
    val focusDaily: Map<LocalDate,Double>,val difficulties: List<Pair<String,Double>>,val goals: List<Pair<LifeGoal,Double>>) {
    fun calculatedText() = buildString {
        appendLine("${comparison.current.start} — ${comparison.current.end}")
        appendLine("Comparación: ${comparison.previous.start} — ${comparison.previous.end}")
        metrics.forEach { appendLine("${it.title}: ${"%.2f".format(it.value)} ${it.unit} · anterior ${"%.2f".format(it.previous)}. ${it.detail}") }
    }
}
data class LifeChoices(val habits: List<Habit> = emptyList(),val tasks: List<Task> = emptyList(),val savings: List<Pair<String,String>> = emptyList())
internal data class ActivityData(val habits: List<Habit>,val logs: List<HabitLog>,val history: List<HabitScheduleVersion>,val tasks: List<Task>,val journal: List<JournalEntry>)
internal data class FinanceData(val accounts: List<AccountEntity>,val transactions: List<TransactionEntity>,val budgets: List<BudgetEntity>,val savings: List<SavingsGoalEntity>)
internal data class FocusData(val sessions: List<FocusSessionEntity>,val contributions: List<FocusHabitContributionEntity>)
internal data class GoalData(val goals: List<LifeGoal>,val versions: List<LifeGoalVersionEntity>)
internal data class LifeData(val activity: ActivityData,val finance: FinanceData,val focus: FocusData,val goalData: GoalData,val today: LocalDate,val locale: java.util.Locale=java.util.Locale.getDefault()) {
    val goals get()=goalData.goals
}

@Singleton
class LifeRepository @Inject constructor(private val db: TrackermasterDatabase,private val journal: com.trackermaster.core.data.repository.JournalRepository,private val clock: ProgressClock,private val settings: SettingsRepository?=null) {
    fun goals() = db.lifeDao().goals().map { list -> list.map { LifeJson.goal(it.payload) } }
    fun reviews() = db.lifeDao().reviews().map { rows -> rows.map { ReviewRecord(it.id,it.period,LocalDate.ofEpochDay(it.startDay),LocalDate.ofEpochDay(it.endDay),it.provisional,it.createdAt,it.fingerprint,it.calculated,it.analysis) } }
    internal fun data(): Flow<LifeData> {
        val activities=combine(db.habitDao().observeAll(),db.progressDao().observeAllLogs(),db.progressDao().observeHistory(),db.taskDao().observeTasks(),journal.observeEntries()) { h,l,v,t,j ->
            ActivityData(h.map { it.toDomain() },l.map { it.toDomain() },v.map { it.toDomain() },t.map { it.toDomain() },j)
        }
        val finance=combine(db.expenseDao().observeAccounts(),db.expenseDao().observeTransactions(),db.expenseDao().observeBudgets(),db.lifeDao().savings()) { a,t,b,s -> FinanceData(a,t,b,s) }
        val focus=combine(db.focusDao().observeSessions(),db.lifeDao().focusContributions(),::FocusData)
        val goalData=combine(goals(),db.lifeDao().goalVersions(),::GoalData)
        val base=combine(activities,finance,focus,goalData,clock.days()) { a,f,s,g,t -> LifeData(a,f,s,g,t) }
        val locales=settings?.settings?.map { java.util.Locale.forLanguageTag(it.localeTag) } ?: flowOf(java.util.Locale.getDefault())
        return combine(base,locales) { d,locale -> d.copy(locale=locale) }.flowOn(Dispatchers.Default)
    }
    fun choices() = data().map { LifeChoices(it.activity.habits.filterNot { h -> h.archived },it.activity.tasks,it.finance.savings.map { g -> g.id to "${g.name} (${g.currency})" }) }
    fun goalProgress() = data().map { d -> d.goals.associate { it.id to goalValue(it,d) } }.flowOn(Dispatchers.Default)
    fun report(period: LifePeriod,anchor: LocalDate): Flow<LifeReport> = data().map { report(it,period,anchor) }.flowOn(Dispatchers.Default)
    fun daySummary(day: LocalDate): Flow<List<LifeMetric>> = data().map { metrics(it,DateSpan(day,day)) }.flowOn(Dispatchers.Default)
    suspend fun save(goal: LifeGoal) = db.withTransaction {
        val all=goals().first();GoalRules.validate(goal,all)
        all.filter { it.parentId==goal.id }.forEach { child -> GoalRules.validate(child,all.filterNot { it.id==goal.id }+goal) }
        if(goal.source==GoalSource.SAVINGS) require(goal.linkedIds.size==1) { "Vincula una sola meta financiera para conservar su moneda" }
        if(goal.source==GoalSource.CHILDREN) require(goal.target==100.0) { "El promedio de hijos utiliza una meta de 100 %" }
        val unit=when(goal.source) {
            GoalSource.HABIT -> "días equivalentes"
            GoalSource.TASK -> "tareas"
            GoalSource.FOCUS -> "min"
            GoalSource.CHILDREN -> "%"
            GoalSource.SAVINGS -> requireNotNull(db.lifeDao().savings().first().firstOrNull { it.id in goal.linkedIds }) { "Meta financiera no disponible" }.currency
            GoalSource.MANUAL -> goal.unit
        }
        val normalized=goal.copy(unit=unit)
        db.lifeDao().saveGoal(LifeGoalEntity(goal.id,LifeJson.goal(normalized)))
        db.lifeDao().saveGoalVersion(LifeGoalVersionEntity(goalId=goal.id,savedAt=clock.wallMs(),payload=LifeJson.goal(normalized)))
    }
    suspend fun delete(id: String) = db.withTransaction {
        val all=goals().first()
        all.filter { it.parentId==id }.forEach { save(it.copy(parentId=null)) }
        all.firstOrNull { it.id==id }?.let { db.lifeDao().saveGoalVersion(LifeGoalVersionEntity(goalId=id,savedAt=clock.wallMs(),payload=LifeJson.goal(it.copy(status="DELETED")))) }
        db.lifeDao().deleteGoal(id)
    }
    internal fun focusDays(data: LifeData,span: DateSpan,ids: List<String> = emptyList()): Map<LocalDate,Long> {
        val result=mutableMapOf<LocalDate,Long>();val contributed=data.focus.contributions.groupBy { it.sessionId }
        for(session in data.focus.sessions) {
            if(ids.isNotEmpty() && session.habitId?.toString() !in ids && session.category !in ids) continue
            val rows=contributed[session.id]
            if(rows!=null) rows.forEach { row -> val date=LocalDate.ofEpochDay(row.dateEpochDay);if(date in span) result[date]=(result[date] ?: 0)+row.durationMs }
            else {
                val date=(session.completedAtEpoch ?: session.startedAtEpoch)?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() } ?: continue
                if(date in span) result[date]=(result[date] ?: 0)+session.actualDurationMs
            }
        }
        return result
    }
    internal fun goalValue(g: LifeGoal,d: LifeData,seen: Set<String> = emptySet(),span: DateSpan=g.span): Double {
        if(g.id in seen) return 0.0
        return when(g.source) {
            GoalSource.MANUAL -> g.manualProgress
            GoalSource.HABIT -> LifeCalculations.habits(span,d.activity.habits.filter { it.id.toString() in g.linkedIds },d.activity.logs,d.activity.history).equivalent
            GoalSource.TASK -> d.activity.tasks.count { t -> t.id.toString() in g.linkedIds && t.completed && t.completedAtEpoch?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() in span }==true }.toDouble()
            GoalSource.FOCUS -> focusDays(d,span,g.linkedIds).values.sum()/60_000.0
            GoalSource.SAVINGS -> {
                val saving=d.finance.savings.firstOrNull { it.id in g.linkedIds } ?: return 0.0
                val net=d.finance.transactions.filter { it.pocketId==saving.pocketId && LocalDate.ofEpochDay(it.dateEpochDay) in span }
                    .sumOf { if(it.type=="INCOME") it.amountMinor else -it.amountMinor }
                Money(net,saving.currency).decimal.toDouble().coerceAtLeast(0.0)
            }
            GoalSource.CHILDREN -> GoalRules.childrenProgress(d.goals.filter { it.parentId==g.id && it.status!="ARCHIVED" }.map { child ->
                val from=maxOf(span.start,child.start);val to=minOf(span.end,child.end)
                (if(from<=to) goalValue(child,d,seen+g.id,DateSpan(from,to)) else 0.0) to child.target
            })
        }
    }
    internal fun report(d: LifeData,period: LifePeriod,anchor: LocalDate): LifeReport {
        val comparison=LifePeriods.comparison(period,anchor,d.today)
        val now=metrics(d,comparison.current);val before=metrics(d,comparison.previous).associateBy { it.key }
        val habits=LifeCalculations.habits(comparison.current,d.activity.habits,d.activity.logs,d.activity.history)
        val historical=d.copy(goalData=d.goalData.copy(goals=goalsAt(d,comparison.current.end)))
        val linked=historical.goals.filter { it.start<=comparison.current.end && it.end>=comparison.current.start && it.status!="ARCHIVED" }.map { it to goalValue(it,historical,span=DateSpan(it.start,minOf(it.end,comparison.current.end))) }
        return LifeReport(comparison,now.map { it.copy(previous=before[it.key]?.value ?: 0.0) },habits.daily,focusDays(d,comparison.current).mapValues { it.value/60_000.0 },
            habits.difficulty.map { (id,value) -> (d.activity.habits.firstOrNull { it.id==id }?.name ?: "Hábito eliminado") to value },linked)
    }
    private fun goalsAt(d: LifeData,day: LocalDate): List<LifeGoal> {
        val end=day.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        return d.goalData.versions.filter { it.savedAt<end }.groupBy { it.goalId }.values.map { versions -> LifeJson.goal(versions.maxWith(compareBy<LifeGoalVersionEntity> { it.savedAt }.thenBy { it.id }).payload) }.filter { it.status!="DELETED" }
    }
    private fun metrics(d: LifeData,span: DateSpan): List<LifeMetric> = buildList {
        val h=LifeCalculations.habits(span,d.activity.habits,d.activity.logs,d.activity.history)
        add(LifeMetric("habits","Habits · cumplimiento",h.percent ?: 0.0,"%",if(h.due==0) "Sin fechas programadas registradas" else "${h.due} fechas programadas; ${"%.2f".format(h.equivalent)} días equivalentes"))
        val t=LifeCalculations.tasks(span,d.activity.tasks,d.today,clock.zone())
        add(LifeMetric("tasks_done","Tasks · completadas",t.completed.toDouble()))
        add(LifeMetric("tasks_pending","Tasks · pendientes",t.pending.toDouble()))
        add(LifeMetric("tasks_overdue","Tasks · vencidas",t.overdue.toDouble()))
        add(LifeMetric("tasks_ontime","Tasks · a tiempo",if(t.due==0) 0.0 else t.onTime*100.0/t.due,"%","${t.onTime}/${t.due} con vencimiento en el periodo; ${t.unknownCompletion} sin fecha de finalización"))
        val transactions=d.finance.transactions.filter { LocalDate.ofEpochDay(it.dateEpochDay) in span && it.transferId==null && !it.principal && !it.investmentCapital }
        for(currency in d.finance.accounts.map { it.currencyCode }.distinct().sorted()) {
            val accounts=d.finance.accounts.filter { it.currencyCode==currency }.map { it.id }.toSet()
            val rows=transactions.filter { it.accountId in accounts }
            val income=rows.filter { it.type=="INCOME" }.sumOf { it.amountMinor };val expense=rows.filter { it.type=="EXPENSE" }.sumOf { it.amountMinor }
            add(LifeMetric("income_$currency","Money · ingresos",Money(income,currency).decimal.toDouble(),currency))
            add(LifeMetric("expense_$currency","Money · gastos",Money(expense,currency).decimal.toDouble(),currency))
            add(LifeMetric("balance_$currency","Money · balance",Money(income-expense,currency).decimal.toDouble(),currency,"Ingresos menos gastos; transferencias y capital de inversión excluidos"))
        }
        for(budget in d.finance.budgets) {
            val currency=budget.currencyCode ?: continue
            val limit=budget.limitAmountMinor ?: continue
            if(limit<=0) continue
            val definition=budget.moneyBudget()
            val custom=budget.period=="CUSTOM"
            val start=if(custom) budget.startDateEpochDay?.let(LocalDate::ofEpochDay) ?: continue else span.start
            val end=if(custom) budget.endDateEpochDay?.let(LocalDate::ofEpochDay) ?: continue else span.end
            if(start>span.end || end<span.start) continue
            var allowance=0.0;var date=maxOf(start,span.start)
            while(date<=minOf(end,span.end)) {
                val cycle=runCatching { BudgetRules.dates(definition,date,d.locale) }.getOrNull() ?: break
                allowance+=limit.toDouble()/DateSpan(cycle.first,cycle.second).days
                date=date.plusDays(1)
            }
            val used=transactions.filter { (budget.categoryId==0L || it.categoryId==budget.categoryId) && it.type=="EXPENSE" &&
                (budget.spaceId==null || it.spaceId==budget.spaceId) && d.finance.accounts.firstOrNull { a -> a.id==it.accountId }?.currencyCode==currency &&
                LocalDate.ofEpochDay(it.dateEpochDay) in DateSpan(start,end) }.sumOf { it.amountMinor }
            add(LifeMetric("budget_${budget.id}","Money · presupuesto #${budget.id}",if(allowance>0) used*100.0/allowance else 0.0,"% consumido","${Money(used,currency).decimal} $currency; límite proporcional a los días consultados: ${Money(allowance.toLong(),currency).decimal}; ${if(used<=allowance) "dentro del límite" else "excedido"}"))
        }
        if(d.finance.budgets.any { it.currencyCode==null || it.limitAmountMinor==null }) add(LifeMetric("budget_unknown","Money · presupuestos sin moneda definida",d.finance.budgets.count { it.currencyCode==null || it.limitAmountMinor==null }.toDouble(),detail="Consulta Money para asignar moneda; no se mezclan divisas"))
        val focus=focusDays(d,span)
        add(LifeMetric("focus","Focus · minutos efectivos",focus.values.sum()/60_000.0,"min","Incluye sesiones parciales y sin hábito; excluye pausas"))
        val sessionIds=d.focus.contributions.filter { LocalDate.ofEpochDay(it.dateEpochDay) in span && it.durationMs>0 }.map { it.sessionId }.toSet()
        val sessions=d.focus.sessions.filter { s -> s.id in sessionIds || (d.focus.contributions.none { it.sessionId==s.id } && s.actualDurationMs>0 && (s.completedAtEpoch ?: s.startedAtEpoch)?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() in span }==true) }
        add(LifeMetric("sessions","Focus · sesiones",sessions.size.toDouble()))
        for(category in FocusCategoryType.entries) {
            val minutes=focusDays(d,span,listOf(category.name)).values.sum()/60_000.0
            if(minutes>0) add(LifeMetric("focus_${category.name}","Focus · ${category.name}",minutes,"min"))
        }
        val j=d.activity.journal.filter { it.contentDate in span }
        val days=j.map { it.contentDate }.distinct().size
        add(LifeMetric("journal","Journal · días con entradas",days.toDouble(),"días"))
        add(LifeMetric("frequency","Journal · frecuencia",days*100.0/span.days,"% de días"))
        add(LifeMetric("dreams","Journal · sueños",j.count { it.kind=="DREAM" }.toDouble()))
        val asOf=goalsAt(d,span.end)
        val historical=d.copy(goalData=d.goalData.copy(goals=asOf))
        val goals=asOf.filter { it.status!="ARCHIVED" && it.start<=span.end && it.end>=span.start }
        val values=goals.map { g ->
            val clipped=DateSpan(maxOf(g.start,span.start),minOf(g.end,span.end))
            g to goalValue(g,historical,span=clipped)
        }
        add(LifeMetric("goals","Objetivos · progreso",if(values.isEmpty()) 0.0 else values.map { GoalRules.ratio(it.second,it.first.target) }.average()*100,"%","Promedio normalizado; progresos manuales según la última versión guardada hasta el cierre consultado"))
        add(LifeMetric("goals_done","Objetivos · completados",goals.count { it.status=="COMPLETED" && it.completedOn?.let { date -> date in span }==true }.toDouble()))
        add(LifeMetric("goals_pending","Objetivos · pendientes",goals.count { it.status!="COMPLETED" && it.end>=d.today }.toDouble()))
        add(LifeMetric("goals_overdue","Objetivos · vencidos",goals.count { it.status!="COMPLETED" && it.end<d.today }.toDouble()))
    }
}

data class ReviewRecord(val id: String,val period: String,val start: LocalDate,val end: LocalDate,val provisional: Boolean,val createdAt: Long,val fingerprint: String,val calculated: String,val analysis: String)
