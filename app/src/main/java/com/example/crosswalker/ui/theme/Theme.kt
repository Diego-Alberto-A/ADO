package com.example.crosswalker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The game ships one palette. It deliberately ignores the system's dark and
 * dynamic colour schemes: the UI has to keep matching the sprites.
 */
private val GameColors = lightColorScheme(
    primary = Tree.base,
    onPrimary = CreamLight,
    secondary = Brick.base,
    tertiary = Sky.base,
    background = Cream,
    surface = Cream,
    onBackground = Ink,
    onSurface = Ink
)

@Composable
fun CrosswalkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColors, typography = Typography, content = content)
}
