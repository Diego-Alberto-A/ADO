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

    // Fija el progreso que la prueba necesita y restaura el anterior al terminar.
    // Sin esto cada prueba depende de las estrellas que quedaron en el dispositivo.
    private fun withStars(vararg stars: Pair<Int, Int?>, body: () -> Unit) {
        val prefs = ui.activity.getSharedPreferences("crosswalker", android.content.Context.MODE_PRIVATE)
        val wanted = stars.associate { (level, value) -> "stars_$level" to value }
        val previous = wanted.keys.associateWith { if (prefs.contains(it)) prefs.getInt(it, 0) else null }
        fun write(values: Map<String, Int?>) = ui.runOnIdle {
            prefs.edit().apply {
                values.forEach { (key, value) -> if (value == null) remove(key) else putInt(key, value) }
            }.apply()
        }
        try {
            write(wanted)
            body()
        } finally {
            write(previous)
        }
    }

    @Test fun finalLevelStaysLockedUntilTheEleventhIsCleared() {
        withStars(11 to null) {
            tap("JUGAR")
            tap("MUNDO 3")
            ui.onNodeWithContentDescription("Nivel 12, La ciudad de todos, no disponible").assertExists()
            ui.onNodeWithText("La ciudad de todos").assertDoesNotExist()
        }
    }

    @Test fun finalLevelCanBePlayedOnceTheEleventhIsCleared() {
        withStars(11 to 1) {
            tap("JUGAR")
            tap("MUNDO 3")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("La ciudad de todos"))
            ui.onNodeWithText("La ciudad de todos").assertIsEnabled()
            tap("La ciudad de todos")
            tap("PREPARAR EL PLAN")
            ui.onNodeWithContentDescription("Nivel 12", substring = true).assertIsDisplayed()
        }
    }

    @Test fun levelDialogIsLimitedToTitleStarsAndPlayButton() {
        // El nivel 4 solo se abre con el 3 superado.
        withStars(1 to 1, 2 to 1, 3 to 1) {
            tap("JUGAR")
            tap("La música no deja oír")
            ui.onAllNodesWithText("La música no deja oír").onLast().assertIsDisplayed()
            ui.onNodeWithText("Llega al otro lado", substring = true).assertIsDisplayed()
            ui.onNodeWithText("PREPARAR EL PLAN", substring = true).assertIsDisplayed()
            ui.onNodeWithText("FICHA NUEVA", substring = true).assertDoesNotExist()
        }
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
