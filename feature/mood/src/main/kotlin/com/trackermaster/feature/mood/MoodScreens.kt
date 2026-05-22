package com.trackermaster.feature.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.MoodRepository
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.MoodEntry
import com.trackermaster.core.domain.model.MoodHabitCorrelation
import com.trackermaster.core.domain.model.MoodLevel
import com.trackermaster.core.ui.components.EmptyState
import com.trackermaster.core.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class MoodViewModel @Inject constructor(private val repo: MoodRepository) : ViewModel() {
    val entries = repo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val levels = HabitScheduleLogic.defaultMoodLevels()
    val month = YearMonth.now()
    val calendar = repo.calendarForMonth(month).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val _correlation = MutableStateFlow<List<MoodHabitCorrelation>>(emptyList())
    val correlation: StateFlow<List<MoodHabitCorrelation>> = _correlation

    fun log(level: Int) = viewModelScope.launch { repo.logMood(level) }
    fun loadCorrelation() = viewModelScope.launch { _correlation.value = repo.moodHabitCorrelation() }
}

@Composable
fun MoodScreen(vm: MoodViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp)) {
        SectionHeader("How are you feeling?")
        MoodLevelPicker(vm.levels) { vm.log(it) }
        Spacer(Modifier.height(16.dp))
        SectionHeader("Recent")
        if (entries.isEmpty()) EmptyState("No mood logs yet")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.take(10)) { e -> MoodEntryRow(e, vm.levels) }
        }
    }
}

@Composable
fun MoodLevelPicker(levels: List<MoodLevel>, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(levels) { level ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(level.level) }
                    .padding(8.dp),
            ) {
                Text(level.emoji, style = MaterialTheme.typography.headlineMedium)
                Text("${level.level}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun MoodEntryRow(entry: MoodEntry, levels: List<MoodLevel>) {
    val level = levels.find { it.level == entry.level }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(level?.emoji ?: "😐", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(level?.label ?: "Level ${entry.level}")
                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MoodCalendarScreen(vm: MoodViewModel = hiltViewModel()) {
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val levels = vm.levels
    Column(Modifier.padding(16.dp)) {
        SectionHeader("${vm.month}")
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(280.dp)) {
            items(vm.month.lengthOfMonth()) { day ->
                val date = vm.month.atDay(day + 1)
                val level = calendar[date]
                val color = levels.find { it.level == level }?.colorArgb ?: 0xFF424242.toInt()
                Box(
                    Modifier.aspectRatio(1f).padding(2.dp).background(Color(color).copy(alpha = if (level != null) 0.8f else 0.2f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { Text("${day + 1}", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
fun MoodCorrelationScreen(vm: MoodViewModel = hiltViewModel()) {
    val data by vm.correlation.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCorrelation() }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(data) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.habitName, style = MaterialTheme.typography.titleMedium)
                    Text("With habit: ${"%.1f".format(c.avgMoodWithHabit)}")
                    Text("Without: ${"%.1f".format(c.avgMoodWithoutHabit)}")
                }
            }
        }
    }
}
