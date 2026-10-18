package com.trackermaster.core.data.progress

import androidx.room.withTransaction
import com.trackermaster.core.data.mapper.*
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.focus.*
import com.trackermaster.core.domain.habits.HabitProgressRules
import com.trackermaster.core.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class FocusRuntimeState(
    val sessionId: Long = 0,
    val habitId: Long? = null,
    val category: FocusCategoryType = FocusCategoryType.WORK,
    val plannedMs: Long = 0,
    val effectiveMs: Long = 0,
    val status: FocusRunStatus = FocusRunStatus.STOPPED,
) {
    val active: Boolean get() = sessionId > 0 && status in listOf(FocusRunStatus.RUNNING,FocusRunStatus.PAUSED)
    val remainingSeconds: Int get() = ((plannedMs-effectiveMs).coerceAtLeast(0)+999).div(1000).toInt()
}

@Singleton
class FocusProgressRepository @Inject constructor(private val db: TrackermasterDatabase, private val clock: ProgressClock) {
    private val dao get() = db.progressDao()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeTodaySeconds(): Flow<Long> = clock.days().flatMapLatest { day ->
        dao.observeFocusTime(day.toEpochDay(),day.atStartOfDay(clock.zone()).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()).map { it/1000 }
    }
    fun observeRuntime(): Flow<FocusRuntimeState> = combine(dao.observeRuntime(),flow {
        while (true) { emit(clock.elapsedMs());delay(1000) }
    }) { value, now ->
        if (value==null) FocusRuntimeState() else {
            val status=FocusRunStatus.valueOf(value.status)
            val sameBoot=value.bootCount==clock.bootCount()
            FocusRuntimeState(value.sessionId,value.habitId,FocusCategoryType.valueOf(value.category),value.plannedMs,
                value.effectiveMs+FocusTimingRules.delta(value.effectiveMs,value.plannedMs,value.lastElapsedMs,now,sameBoot,status),
                if (!sameBoot && status==FocusRunStatus.RUNNING) FocusRunStatus.PAUSED else status)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun start(category: FocusCategoryType, minutes: Int, habitId: Long?): Long = db.withTransaction {
        require(minutes in 1..720)
        check(dao.runtime()?.status !in listOf("RUNNING","PAUSED")) { "An active focus session already exists" }
        val habit=habitId?.let { db.habitDao().getById(it)?.also { h -> require(!h.archived) } ?: error("Habit unavailable") }
        val wall=clock.wallMs()
        val id=db.focusDao().insert(FocusSessionEntity(category=category.name,plannedMinutes=minutes,
            startedAtEpoch=wall,habitId=habitId,habitName=habit?.name))
        dao.saveRuntime(FocusRuntimeEntity(sessionId=id,category=category.name,habitId=habitId,
            plannedMs=minutes*60_000L,effectiveMs=0,status="RUNNING",lastElapsedMs=clock.elapsedMs(),lastWallMs=wall,bootCount=clock.bootCount()))
        id
    }

    suspend fun checkpoint(): FocusRuntimeState = db.withTransaction { checkpointInTransaction(null) }
    suspend fun pause(): FocusRuntimeState = db.withTransaction { checkpointInTransaction(FocusRunStatus.PAUSED) }
    suspend fun stop(): FocusRuntimeState = db.withTransaction { checkpointInTransaction(FocusRunStatus.STOPPED) }
    suspend fun resume() = db.withTransaction {
        val value=dao.runtime() ?: return@withTransaction
        check(value.status=="PAUSED")
        dao.saveRuntime(value.copy(status="RUNNING",lastElapsedMs=clock.elapsedMs(),lastWallMs=clock.wallMs(),bootCount=clock.bootCount()))
    }

    private suspend fun checkpointInTransaction(requested: FocusRunStatus?): FocusRuntimeState {
        val value=dao.runtime() ?: return FocusRuntimeState()
        val oldStatus=FocusRunStatus.valueOf(value.status)
        if (oldStatus==FocusRunStatus.COMPLETED || oldStatus==FocusRunStatus.STOPPED) return value.state()
        val now=clock.elapsedMs()
        val wall=clock.wallMs()
        val sameBoot=value.bootCount==clock.bootCount()
        val delta=FocusTimingRules.delta(value.effectiveMs,value.plannedMs,value.lastElapsedMs,now,sameBoot,oldStatus)
        val total=value.effectiveMs+delta
        val next=when {
            total>=value.plannedMs -> FocusRunStatus.COMPLETED
            requested!=null -> requested
            !sameBoot -> FocusRunStatus.PAUSED
            else -> oldStatus
        }
        val session=dao.session(value.sessionId) ?: error("Focus session unavailable")
        db.focusDao().update(session.copy(actualSeconds=(total/1000).toInt(),actualDurationMs=total,
            completed=next==FocusRunStatus.COMPLETED,
            completedAtEpoch=if (next in listOf(FocusRunStatus.COMPLETED,FocusRunStatus.STOPPED)) wall else null))
        val habit=value.habitId?.let { db.habitDao().getById(it)?.takeUnless { h -> h.archived }?.toDomain() }
        if (delta>0) {
            val history=habit?.let { dao.history(it.id).map { row -> row.toDomain() } }.orEmpty()
            // If the timer finished while the process was absent, exclude all time after its planned end.
            val end=if (sameBoot && now-value.lastElapsedMs>delta) wall-(now-value.lastElapsedMs-delta) else wall
            for ((day,amount) in FocusTimingRules.splitDays(end,delta,clock.zone())) {
                val previous=dao.contribution(value.sessionId,day.toEpochDay())?.durationMs ?: 0
                dao.saveContribution(FocusHabitContributionEntity(value.sessionId,day.toEpochDay(),value.habitId,Math.addExact(previous,amount)))
                if(habit==null) continue
                val existing=db.habitLogDao().getForDate(habit.id,day.toEpochMilli())?.toDomain()
                val version=HabitProgressRules.versionOn(habit,day,history)
                val duration=Math.addExact(existing?.durationMs?.coerceAtLeast(0) ?: 0,amount)
                val base=existing ?: HabitLog(habitId=habit.id,date=day,progressTypeAtLog=version.progressType,
                    targetDurationMsAtLog=version.targetDurationMs,targetCountAtLog=version.targetCount,checklistSizeAtLog=version.checklistSize)
                val changed=base.copy(durationMs=duration)
                val type=base.progressTypeAtLog ?: version.progressType
                val timed=type==ProgressType.TIMER || type==ProgressType.STOPWATCH
                val autoComplete=(timed && HabitProgressRules.progress(changed.copy(completed=false),version)>=1.0) ||
                    (type==ProgressType.CHECKBOX && next==FocusRunStatus.COMPLETED && day==clock.today())
                val log=changed.copy(value=changed.value.coerceAtLeast(0),completed=base.completed || autoComplete,
                    completionSource=if (base.completed) base.completionSource else "focus")
                if (existing==null) db.habitLogDao().insert(log.toEntity()) else db.habitLogDao().update(log.toEntity())
            }
        }
        val saved=value.copy(effectiveMs=total,status=next.name,lastElapsedMs=now,lastWallMs=wall,bootCount=clock.bootCount())
        dao.saveRuntime(saved)
        return saved.state()
    }

    private fun FocusRuntimeEntity.state() = FocusRuntimeState(sessionId,habitId,FocusCategoryType.valueOf(category),plannedMs,effectiveMs,FocusRunStatus.valueOf(status))
}
