package ru.flawden.baskovmusic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val BaskovPurple = Color(0xFFB85CFF)
internal val BaskovCyan = Color(0xFF33E6FF)
internal val BaskovMagenta = Color(0xFFFF4FD8)

private val BaskovDarkColorScheme = darkColorScheme(
    primary = BaskovPurple,
    onPrimary = Color(0xFF140020),
    primaryContainer = Color(0xFF35105A),
    onPrimaryContainer = Color(0xFFF3DDFF),
    secondary = BaskovCyan,
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF063D48),
    onSecondaryContainer = Color(0xFFB9F4FF),
    tertiary = BaskovMagenta,
    onTertiary = Color(0xFF310027),
    background = Color(0xFF070712),
    onBackground = Color(0xFFF6F0FF),
    surface = Color(0xFF0E1020),
    onSurface = Color(0xFFF6F0FF),
    surfaceVariant = Color(0xFF1A1930),
    onSurfaceVariant = Color(0xFFC9C4D8),
    outline = Color(0xFF706A82),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
internal fun BaskovTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BaskovDarkColorScheme, content = content)
}
