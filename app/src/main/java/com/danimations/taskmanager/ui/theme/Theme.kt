package com.danimations.taskmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60CDFF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFC3E8FF),
    secondary = Color(0xFFB8C8D6),
    background = Color(0xFF1C1C1C),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2B2B2B),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFFF99A4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF005FB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF52606D),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFF3F3F3),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF5F5F5F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF9F9F9),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFFADADAD),
    outlineVariant = Color(0xFFDDDDDD),
    error = Color(0xFFC42B1C),
)

/** Per-resource accent colors used by graphs, mirroring the classic desktop task manager palette. */
@Immutable
data class ResourceColors(
    val cpu: Color,
    val memory: Color,
    val storage: Color,
    val network: Color,
    val battery: Color,
    val heat: Color,
    val good: Color,
    val warn: Color,
)

private val DarkResources = ResourceColors(
    cpu = Color(0xFF4CB4FF),
    memory = Color(0xFFC07CF2),
    storage = Color(0xFF6CCB5F),
    network = Color(0xFFF0A04B),
    battery = Color(0xFF3CD3C1),
    heat = Color(0xFFFFB900),
    good = Color(0xFF6CCB5F),
    warn = Color(0xFFFFB900),
)

private val LightResources = ResourceColors(
    cpu = Color(0xFF117DBB),
    memory = Color(0xFF8B12AE),
    storage = Color(0xFF4DA60C),
    network = Color(0xFFA74F01),
    battery = Color(0xFF0E8A7E),
    heat = Color(0xFFFFB900),
    good = Color(0xFF107C10),
    warn = Color(0xFF9D5D00),
)

val LocalResourceColors = staticCompositionLocalOf { DarkResources }

@Composable
fun TaskManagerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalResourceColors provides if (dark) DarkResources else LightResources,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
