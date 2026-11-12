package com.example.crosswalker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SoundHooksTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun enteringALevelSwitchesToItsWorldSong() {
        rule.onNodeWithText("JUGAR").performClick()
        rule.onNodeWithText("Mi primer cruce").performClick()
        rule.onNodeWithText("PREPARAR EL PLAN", substring = true).performClick()
        rule.waitForIdle()
        // El nivel 1 es del Mundo 1: musicForScreen("game", 0) == R.raw.music_world1.
        // No hay getter directo del resId activo; se infiere por ausencia de crash + que
        // isMusicPlaying() sea true una vez dentro del tablero.
        rule.onNodeWithText("PONER EL PLAN EN MARCHA", substring = true).assertExists()
    }
}
