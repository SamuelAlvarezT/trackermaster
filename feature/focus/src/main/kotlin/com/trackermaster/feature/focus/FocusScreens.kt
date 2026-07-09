package com.trackermaster.feature.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.FocusRepository
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.model.FocusCategoryType
import com.trackermaster.core.domain.model.FocusSession
import com.trackermaster.core.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val repo: FocusRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val score = repo.weeklyFocusScore().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val sessions = repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settingsFlow = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.trackermaster.core.domain.model.AppSettings())

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft: StateFlow<Int> = _secondsLeft
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _plannedSeconds = MutableStateFlow(0)
    val plannedSeconds: StateFlow<Int> = _plannedSeconds
    private var sessionId: Long = 0

    fun start(category: FocusCategoryType) = viewModelScope.launch {
        val mins = settingsFlow.value.pomodoroWorkMinutes
        sessionId = repo.startSession(category, mins)
        _plannedSeconds.value = mins * 60
        _secondsLeft.value = _plannedSeconds.value
        _running.value = true
        while (_secondsLeft.value > 0 && _running.value) {
            delay(1000)
            _secondsLeft.value -= 1
        }
        if (_running.value) {
            repo.completeSession(sessionId, mins * 60 - _secondsLeft.value)
            _running.value = false
        }
    }

    fun pause() { _running.value = false }

    fun stop() = viewModelScope.launch {
        val elapsed = (_plannedSeconds.value - _secondsLeft.value).coerceAtLeast(0)
        if (sessionId != 0L && elapsed > 0) repo.stopSession(sessionId, elapsed, completed = false)
        _running.value = false
        _secondsLeft.value = 0
        _plannedSeconds.value = 0
        sessionId = 0
    }
}

@Composable
fun FocusScreen(vm: FocusViewModel = hiltViewModel()) {
    val score by vm.score.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val seconds by vm.secondsLeft.collectAsStateWithLifecycle()
    val plannedSeconds by vm.plannedSeconds.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(FocusCategoryType.WORK) }
    val todaySeconds = sessions.filter { it.completedAt?.toLocalDate() == LocalDate.now() }.sumOf { it.actualSeconds }
    val progress = if (plannedSeconds == 0) 0f else 1f - (seconds.toFloat() / plannedSeconds)

    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Today", "${todaySeconds / 60} min")
                StatChip("Focus Score", "$score")
            }
        }
        item {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 14.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%02d:%02d".format(seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                        textAlign = TextAlign.Center,
                    )
                    Text(category.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(FocusCategoryType.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.name.take(8)) },
                        leadingIcon = { Icon(categoryIcon(cat), null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!running) {
                    Button({ vm.start(category) }) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start")
                    }
                } else {
                    Button({ vm.pause() }) {
                        Text("Pause")
                    }
                }
                OutlinedButton({ vm.stop() }, enabled = plannedSeconds > 0) {
                    Text("Stop")
                }
            }
        }
        item {
            Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        }
        val groups = sessions.filter { it.completedAt != null }.groupBy { it.completedAt!!.toLocalDate() }
        if (groups.isEmpty()) {
            item { Text("No focus history yet", style = MaterialTheme.typography.bodyMedium) }
        } else {
            groups.forEach { (date, daySessions) ->
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dayLabel(date), style = MaterialTheme.typography.labelLarge)
                        daySessions.forEach { session -> FocusHistoryRow(session) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusHistoryRow(session: FocusSession) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(categoryIcon(session.category), null)
            Column(Modifier.weight(1f)) {
                Text(session.category.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${session.actualSeconds / 60} min ${session.actualSeconds % 60}s",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AssistChip(onClick = {}, label = { Text(if (session.completed) "Done" else "Partial") })
        }
    }
}

private fun dayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun categoryIcon(category: FocusCategoryType): ImageVector = when (category) {
    FocusCategoryType.WORK -> Icons.Default.PlayArrow
    FocusCategoryType.STUDY -> Icons.Default.PlayArrow
    FocusCategoryType.CREATIVE -> Icons.Default.PlayArrow
    FocusCategoryType.CODING -> Icons.Default.PlayArrow
    FocusCategoryType.READING, FocusCategoryType.WRITING -> Icons.Default.PlayArrow
    FocusCategoryType.EXERCISE -> Icons.Default.PlayArrow
    FocusCategoryType.OTHER -> Icons.Default.PlayArrow
}
