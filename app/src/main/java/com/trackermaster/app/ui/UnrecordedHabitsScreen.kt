package com.trackermaster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.trackermaster.app.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.domain.habits.UnrecordedHabitDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class UnrecordedHabitsViewModel @Inject constructor(private val repo: HabitRepository): ViewModel() {
    val days = repo.observeUnrecordedDays().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun record(day: UnrecordedHabitDay, completed: Boolean) = viewModelScope.launch { repo.recordPastDay(day.habit, day.date, completed) }
}

@Composable
fun UnrecordedHabitsScreen(onFocus: (Long) -> Unit, onBack: () -> Unit, vm: UnrecordedHabitsViewModel = hiltViewModel()) {
    val days by vm.days.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<UnrecordedHabitDay?>(null) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.unrecorded_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(stringResource(R.string.unrecorded_home)) }
        }
        when (val rows = days) {
            null -> CircularProgressIndicator()
            else -> if (rows.isEmpty()) Text(stringResource(R.string.unrecorded_empty)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { "${it.habit.id}:${it.date}" }) { row ->
                    Card(Modifier.fillMaxWidth().clickable { selected = row }) {
                        Column(Modifier.padding(16.dp)) { Text(row.habit.name, style = MaterialTheme.typography.titleMedium); Text(row.date.toString()) }
                    }
                }
            }
        }
    }
    selected?.let { day ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(day.habit.name) }, text = { Text(stringResource(R.string.unrecorded_date, day.date.toString())) },
            confirmButton = { TextButton(onClick = { if (YearMonth.from(day.date) < YearMonth.now()) confirm = true else { vm.record(day, true); selected = null } }) { Text(stringResource(R.string.unrecorded_done)) } },
            dismissButton = { Column {
                TextButton(onClick = { if (YearMonth.from(day.date) < YearMonth.now()) confirm = false else { vm.record(day, false); selected = null } }) { Text(stringResource(R.string.unrecorded_not_done)) }
                TextButton(onClick = { selected = null; onFocus(day.habit.id) }) { Text(stringResource(R.string.unrecorded_focus)) }
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.unrecorded_cancel)) }
            } })
        confirm?.let { value -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(R.string.unrecorded_confirm_title)) },
            text = { Text(stringResource(R.string.unrecorded_confirm_detail, day.habit.name, day.date.toString(), stringResource(if (value) R.string.unrecorded_done else R.string.unrecorded_not_done))) },
            confirmButton = { TextButton(onClick = { vm.record(day, value); confirm = null; selected = null }) { Text(stringResource(R.string.unrecorded_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.unrecorded_cancel)) } }) }
    }
}
