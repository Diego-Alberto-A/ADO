package com.example.crosswalker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.crosswalker.R

val Jersey25 = FontFamily(Font(R.font.jersey_25))

private val defaults = Typography()
val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = Jersey25),
    displayMedium = defaults.displayMedium.copy(fontFamily = Jersey25),
    displaySmall = defaults.displaySmall.copy(fontFamily = Jersey25),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = Jersey25),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = Jersey25),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = Jersey25),
    titleLarge = defaults.titleLarge.copy(fontFamily = Jersey25),
    titleMedium = defaults.titleMedium.copy(fontFamily = Jersey25),
    titleSmall = defaults.titleSmall.copy(fontFamily = Jersey25),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = Jersey25),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = Jersey25),
    bodySmall = defaults.bodySmall.copy(fontFamily = Jersey25),
    labelLarge = defaults.labelLarge.copy(fontFamily = Jersey25),
    labelMedium = defaults.labelMedium.copy(fontFamily = Jersey25),
    labelSmall = defaults.labelSmall.copy(fontFamily = Jersey25)
)
