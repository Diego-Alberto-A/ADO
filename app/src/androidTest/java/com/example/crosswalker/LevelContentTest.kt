package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class LevelContentTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    private fun tap(text: String) {
        ui.onNodeWithText(text, substring = true).performClick()
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
    }

    @Test fun finalLevelCanBePlayedWithoutCompletingPreviousLevels() {
        val prefs = ui.activity.getSharedPreferences("crosswalker", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getInt("stars_11", 0)
        val hadPrevious = prefs.contains("stars_11")
        try {
            ui.runOnIdle { prefs.edit().remove("stars_11").apply() }
            tap("JUGAR")
            tap("MUNDO 3")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("La ciudad de todos"))
            ui.onNodeWithText("La ciudad de todos").assertIsEnabled()
            tap("La ciudad de todos")
            tap("PREPARAR EL PLAN")
            ui.onNodeWithContentDescription("Nivel 12", substring = true).assertIsDisplayed()
        } finally {
            ui.runOnIdle {
                val edit = prefs.edit()
                if (hadPrevious) edit.putInt("stars_11", previous) else edit.remove("stars_11")
                edit.apply()
            }
        }
    }

    @Test fun levelDialogIsLimitedToTitleStarsAndPlayButton() {
        tap("JUGAR")
        tap("La música no deja oír")
        ui.onAllNodesWithText("La música no deja oír").onLast().assertIsDisplayed()
        ui.onNodeWithText("Llega al otro lado", substring = true).assertIsDisplayed()
        ui.onNodeWithText("PREPARAR EL PLAN", substring = true).assertIsDisplayed()
        ui.onNodeWithText("FICHA NUEVA", substring = true).assertDoesNotExist()
    }

    @Test fun toolPaletteOnlyShowsFichasAvailableForThisLevel() {
        tap("JUGAR")
        tap("Mi primer cruce")
        tap("PREPARAR EL PLAN")
        tap("ABRIR PLAN")
        ui.onNodeWithText("MIRAR", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ESPERAR", substring = true).assertDoesNotExist()
    }
}
