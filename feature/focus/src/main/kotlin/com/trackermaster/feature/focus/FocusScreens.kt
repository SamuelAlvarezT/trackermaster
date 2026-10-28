package com.trackermaster.feature.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.domain.focus.FocusRunStatus
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.ui.R as P8R
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.testTag

@Composable
fun FocusScreen(vm: FocusViewModel = hiltViewModel(),onStatistics: ()->Unit={}) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    FocusContent(state,vm::onAction,onStatistics)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FocusContent(state: FocusUiState,onAction: (FocusAction)->Unit,onStatistics: ()->Unit={}) {
    val locale=LocalConfiguration.current.locales[0]
    val runtime=state.runtime
    val displayMinutes=if(runtime.active)(runtime.plannedMs/60000).toString() else state.minutes
    val habitId=if (runtime.active) runtime.habitId else state.selectedHabitId
    val habit=state.habits.firstOrNull { it.id==habitId }
    var expanded by remember { mutableStateOf(false) }
    val progress=if(runtime.active && runtime.plannedMs>0) (runtime.effectiveMs.toFloat()/runtime.plannedMs).coerceIn(0f,1f) else 0f
    val number=remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits=2;minimumFractionDigits=0 } }
    val listState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    LazyColumn(state=listState,modifier=Modifier.fillMaxSize().imePadding().padding(horizontal=24.dp),contentPadding=PaddingValues(top=24.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        item { Text(stringResource(P8R.string.p8_focus_title),style=MaterialTheme.typography.headlineMedium)
            Row { TextButton({scope.launch {listState.animateScrollToItem((listState.layoutInfo.totalItemsCount-state.sessions.count {it.actualDurationMs>0 && it.completedAt!=null}-1).coerceAtLeast(0))}}) {Text("Historial")};TextButton(onStatistics) {Text("Estadísticas")}}
        }
        if(state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant) {
                Text(stringResource(P8R.string.p8_focus_time,formatFocusTime(state.todaySeconds,locale)),
                    Modifier.padding(8.dp),style=MaterialTheme.typography.labelLarge)
            }
            Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant) {
                Text("${stringResource(P8R.string.p8_focus_score)}: ${state.score}",
                    Modifier.padding(8.dp),style=MaterialTheme.typography.labelLarge)
            }
        } }
        item { Box(Modifier.size(220.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={progress},modifier=Modifier.fillMaxSize(),strokeWidth=14.dp)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(formatFocusTime(if(!runtime.active)(state.minutes.toLongOrNull() ?: 0)*60 else runtime.remainingSeconds.toLong(),locale),style=MaterialTheme.typography.displayMedium)
                Text(stringResource(categoryString(if(runtime.active)runtime.category else state.category)),style=MaterialTheme.typography.labelLarge)
                if(runtime.status==FocusRunStatus.PAUSED) Text(stringResource(P8R.string.p8_focus_paused),style=MaterialTheme.typography.labelSmall)
            }
        } }
        item { OutlinedTextField(value=displayMinutes,onValueChange={onAction(FocusAction.Duration(it))},
            enabled=!runtime.active && !state.loading,label={Text(stringResource(P8R.string.p8_focus_duration))},
            isError=displayMinutes.toIntOrNull()?.let { it !in 1..720 } ?: true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("focus_duration")) }
        item { Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={expanded=true},enabled=!runtime.active && !state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("focus_habit")) {
                Text("${stringResource(P8R.string.p8_focus_habit)}: ${habit?.name ?: stringResource(P8R.string.p8_focus_none)}")
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                DropdownMenuItem(text={Text(stringResource(P8R.string.p8_focus_none))},onClick={onAction(FocusAction.HabitSelected(null));expanded=false})
                state.habits.forEach { h -> DropdownMenuItem(text={Text(h.name)},onClick={onAction(FocusAction.HabitSelected(h.id));expanded=false}) }
            }
        } }
        if(habit!=null) item {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(habit.targetDurationMs>0) Text(stringResource(P8R.string.p8_focus_goal,
                    stringResource(P8R.string.p8_focus_minutes,number.format(habit.targetDurationMs/60_000.0))))
                if(habit.progressType==ProgressType.COUNTER || habit.progressType==ProgressType.CHECKLIST) {
                    val units=if(habit.progressType==ProgressType.COUNTER)habit.targetCount else habit.checklistItems.size
                    Text(stringResource(P8R.string.p8_focus_goal,pluralStringResource(P8R.plurals.p8_focus_units,units,units)))
                    Text(stringResource(P8R.string.p8_focus_quantity),style=MaterialTheme.typography.bodySmall)
                } else if(habit.progressType==ProgressType.CHECKBOX) Text(stringResource(P8R.string.p8_focus_simple),style=MaterialTheme.typography.bodySmall)
            }
        } else if(habitId!=null) item { Text(stringResource(P8R.string.p8_focus_unavailable),style=MaterialTheme.typography.bodySmall) }
        item { LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(FocusCategoryType.entries,key={it.name}) { category ->
                FilterChip(selected=(if(runtime.active)runtime.category else state.category)==category,enabled=!runtime.active,
                    onClick={onAction(FocusAction.Category(category))},label={Text(stringResource(categoryString(category)))})
            }
        } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            when(runtime.status) {
                FocusRunStatus.RUNNING -> Button(onClick={onAction(FocusAction.Pause)},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(P8R.string.p8_focus_pause)) }
                FocusRunStatus.PAUSED -> Button(onClick={onAction(FocusAction.Resume)},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(P8R.string.p8_focus_resume)) }
                else -> Button(onClick={onAction(FocusAction.Start)},enabled=!state.loading && state.minutes.toIntOrNull()?.let { it in 1..720 }==true,
                    modifier=Modifier.heightIn(min=48.dp)) { Icon(Icons.Default.PlayArrow,null);Text(stringResource(P8R.string.p8_focus_start)) }
            }
            if(runtime.active) OutlinedButton(onClick={onAction(FocusAction.Stop)},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(P8R.string.p8_focus_stop)) }
        } }
        if(state.error) item { Text(stringResource(P8R.string.p8_focus_error),color=MaterialTheme.colorScheme.error) }
        item { Text(stringResource(P8R.string.p8_focus_history),modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium) }
        val history=state.sessions.filter { it.actualDurationMs>0 && it.completedAt!=null }
        if(history.isEmpty()) item { Text(stringResource(P8R.string.p8_focus_empty)) }
        items(history,key={it.id}) { session ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(session.habitName ?: stringResource(categoryString(session.category)),style=MaterialTheme.typography.titleSmall)
                Text("${formatFocusTime(session.actualDurationMs/1000,locale)} · ${session.completedAt?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)).orEmpty()}")
                Text(stringResource(if(session.completed)P8R.string.p8_focus_done else P8R.string.p8_focus_partial),style=MaterialTheme.typography.labelSmall)
            } }
        }
    }
}

internal fun categoryString(category: FocusCategoryType): Int = when(category) {
    FocusCategoryType.WORK -> P8R.string.p8_focus_cat_work
    FocusCategoryType.STUDY -> P8R.string.p8_focus_cat_study
    FocusCategoryType.CREATIVE -> P8R.string.p8_focus_cat_creative
    FocusCategoryType.CODING -> P8R.string.p8_focus_cat_coding
    FocusCategoryType.READING -> P8R.string.p8_focus_cat_reading
    FocusCategoryType.WRITING -> P8R.string.p8_focus_cat_writing
    FocusCategoryType.EXERCISE -> P8R.string.p8_focus_cat_exercise
    FocusCategoryType.OTHER -> P8R.string.p8_focus_cat_other
}
internal fun formatFocusTime(seconds: Long,locale: java.util.Locale): String = String.format(locale,"%02d:%02d",seconds/60,seconds%60)

@androidx.compose.ui.tooling.preview.Preview(name="Focus · English",locale="en",showBackground=true)
@androidx.compose.ui.tooling.preview.Preview(name="Focus · Español",locale="es",showBackground=true)
@androidx.compose.ui.tooling.preview.Preview(name="Focus · 中文",locale="zh",showBackground=true)
@Composable
private fun FocusPreview() {
    com.trackermaster.core.ui.theme.TrackermasterTheme(ThemeMode.LIGHT,0) {
        Surface { FocusContent(FocusUiState(loading=false),{}) }
    }
}
