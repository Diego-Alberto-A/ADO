package com.example.crosswalker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.example.crosswalker.game.Simulation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Music resumes for the next attempt, including levels in the same world. */
class VictoryMusicTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    private fun tap(text: String) {
        ui.onNodeWithText(text, substring = true).performClick()
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
    }

    @Test fun winningRestartsLevelMusicOnNextLevel() {
        session.repo.tutorialDone = true   // sin overlay de tutorial, que no es lo que se prueba aquí
        session.repo.sound = true
        tap("JUGAR")
        tap("Mi primer cruce")
        tap("PREPARAR EL PLAN")
        ui.waitUntil(5000) { session.sound?.isMusicPlaying() == true }
        ui.runOnIdle {
            session.simulation = Simulation(session.level, session.plan).apply {
                people.forEach { it.done = true }
                tick()
            }
        }
        ui.waitUntil(5000) { session.musicSuspended }
        ui.runOnIdle { assertFalse("la música del nivel debe cortarse al ganar", session.sound!!.isMusicPlaying()) }
        ui.waitUntil(10000) { session.showResultDialog }
        tap("SIGUIENTE CAMINO")
        ui.runOnIdle {
            assertEquals(2, session.level.id)
            assertFalse(session.musicSuspended)
            assertTrue("el siguiente nivel debe arrancar su música", session.sound!!.isMusicPlaying())
        }
        tap("Ⅱ")
        tap("VOLVER AL MAPA")
        ui.waitUntil(5000) { session.sound?.isMusicPlaying() == true }
        ui.runOnIdle { assertFalse(session.musicSuspended) }
    }
}
