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
    val habits: List<Habit> = emptyList(),
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
    data object DismissError: FocusAction
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FocusViewModel @Inject constructor(runtime: FocusProgressRepository, habits: HabitRepository,
    sessions: FocusRepository, private val controller: FocusTimerController, private val saved: SavedStateHandle,
    settings: com.trackermaster.core.data.settings.SettingsRepository): ViewModel() {
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
        rows.map { (habit,logs) ->
            val log=logs.filter { it.date==day }.maxByOrNull { it.id }
            habit.copy(targetDurationMs=log?.targetDurationMsAtLog ?: habit.targetDurationMs,
                targetCount=log?.targetCountAtLog ?: habit.targetCount,progressType=log?.progressTypeAtLog ?: habit.progressType)
        }
    } }
    val uiState=combine(runtime.observeRuntime(),habitOptions,draft,history,FocusTimerSignals.error) { r,h,d,s,e ->
        d.copy(runtime=r,habits=h,sessions=s.first,score=s.second,todaySeconds=s.third,error=d.error || e,loading=false)
    }.catch { emit(FocusUiState(error=true,loading=false)) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),FocusUiState())

    init { if(!restoredDuration) viewModelScope.launch {
        saved["focus_minutes"]=settings.settings.first().pomodoroWorkMinutes.coerceIn(1,720).toString()
    } }
    fun onAction(action: FocusAction) {
        when(action) {
            is FocusAction.HabitSelected -> if (!uiState.value.runtime.active) {
                saved["focus_habit"]=action.id
                uiState.value.habits.firstOrNull { it.id==action.id }?.targetDurationMs?.takeIf { it>0 }?.let {
                    saved["focus_minutes"]=((it+59_999)/60_000).coerceIn(1,720).toString()
                }
            }
            is FocusAction.Duration -> if (!uiState.value.runtime.active && action.minutes.length<=3 && action.minutes.all(Char::isDigit)) saved["focus_minutes"]=action.minutes
            is FocusAction.Category -> if (!uiState.value.runtime.active) saved["focus_category"]=action.category.name
            FocusAction.DismissError -> { error.value=false;FocusTimerSignals.error.value=false }
            else -> viewModelScope.launch {
                try {
                    val value=uiState.value
                    when(action) {
                        FocusAction.Start -> value.minutes.toIntOrNull()?.takeIf { it in 1..720 }?.let {
                            if (!value.runtime.active && !value.loading) controller.send(FocusTimerController.START,value.category.name,it,value.selectedHabitId)
                        }
                        FocusAction.Pause -> controller.send(FocusTimerController.PAUSE)
                        FocusAction.Resume -> controller.send(FocusTimerController.RESUME)
                        FocusAction.Stop -> controller.send(FocusTimerController.STOP)
                        else -> Unit
                    }
                } catch (_: Exception) { error.value=true }
            }
        }
    }
}
