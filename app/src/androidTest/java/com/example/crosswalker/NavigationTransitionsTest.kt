package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.crosswalker.game.Simulation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationTransitionsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    private fun tap(text: String) {
        ui.onNodeWithText(text, substring = true).performClick()
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
    }

    @Test fun roundTripThroughWorldsGameAndNestedModals() {
        tap("AJUSTES")
        ui.onNodeWithText("A TU MANERA").assertIsDisplayed()
        tap("LISTO")
        ui.onNodeWithText("A TU MANERA").assertDoesNotExist()
        tap("JUGAR")
        tap("MUNDO 2")
        ui.onNodeWithText("Campus de innovación").assertIsDisplayed()
        tap("MUNDO 1")
        tap("Primeros pasos")
        tap("PREPARAR EL PLAN")
        ui.runOnIdle { assertEquals("game", session.screen) }
        tap("LA CALLE")
        tap("HABILIDADES")
        tap("Ⅱ")
        tap("AJUSTES")
        tap("LISTO")
        ui.onNodeWithText("PAUSA").assertIsDisplayed()
        tap("CONTINUAR")
        ui.onNodeWithText("PAUSA").assertDoesNotExist()
        tap("Ⅱ")
        tap("VOLVER AL MAPA")
        ui.onNodeWithText("ELIGE TU PRÓXIMA AVENTURA").assertIsDisplayed()
        tap("INICIO")
        ui.onNodeWithText("JUGAR").assertIsDisplayed()
    }

    @Test fun repeatedNavigationIsIgnoredAndSystemBackReturnsHome() {
        tap("JUGAR")
        ui.runOnIdle {
            session.enter(session.repo.levels[0])
            session.enter(session.repo.levels[1])
        }
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
        ui.runOnIdle { assertEquals(1, session.level.id) }
        tap("Ⅱ")
        tap("VOLVER AL MAPA")
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.waitUntil(5000) { !session.transitioning }
        ui.onNodeWithText("JUGAR").assertIsDisplayed()
    }

    @Test fun resultsLeaveBeforeOpeningNextLevelAndRetryReturnsToPlanning() {
        tap("JUGAR")
        tap("Primeros pasos")
        tap("PREPARAR EL PLAN")
        ui.runOnIdle {
            // Finish deterministically; the test covers result navigation, not traffic timing.
            session.simulation = Simulation(session.level, session.plan).apply {
                people.forEach { it.done = true }
                tick()
            }
        }
        ui.waitForIdle()
        tap("SIGUIENTE CAMINO")
        ui.runOnIdle {
            assertEquals(2, session.level.id)
            assertNull(session.simulation)
        }
        ui.onNodeWithText("¡BUEN CAMINO!").assertDoesNotExist()
        tap("Ⅱ")
        tap("REINTENTAR")
        ui.onNodeWithText("PAUSA").assertDoesNotExist()
        ui.onNodeWithText("PONER EL PLAN EN MARCHA", substring = true).assertIsDisplayed()
    }
}
