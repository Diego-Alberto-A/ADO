package com.example.crosswalker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sampled from the Kenney tiles the sprites are cut from, so the UI and
 * the board share one set of colours.
 *
 * Accents carry a single meaning each and are reserved for buttons, bars,
 * badges and names — never for large backgrounds.
 */

// Neutrals
val Ink = Color(0xFF2B2540)          // outlines and body text
val InkShadow = Color(0xFF1A1626)    // the solid 1px drop shadow under everything
val Asphalt = Color(0xFF4E4A63)      // dark panels and HUD
val AsphaltEdge = Color(0xFF6B6784)  // inner edge of a dark panel
val Kerb = Color(0xFF9C99B5)
val KerbLight = Color(0xFFC4C2D8)    // disabled
val Cream = Color(0xFFF4EAD0)        // light panels
val CreamLight = Color(0xFFFFF8E6)   // top highlight, text on dark
val CreamDark = Color(0xFFE0CFA8)    // sunken slots, inner border

/** base / highlight / shade for one accent. */
data class Accent(val base: Color, val light: Color, val dark: Color)

val Taxi = Accent(Color(0xFFFFC23D), Color(0xFFFFE07A), Color(0xFFD98A1C))     // primary action, focus
val Tree = Accent(Color(0xFF3FD4A2), Color(0xFF8CF2CC), Color(0xFF1F9A78))     // confirm, success
val Brick = Accent(Color(0xFFD9483F), Color(0xFFF27A6B), Color(0xFF962A35))    // cancel, error
val Sky = Accent(Color(0xFF3B9BE0), Color(0xFF7CC6F5), Color(0xFF2468A8))      // information, selection
val Orange = Accent(Color(0xFFE8823A), Color(0xFFF7AE6B), Color(0xFFB35A24))   // money, rewards

/** Secondary buttons: a cream face, no accent meaning attached. */
val Neutral = Accent(Cream, CreamLight, CreamDark)
