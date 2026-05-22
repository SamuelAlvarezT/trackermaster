package com.trackermaster.feature.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.trackermaster.core.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val repo: FocusRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val score = repo.weeklyFocusScore().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val settingsFlow = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.trackermaster.core.domain.model.AppSettings())

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft: StateFlow<Int> = _secondsLeft
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private var sessionId: Long = 0

    fun start(category: FocusCategoryType) = viewModelScope.launch {
        val mins = settingsFlow.value.pomodoroWorkMinutes
        sessionId = repo.startSession(category, mins)
        _secondsLeft.value = mins * 60
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
}

@Composable
fun FocusScreen(vm: FocusViewModel = hiltViewModel()) {
    val score by vm.score.collectAsStateWithLifecycle()
    val seconds by vm.secondsLeft.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(FocusCategoryType.WORK) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StatChip("Focus Score", "$score")
        Spacer(Modifier.height(32.dp))
        Text(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FocusCategoryType.entries) { cat ->
                FilterChip(category == cat, { category = cat }, { Text(cat.name.take(8)) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!running) {
                Button({ vm.start(category) }) { Text("Start") }
            } else {
                Button({ vm.pause() }) { Text("Pause") }
            }
        }
    }
}
