package com.logact.peereminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// High contrast dark color scheme for elderly-friendly UI
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = BrightGreen,
    secondary = BrightYellow,
    tertiary = BrightRed,
    background = DarkBackground,
    surface = DarkGray,
    onPrimary = BrightText,
    onSecondary = DarkBackground,
    onTertiary = BrightText,
    onBackground = BrightText,
    onSurface = BrightText
)

@Composable
fun PeeReminderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HighContrastDarkColorScheme,
        typography = Typography,
        content = content
    )
}