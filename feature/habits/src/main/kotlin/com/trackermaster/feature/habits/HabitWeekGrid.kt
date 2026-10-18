package com.trackermaster.feature.habits

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val HabitListHorizontalPadding = 16.dp
internal val HabitCardContentPadding = 12.dp

/** Identical seven-column geometry for the screen heading and every habit card. */
@Composable
internal fun HabitWeekGrid(modifier: Modifier = Modifier, cell: @Composable (Int) -> Unit) {
    Row(modifier.fillMaxWidth()) {
        repeat(7) { index ->
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { cell(index) }
        }
    }
}
