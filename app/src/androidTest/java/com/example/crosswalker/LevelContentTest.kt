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

    @Test fun levelDialogShowsNewToolCard() {
        ui.runOnIdle { session.repo.save(3, 1) }   // unlock level 4 for this run
        tap("JUGAR")
        tap("Ver entre los coches")
        ui.onNodeWithText("FICHA NUEVA", substring = true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Zona lenta", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun toolPaletteOnlyShowsFichasAvailableForThisLevel() {
        tap("JUGAR")
        tap("Mi primer cruce")
        tap("PREPARAR EL PLAN")
        ui.onNodeWithText("MIRAR", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ESPERAR", substring = true).assertDoesNotExist()
    }
}
