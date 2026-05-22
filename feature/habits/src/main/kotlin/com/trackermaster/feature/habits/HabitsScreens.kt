package com.trackermaster.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.ui.components.EmptyState
import com.trackermaster.core.ui.components.SectionHeader
import com.trackermaster.core.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HabitsViewModel @Inject constructor(private val repo: HabitRepository) : ViewModel() {
    val todayHabits = repo.observeTodayHabits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allHabits = repo.observeActiveHabits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements = repo.observeAchievements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggle(habit: Habit) = viewModelScope.launch { repo.toggleCheckbox(habit) }

    fun saveHabit(habit: Habit) = viewModelScope.launch { repo.saveHabit(habit) }

    fun logCounter(habit: Habit, value: Int) = viewModelScope.launch {
        repo.logHabit(habit, HabitLog(habitId = habit.id, date = LocalDate.now(), value = value, completed = value >= habit.targetCount))
    }

    private val _heatmap = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val heatmap: StateFlow<Map<LocalDate, Int>> = _heatmap

    fun loadHeatmap(habitId: Long) = viewModelScope.launch {
        _heatmap.value = repo.getHeatmapData(habitId, LocalDate.now().year)
    }
}

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel(), onAddHabit: () -> Unit = {}) {
    val today by viewModel.todayHabits.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { padding ->
        if (today.isEmpty() && !showEditor) {
            EmptyState("No habits due today. Tap + to create one.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SectionHeader("Today") }
                items(today, key = { it.first.id }) { (habit, log) ->
                    HabitRow(habit, log?.completed == true) { viewModel.toggle(habit) }
                }
            }
        }
    }
    if (showEditor) {
        HabitEditorDialog(onDismiss = { showEditor = false }) { habit ->
            viewModel.saveHabit(habit)
            showEditor = false
        }
    }
}

@Composable
fun HabitRow(habit: Habit, completed: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (completed) Color(habit.colorArgb) else MaterialTheme.colorScheme.outline,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Text("${habit.progressType.name} · ${habit.scheduleType.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun HabitEditorDialog(onDismiss: () -> Unit, onSave: (Habit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var progressType by remember { mutableStateOf(ProgressType.CHECKBOX) }
    var scheduleType by remember { mutableStateOf(ScheduleType.DAILY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Progress: ${progressType.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressType.entries.take(3).forEach { t ->
                        FilterChip(progressType == t, { progressType = t }, { Text(t.name.take(4)) })
                    }
                }
                Text("Schedule: ${scheduleType.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScheduleType.entries.take(4).forEach { s ->
                        FilterChip(scheduleType == s, { scheduleType = s }, { Text(s.name.take(5)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(
                    Habit(name = name, progressType = progressType, scheduleType = scheduleType, colorArgb = 0xFF6750A4.toInt())
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HabitDetailScreen(habitId: Long, viewModel: HabitsViewModel = hiltViewModel()) {
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val habit = habits.find { it.id == habitId }
    LaunchedEffect(habitId) { viewModel.loadHeatmap(habitId) }
    if (habit == null) {
        EmptyState("Habit not found")
        return
    }
    Column(Modifier.padding(16.dp)) {
        Text(habit.name, style = MaterialTheme.typography.headlineMedium)
        HeatmapView(heatmap)
    }
}

@Composable
fun HeatmapView(data: Map<LocalDate, Int>) {
    SectionHeader("Year Heatmap")
    val year = LocalDate.now().year
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..52).chunked(13).forEach { weekChunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                weekChunk.forEach { week ->
                    val date = LocalDate.ofYearDay(year, (week * 7).coerceAtMost(365))
                    val active = data[date] == 1
                    Box(
                        Modifier.size(12.dp).background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(achievements) { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    Text(a.type, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
