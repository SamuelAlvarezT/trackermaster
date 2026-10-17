package com.trackermaster.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
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
    val colors = remember(themeMode, accentIndex) { trackermasterColors(themeMode, accentIndex) }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

internal fun trackermasterColors(themeMode: ThemeMode, accentIndex: Int): ColorScheme {
    val base = if (themeMode == ThemeMode.LIGHT) lightColorScheme() else darkColorScheme(
        background = if (themeMode == ThemeMode.OLED) Color.Black else Color(0xFF121212),
        surface = if (themeMode == ThemeMode.OLED) Color.Black else Color(0xFF1E1E1E),
    )
    val accent = readableAccent(AccentColors[accentIndex.coerceIn(AccentColors.indices)], base.surfaceContainerHighest)
    return base.copy(primary = accent, secondary = accent, surfaceTint = accent)
}

/** Preserve the selected hue while making accent text readable on Material card surfaces. */
internal fun readableAccent(accent: Color, background: Color): Color {
    fun contrast(color: Color): Float {
        val a = color.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    if (contrast(accent) >= 4.5f) return accent
    val target = if (background.luminance() < 0.5f) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (contrast(lerp(accent, target, middle)) >= 4.5f) high = middle else low = middle
    }
    return lerp(accent, target, high)
}
