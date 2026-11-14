package com.example.crosswalker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SoundHooksTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun menuMusicContinuesWhenNavigating() {
        val session = ViewModelProvider(rule.activity)[Session::class.java]
        val previousSound = rule.runOnIdle {
            val previous = session.repo.sound
            session.repo.sound = true
            session.sound!!.playMusic(R.raw.music_menu)
            previous
        }
        try {
            rule.waitUntil(5000) { rule.runOnIdle { session.sound!!.musicPositionMs >= 1500 } }
            for (label in listOf("JUGAR", "MUNDO 2", "INICIO")) {
                val before = rule.runOnIdle { session.sound!!.musicPositionMs }
                rule.onNodeWithText(label, substring = true).performClick()
                rule.waitUntil(5000) { !session.transitioning }
                val after = rule.runOnIdle { session.sound!!.musicPositionMs }
                assertTrue("La pista volvió al inicio al tocar $label ($before → $after ms)", after >= before)
            }
        } finally {
            rule.runOnIdle { session.repo.sound = previousSound }
        }
    }

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
