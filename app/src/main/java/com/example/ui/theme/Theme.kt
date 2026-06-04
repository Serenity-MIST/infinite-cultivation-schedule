package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = JadeGreen,
    secondary = AccentGold,
    tertiary = SlateVariant,
    background = OnyxBlack,
    surface = DarkSlate,
    onPrimary = OnyxBlack,
    onSecondary = OnyxBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Force modern Custom Dark Mode for absolute game immersion
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
