package com.logact.peereminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// High contrast light color scheme for elderly-friendly UI
private val HighContrastLightColorScheme = lightColorScheme(
    primary = BrightGreen,
    secondary = BrightYellow,
    tertiary = BrightRed,
    background = DarkBackground,
    surface = DarkGray,
    onPrimary = WhiteText,
    onSecondary = DarkBackground,
    onTertiary = WhiteText,
    onBackground = BrightText,
    onSurface = BrightText
)

@Composable
fun PeeReminderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HighContrastLightColorScheme,
        typography = Typography,
        content = content
    )
}