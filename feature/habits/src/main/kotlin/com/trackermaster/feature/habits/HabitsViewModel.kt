package com.trackermaster.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.StreakInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitsUiState(
    val activeHabits: List<Pair<Habit, List<HabitLog>>> = emptyList(),
    val archivedHabits: List<Pair<Habit, List<HabitLog>>> = emptyList(),
    val selectedTab: Int = 0,
    val isReorderMode: Boolean = false
)

@HiltViewModel
class HabitsViewModel @Inject constructor(private val repo: HabitRepository) : ViewModel() {
    
    val allHabits = repo.observeActiveHabits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements = repo.observeAchievements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    private val _isReorderMode = MutableStateFlow(false)

    val uiState: StateFlow<HabitsUiState> = combine(
        repo.observeActiveHabitsWithLogs(LocalDate.now().minusDays(6)),
        repo.observeArchivedHabitsWithLogs(LocalDate.now().minusDays(6)),
        _selectedTab,
        _isReorderMode
    ) { active, archived, tab, reorderMode ->
        HabitsUiState(
            activeHabits = active,
            archivedHabits = archived,
            selectedTab = tab,
            isReorderMode = reorderMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
        _isReorderMode.value = false
    }

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun toggle(habit: Habit) = viewModelScope.launch { repo.toggleCheckbox(habit) }

    fun toggleDay(habit: Habit, date: LocalDate) = viewModelScope.launch {
        repo.toggleHabitDayState(habit, date)
    }

    fun saveHabit(habit: Habit) = viewModelScope.launch { repo.saveHabit(habit) }

    fun updateHabitsOrder(habits: List<Habit>) = viewModelScope.launch {
        repo.updateHabitsOrder(habits)
    }

    fun toggleArchive(habit: Habit) = viewModelScope.launch {
        repo.archiveHabit(habit.id, !habit.archived)
    }

    fun deleteHabit(habit: Habit) = viewModelScope.launch {
        repo.deleteHabit(habit)
    }

    fun observeHabitLogs(habitId: Long): Flow<List<HabitLog>> = repo.observeHabitLogs(habitId)

    fun observeStreak(habitId: Long): Flow<StreakInfo> = repo.streakFor(habitId)

    fun observeCompletionsCount(habitId: Long): Flow<Int> = repo.observeCompletionsCount(habitId)

    fun logCounter(habit: Habit, value: Int) = viewModelScope.launch {
        repo.logHabit(habit, HabitLog(habitId = habit.id, date = LocalDate.now(), value = value, completed = value >= habit.targetCount))
    }

    private val _heatmap = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val heatmap: StateFlow<Map<LocalDate, Int>> = _heatmap

    fun loadHeatmap(habitId: Long) = viewModelScope.launch {
        _heatmap.value = repo.getHeatmapData(habitId, LocalDate.now().year)
    }
}
