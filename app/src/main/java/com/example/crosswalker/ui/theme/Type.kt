package com.example.crosswalker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.crosswalker.R

/**
 * Three pixel faces, each at its native size: body copy, titles, and the HUD.
 * Sizes are expressed in sp so the design's pixel grid survives text scaling.
 */
val PixelifySans = FontFamily(
    Font(R.font.pixelify_sans),
    Font(R.font.pixelify_sans, FontWeight.SemiBold)
)
val Jersey10 = FontFamily(Font(R.font.jersey_10))
val Silkscreen = FontFamily(
    Font(R.font.silkscreen),
    Font(R.font.silkscreen_bold, FontWeight.Bold)
)

/** Jersey 25 still sets the board's in-world labels. */
val Jersey25 = FontFamily(Font(R.font.jersey_25))

/** Dialogue and general copy. */
val BodyText = TextStyle(fontFamily = PixelifySans, fontSize = 16.sp, lineHeight = 20.sp)
val BodyStrong = BodyText.copy(fontWeight = FontWeight.SemiBold)

/** Titles and the logo. */
val TitleText = TextStyle(fontFamily = Jersey10, fontSize = 32.sp, lineHeight = 36.sp)
val TitleSmall = TitleText.copy(fontSize = 24.sp, lineHeight = 28.sp)

/** Numbers, HUD and labels — always upper case. */
val HudText = TextStyle(fontFamily = Silkscreen, fontSize = 8.sp, lineHeight = 12.sp)
val HudStrong = HudText.copy(fontWeight = FontWeight.Bold)

/** Tabs and tool names: the HUD face, one step up so short words stay legible. */
val HudLabel = HudText.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)

private val defaults = Typography()
val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = Jersey10),
    displayMedium = defaults.displayMedium.copy(fontFamily = Jersey10),
    displaySmall = defaults.displaySmall.copy(fontFamily = Jersey10),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = Jersey10),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = Jersey10),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = Jersey10),
    titleLarge = defaults.titleLarge.copy(fontFamily = Jersey10),
    titleMedium = defaults.titleMedium.copy(fontFamily = Jersey10),
    titleSmall = defaults.titleSmall.copy(fontFamily = Jersey10),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = PixelifySans),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = PixelifySans),
    bodySmall = defaults.bodySmall.copy(fontFamily = PixelifySans),
    labelLarge = defaults.labelLarge.copy(fontFamily = Silkscreen),
    labelMedium = defaults.labelMedium.copy(fontFamily = Silkscreen),
    labelSmall = defaults.labelSmall.copy(fontFamily = Silkscreen)
)
