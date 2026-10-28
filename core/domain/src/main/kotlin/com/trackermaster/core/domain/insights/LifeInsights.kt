package com.trackermaster.core.domain.insights

import com.trackermaster.core.domain.habits.*
import com.trackermaster.core.domain.model.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class LifePeriod(val label: String) {
    LAST_SEVEN("Últimos siete días"), WEEK("Semana"), MONTH("Mes"), QUARTER("Trimestre"), HALF("Semestre"), YEAR("Año")
}
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    init { require(end >= start) }
    operator fun contains(date: LocalDate) = date >= start && date <= end
    val days get() = ChronoUnit.DAYS.between(start,end)+1
}
data class PeriodComparison(val current: DateSpan, val previous: DateSpan, val calendarEnd: LocalDate, val provisional: Boolean = current.end < calendarEnd)
object LifePeriods {
    fun bounds(period: LifePeriod, anchor: LocalDate): DateSpan {
        val start = when(period) {
            LifePeriod.LAST_SEVEN -> anchor.minusDays(6)
            LifePeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            LifePeriod.MONTH -> anchor.withDayOfMonth(1)
            LifePeriod.QUARTER -> anchor.withMonth(((anchor.monthValue-1)/3)*3+1).withDayOfMonth(1)
            LifePeriod.HALF -> anchor.withMonth(if(anchor.monthValue<=6) 1 else 7).withDayOfMonth(1)
            LifePeriod.YEAR -> anchor.withDayOfYear(1)
        }
        val end = when(period) {
            LifePeriod.LAST_SEVEN -> anchor
            LifePeriod.WEEK -> start.plusDays(6)
            else -> start.plusMonths(months(period)).minusDays(1)
        }
        return DateSpan(start,end)
    }
    fun months(period: LifePeriod): Long = when(period) {
        LifePeriod.MONTH -> 1; LifePeriod.QUARTER -> 3; LifePeriod.HALF -> 6; LifePeriod.YEAR -> 12; else -> 0
    }
    fun comparison(period: LifePeriod, anchor: LocalDate, today: LocalDate): PeriodComparison {
        val full = bounds(period,anchor)
        val end = minOf(full.end,today).coerceAtLeast(full.start)
        val previous = when(period) {
            LifePeriod.LAST_SEVEN, LifePeriod.WEEK -> DateSpan(full.start.minusDays(7),full.start.minusDays(1))
            else -> bounds(period,full.start.minusDays(1))
        }
        val previousEnd = if(end < full.end) minOf(previous.end,previous.start.plusDays(ChronoUnit.DAYS.between(full.start,end))) else previous.end
        return PeriodComparison(DateSpan(full.start,end),DateSpan(previous.start,previousEnd),full.end,full.end>=today)
    }
}

enum class GoalSource(val label: String) { MANUAL("Manual"), HABIT("Hábito: días equivalentes"), TASK("Tareas completadas"), FOCUS("Focus: minutos efectivos"), SAVINGS("Meta financiera: aportes netos"), CHILDREN("Promedio de objetivos hijos") }
data class LifeGoal(
    val id: String = java.util.UUID.randomUUID().toString(), val title: String = "", val description: String = "",
    val period: LifePeriod = LifePeriod.WEEK, val start: LocalDate = LocalDate.now(), val end: LocalDate = LocalDate.now().plusDays(6),
    val status: String = "ACTIVE", val criterion: String = "", val target: Double = 1.0, val unit: String = "",
    val manualProgress: Double = 0.0, val source: GoalSource = GoalSource.MANUAL, val linkedIds: List<String> = emptyList(),
    val steps: List<SubTask> = emptyList(), val notes: String = "", val parentId: String? = null,
    val completedOn: LocalDate? = null,
) {
    val span get() = DateSpan(start,end)
}
object GoalRules {
    fun validate(goal: LifeGoal, all: List<LifeGoal>) {
        require(goal.title.isNotBlank()) { "Escribe un título" }
        require(goal.end>=goal.start && goal.target.isFinite() && goal.target>0 && goal.manualProgress.isFinite() && goal.manualProgress>=0) { "Revisa fechas y meta" }
        require(goal.period!=LifePeriod.LAST_SEVEN) { "Selecciona un periodo de calendario" }
        require(goal.source in listOf(GoalSource.MANUAL,GoalSource.CHILDREN) || goal.linkedIds.isNotEmpty()) { "Selecciona un vínculo" }
        var parent=goal.parentId
        val seen=mutableSetOf(goal.id)
        while(parent!=null) {
            require(seen.add(parent)) { "La jerarquía no puede tener ciclos" }
            val p=all.firstOrNull { it.id==parent } ?: error("Objetivo superior no disponible")
            require(p.start<=goal.start && p.end>=goal.end && p.period.ordinal>goal.period.ordinal) { "El objetivo superior debe abarcar las fechas y tener un periodo mayor" }
            parent=p.parentId
        }
    }
    fun ratio(value: Double,target: Double) = if(target>0) (value/target).coerceIn(0.0,1.0) else 0.0
    /** Each child contributes the same weight after normalization; child state never changes parent state. */
    fun childrenProgress(children: List<Pair<Double,Double>>) = if(children.isEmpty()) 0.0 else children.map { ratio(it.first,it.second) }.average()*100
}

data class HabitStatistics(val due: Int, val equivalent: Double, val daily: Map<LocalDate,Double>, val difficulty: List<Pair<Long,Double>>) {
    val percent get() = if(due==0) null else equivalent*100/due
}
data class TaskStatistics(val completed: Int, val pending: Int, val overdue: Int, val due: Int, val onTime: Int, val unknownCompletion: Int)
object LifeCalculations {
    fun habits(span: DateSpan,habits: List<Habit>,logs: List<HabitLog>,history: List<HabitScheduleVersion>): HabitStatistics {
        val indexed=logs.groupBy { it.habitId to it.date }.mapValues { it.value.maxBy { log -> log.id } }
        var due=0; var done=0.0
        val daily=linkedMapOf<LocalDate,Double>(); val difficulty=mutableListOf<Pair<Long,Double>>()
        for(habit in habits) {
            val first=if(habit.createdEpochDay==0L && !habit.creationDateKnown) logs.filter { it.habitId==habit.id }.minOfOrNull { it.date } ?: span.end.plusDays(1) else LocalDate.ofEpochDay(habit.createdEpochDay)
            var date=maxOf(first,span.start); var count=0;var sum=0.0
            while(date<=span.end) {
                val v=HabitProgressRules.versionOn(habit,date,history)
                if(HabitProgressRules.isDue(habit,date,v)) {
                    val p=HabitProgressRules.progress(indexed[habit.id to date],v)
                    count++;sum+=p;daily[date]=(daily[date] ?: 0.0)+p
                }
                date=date.plusDays(1)
            }
            due+=count;done+=sum
            if(count>0) difficulty+=habit.id to (sum/count)
        }
        return HabitStatistics(due,done,daily,difficulty.sortedBy { it.second }.take(5))
    }
    fun tasks(span: DateSpan,tasks: List<Task>,today: LocalDate,zone: ZoneId): TaskStatistics {
        val active=tasks.filter { it.cancelledAtEpoch==null }
        val due=active.filter { it.plannedDate?.let { d -> runCatching { LocalDate.parse(d) in span }.getOrDefault(false) }==true }
        val inPeriod=active.filter { it.createdAt.toLocalDate() in span || it in due }
        val completed=active.count { it.completedAtEpoch?.let { ms -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() in span }==true }
        val pending=inPeriod.count { !it.completed }
        val overdue=due.count { !it.completed && LocalDate.parse(it.plannedDate)<today }
        val onTime=due.count { task -> task.completed && task.completedAtEpoch?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate()<=LocalDate.parse(task.plannedDate) }==true }
        return TaskStatistics(completed,pending,overdue,due.size,onTime,due.count { it.completed && it.completedAtEpoch==null })
    }
    fun variation(current: Double,previous: Double): String = when {
        previous==0.0 && current==0.0 -> "Sin cambio (base 0)"
        previous==0.0 -> "Sin base para variación porcentual"
        else -> "%+.1f %%".format(java.util.Locale.ROOT,(current-previous)/kotlin.math.abs(previous)*100)
    }
}
