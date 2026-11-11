package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TutorialTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    private fun tap(text: String) {
        ui.onNodeWithText(text, substring = true).performClick()
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
    }

    /** One tap finishes the typewriter, the next turns the page: tap until the line is gone. */
    private fun advancePast(line: String) {
        repeat(40) {
            ui.onNodeWithContentDescription(line).performClick()
            ui.waitForIdle()
            if (ui.onAllNodesWithContentDescription(line).fetchSemanticsNodes().isEmpty()) return
            Thread.sleep(150)
        }
        fail("no se pudo avanzar la línea: $line")
    }

    @Test fun tutorialQueueAdvancesByTappingTheBoxThenBecomesReactive() {
        tap("JUGAR")
        tap("Mi primer cruce")
        tap("PREPARAR EL PLAN")
        val level = session.level
        assertTrue("el nivel 1 debe tener introLines", level.introLines.isNotEmpty())
        ui.onNodeWithContentDescription(level.introLines[0]).assertIsDisplayed()
        level.introLines.forEach { advancePast(it) }
        ui.onNodeWithContentDescription(level.lesson).assertIsDisplayed()
        tap("MIRAR")
        ui.onNodeWithContentDescription(level.readyMessage).assertIsDisplayed()
    }
}
