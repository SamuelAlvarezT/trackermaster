package com.trackermaster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.habits.PendingHabit
import com.trackermaster.core.ui.R as P8R
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class PendingHabitsUiState(val loading: Boolean=true,val habits: List<PendingHabit> = emptyList(),val error: Boolean=false)

@Composable
fun PendingHabitsCard(state: PendingHabitsUiState,onOpen: (Long)->Unit,modifier: Modifier=Modifier) {
    val locale=LocalConfiguration.current.locales[0]
    val number=remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits=2;minimumFractionDigits=0 } }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(P8R.string.p8_pending_title),style=MaterialTheme.typography.titleMedium)
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error -> Text(stringResource(P8R.string.p8_pending_error),color=MaterialTheme.colorScheme.error)
                state.habits.isEmpty() -> Text(stringResource(P8R.string.p8_pending_none))
                else -> state.habits.forEach { row ->
                    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button) { onOpen(row.habit.id) }
                        .padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(row.habit.name,style=MaterialTheme.typography.titleSmall)
                        Text(stringResource(P8R.string.p8_pending_days,number.format(row.equivalentDays)))
                        if(row.includesProvisionalToday) Text(stringResource(P8R.string.p8_pending_today),style=MaterialTheme.typography.bodySmall)
                        if(!row.creationDateKnown) Text(stringResource(P8R.string.p8_pending_baseline,
                            row.trackingFrom.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name="Pending habits",locale="es",showBackground=true)
@Composable
private fun PendingHabitsPreview() {
    com.trackermaster.core.ui.theme.TrackermasterTheme(com.trackermaster.core.domain.model.ThemeMode.LIGHT,0) {
        PendingHabitsCard(PendingHabitsUiState(loading=false),{})
    }
}
