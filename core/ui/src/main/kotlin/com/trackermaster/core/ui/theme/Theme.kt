package com.trackermaster.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trackermaster.core.domain.model.ThemeMode

val AccentColors = listOf(
    Color(0xFF6750A4), Color(0xFF006A6B), Color(0xFF7D5260), Color(0xFF984061),
    Color(0xFF4A635F), Color(0xFF3B647A), Color(0xFF5D4037), Color(0xFF455A64),
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF2196F3),
    Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A),
    Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548),
)

@Composable
fun TrackermasterTheme(
    themeMode: ThemeMode,
    accentIndex: Int,
    content: @Composable () -> Unit,
) {
    val accent = AccentColors[accentIndex.coerceIn(AccentColors.indices)]
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    } || (themeMode == ThemeMode.LIGHT && isSystemInDarkTheme().not() && false)

    val darkScheme = darkColorScheme(
        primary = accent,
        secondary = accent.copy(alpha = 0.8f),
        background = if (themeMode == ThemeMode.OLED) Color.Black else Color(0xFF121212),
        surface = if (themeMode == ThemeMode.OLED) Color.Black else Color(0xFF1E1E1E),
    )
    val lightScheme = lightColorScheme(primary = accent, secondary = accent.copy(alpha = 0.8f))

    MaterialTheme(
        colorScheme = if (useDark) darkScheme else lightScheme,
        content = content,
    )
}
