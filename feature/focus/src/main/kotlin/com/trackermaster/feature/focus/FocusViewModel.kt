package com.trackermaster.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.data.repository.*
import com.trackermaster.core.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val runtime: FocusRuntimeState = FocusRuntimeState(),
    val alarm: FocusAlarmState = FocusAlarmState(),
    val habits: List<Habit> = emptyList(),
    val todayHabitDurations: Map<Long, Long> = emptyMap(),
    val sessions: List<FocusSession> = emptyList(),
    val category: FocusCategoryType = FocusCategoryType.WORK,
    val selectedHabitId: Long? = null,
    val minutes: String = "25",
    val todaySeconds: Long = 0,
    val score: Int = 0,
    val error: Boolean = false,
    val loading: Boolean = true,
)

sealed interface FocusAction {
    data class HabitSelected(val id: Long?): FocusAction
    data class Duration(val minutes: String): FocusAction
    data class Category(val category: FocusCategoryType): FocusAction
    data object Start: FocusAction
    data object Pause: FocusAction
    data object Resume: FocusAction
    data object Stop: FocusAction
    data object DismissAlarm: FocusAction
    data object ClearAlarmFallback: FocusAction
    data class SaveAlarmSettings(val uri: String?, val vibrate: Boolean): FocusAction
    data object DismissError: FocusAction
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FocusViewModel @Inject constructor(runtime: FocusProgressRepository, habits: HabitRepository,
    sessions: FocusRepository, private val controller: FocusTimerController, private val saved: SavedStateHandle,
    settings: com.trackermaster.core.data.settings.SettingsRepository, private val alarmStore: FocusAlarmStore): ViewModel() {
    private val restoredDuration=saved.contains("focus_minutes")
    private val category=saved.getStateFlow("focus_category",FocusCategoryType.WORK.name)
    private val habit=saved.getStateFlow<Long?>("focus_habit",null)
    private val minutes=saved.getStateFlow("focus_minutes","25")
    private val error=MutableStateFlow(false)
    private val draft=combine(category,habit,minutes,error) { c,h,m,e -> FocusUiState(category=FocusCategoryType.valueOf(c),selectedHabitId=h,minutes=m,error=e) }
    private val history=combine(sessions.observeSessions(),sessions.weeklyFocusScore(),runtime.observeTodaySeconds()) { entries,score,time ->
        Triple(entries,score,time)
    }
    private val habitOptions=habits.observeDay().flatMapLatest { day -> habits.observeActiveHabitsWithLogs(day).map { rows ->
        val options = rows.map { (habit, logs) ->
            val log = logs.filter { it.date == day }.maxByOrNull { it.id }
            habit.copy(targetDurationMs=log?.targetDurationMsAtLog ?: habit.targetDurationMs,
                targetCount=log?.targetCountAtLog ?: habit.targetCount,progressType=log?.progressTypeAtLog ?: habit.progressType)
        }
        val durations = rows.associate { (habit, logs) ->
            habit.id to (logs.filter { it.date == day }.maxByOrNull { it.id }?.durationMs?.coerceAtLeast(0) ?: 0L)
        }
        options to durations
    } }
    private val runtimeWithAlarm = combine(runtime.observeRuntime(), alarmStore.state) { runtimeState, alarm -> runtimeState to alarm }
    val uiState=combine(runtimeWithAlarm,habitOptions,draft,history,FocusTimerSignals.error) { r,h,d,s,e ->
        d.copy(runtime=r.first,alarm=r.second,habits=h.first,todayHabitDurations=h.second,sessions=s.first,score=s.second,todaySeconds=s.third,error=d.error || e,loading=false)
    }.catch { emit(FocusUiState(error=true,loading=false)) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),FocusUiState())

    init { if(!restoredDuration) viewModelScope.launch {
        val defaultMinutes = settings.settings.first().pomodoroWorkMinutes.coerceIn(1,720).toString()
        if (!saved.contains("focus_minutes")) saved["focus_minutes"] = defaultMinutes
    } }
    fun onAction(action: FocusAction) {
        when(action) {
            is FocusAction.HabitSelected -> if (!uiState.value.runtime.active) {
                saved["focus_habit"]=action.id
                val selected = uiState.value.habits.firstOrNull { it.id==action.id }
                if (selected != null && selected.progressType in listOf(ProgressType.TIMER, ProgressType.STOPWATCH)) {
                    val done = uiState.value.todayHabitDurations[selected.id] ?: 0L
                    suggestedFocusMinutes(selected.targetDurationMs, done)?.let { saved["focus_minutes"] = it.toString() }
                }
            }
            is FocusAction.Duration -> if (!uiState.value.runtime.active && action.minutes.length<=3 && action.minutes.all(Char::isDigit)) saved["focus_minutes"]=action.minutes
            is FocusAction.Category -> if (!uiState.value.runtime.active) saved["focus_category"]=action.category.name
            FocusAction.DismissError -> { error.value=false;FocusTimerSignals.error.value=false }
            FocusAction.ClearAlarmFallback -> viewModelScope.launch { alarmStore.clearFallback() }
            is FocusAction.SaveAlarmSettings -> viewModelScope.launch { alarmStore.saveSelection(action.uri, action.vibrate) }
            else -> viewModelScope.launch {
                try {
                    val value=uiState.value
                    when(action) {
                        FocusAction.Start -> minutes.value.toIntOrNull()?.takeIf { it in 1..720 }?.let {
                            if (!value.runtime.active && value.alarm.activeSessionId == 0L && !value.loading) controller.send(FocusTimerController.START,value.category.name,it,value.selectedHabitId)
                        }
                        FocusAction.Pause -> controller.send(FocusTimerController.PAUSE)
                        FocusAction.Resume -> controller.send(FocusTimerController.RESUME)
                        FocusAction.Stop -> controller.send(FocusTimerController.STOP)
                        FocusAction.DismissAlarm -> controller.send(FocusTimerController.DISMISS_ALARM)
                        else -> Unit
                    }
                } catch (_: Exception) { error.value=true }
            }
        }
    }
}

internal fun suggestedFocusMinutes(targetMs: Long, completedMs: Long): Int? {
    val remaining = (targetMs.coerceAtLeast(0) - completedMs.coerceAtLeast(0)).coerceAtLeast(0)
    if (remaining == 0L) return null
    return (1 + (remaining - 1) / 60_000).coerceIn(1, 720).toInt()
}
