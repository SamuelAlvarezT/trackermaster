package com.trackermaster.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.trackermaster.core.domain.model.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class AccentContrastTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun allAccentPresetsRemainReadableInEveryTheme() {
        ThemeMode.entries.forEach { theme ->
            AccentColors.indices.forEach { index ->
                val colors = trackermasterColors(theme, index)
                listOf(colors.surface, colors.background, colors.surfaceContainerHighest).forEach { background ->
                    assertTrue("$theme accent $index on $background", contrast(colors.primary, background) >= 4.5f)
                    assertTrue("$theme secondary $index", contrast(colors.secondary, background) >= 4.5f)
                }
                assertTrue("$theme filled button $index", contrast(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue("$theme secondary button $index", contrast(colors.onSecondary, colors.secondary) >= 4.5f)
            }
        }
    }

    @Test fun readablePresetsAndBlackOledBackgroundArePreserved() {
        assertEquals(AccentColors.first(), trackermasterColors(ThemeMode.LIGHT, 0).primary)
        val oled = trackermasterColors(ThemeMode.OLED, 0)
        assertEquals(Color.Black, oled.background)
        assertEquals(Color.Black, oled.surface)
        assertNotEquals(AccentColors.first(), oled.primary)
    }

    @Test fun accentIndicesRetainTheirPreviousClampingBehavior() {
        assertEquals(trackermasterColors(ThemeMode.LIGHT, 0).primary, trackermasterColors(ThemeMode.LIGHT, -10).primary)
        assertEquals(trackermasterColors(ThemeMode.DARK, AccentColors.lastIndex).primary, trackermasterColors(ThemeMode.DARK, 100).primary)
    }
}
