package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * El tutorial del nivel 1 ya no avanza con cualquier toque: cada paso tiene un único objetivo
 * válido (la caja de diálogo, o la acción real — cruce, ficha MIRAR, botón de lanzar). Tocar
 * cualquier otra cosa no debe cambiar nada; PAUSA sigue funcionando siempre.
 *
 * Nota: mientras el overlay está activo, un solo toque sobre la caja de diálogo avanza la línea
 * directamente (el overlay llama a `advanceDialogue`, no al `onClick` interno de `DialogueBox`
 * que antes completaba el tecleo en el primer toque y avanzaba en el segundo) — por eso aquí basta
 * un `tapDialogue` por línea, a diferencia del viejo `advancePast` que tocaba dos veces.
 */
class TutorialTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    private fun tapText(text: String, substring: Boolean = false) {
        ui.onNodeWithText(text, substring = substring).performClick()
        ui.waitForIdle()
    }

    /** Toca la caja de diálogo por su contenido actual. */
    private fun tapDialogue(line: String) {
        ui.onNodeWithContentDescription(line).performClick()
        ui.waitForIdle()
    }

    private fun enterTutorial() {
        // `tutorial_done` vive en SharedPreferences, que persiste entre métodos @Test dentro de
        // la misma ejecución instrumentada (cada uno lanza una Activity nueva, pero el archivo de
        // preferencias es el mismo): si un test anterior completó el tutorial, hay que reiniciarlo
        // aquí para que cada test parta de un estado limpio, sin depender del orden de ejecución.
        session.repo.tutorialDone = false
        tapText("JUGAR")
        tapText("Mi primer cruce")
        tapText("PREPARAR EL PLAN", substring = true)
        ui.waitForIdle()
    }

    /** Avanza las líneas narrativas 0..hasta (exclusive) tocando la caja de diálogo una vez cada una. */
    private fun advanceNarrationLines(hasta: Int) {
        for (i in 0 until hasta) tapDialogue(session.level.introLines[i])
    }

    @Test fun tappingOutsideTheActiveTargetChangesNothing() {
        enterTutorial()
        val before = session.introIndex
        val tabBefore = session.tab
        // "LA CALLE" no es el objetivo del primer paso (la caja de diálogo lo es): tocarla no
        // debe cambiar de pestaña ni avanzar el tutorial.
        tapText("LA CALLE")
        assertEquals(before, session.introIndex)
        assertEquals(tabBefore, session.tab)
    }

    @Test fun pauseStillWorksMidTutorial() {
        enterTutorial()
        assertFalse(session.paused)
        tapText("Ⅱ")
        assertTrue("PAUSA debe seguir funcionando aunque el tutorial esté resaltando otro elemento", session.paused)
    }

    @Test fun narrationLinesOnlyAdvanceByTappingTheDialogueItself() {
        enterTutorial()
        val level = session.level
        // Las primeras líneas (0..4) son narrativas/informativas: su único toque válido es la
        // propia caja de diálogo, sin importar qué más señalen (PAUSA, FICHAS, la pestaña...).
        for (i in 0..4) {
            ui.onNodeWithContentDescription(level.introLines[i]).assertIsDisplayed()
            tapDialogue(level.introLines[i])
        }
        assertEquals(5, session.introIndex)
    }

    @Test fun crossingStepRequiresTappingAChipNotTheDialogue() {
        enterTutorial()
        val level = session.level
        advanceNarrationLines(5)
        assertEquals(5, session.introIndex)
        val crossingBefore = session.plan.people.getValue(session.selected).crossing
        // Tocar la caja de diálogo en este paso no debe hacer nada: el objetivo ahora es el cruce.
        tapDialogue(level.introLines[5])
        assertEquals(5, session.introIndex)
        assertEquals(crossingBefore, session.plan.people.getValue(session.selected).crossing)
        // Tocar "Der." sí es el objetivo válido: aplica la elección real y avanza el tutorial.
        tapText("Der.")
        assertEquals(2, session.plan.people.getValue(session.selected).crossing)
        assertEquals(6, session.introIndex)
    }

    @Test fun toolAndLaunchStepsRequireTappingThoseExactControls() {
        enterTutorial()
        advanceNarrationLines(5)
        tapText("Der.")
        // Fase de habilidad: tocar MIRAR es el único objetivo válido (el nivel 1 solo ofrece esa
        // ficha, así que "LA CALLE" sirve de toque fuera de objetivo para esta comprobación).
        assertFalse("look" in session.plan.people.getValue(session.selected).skills)
        tapText("LA CALLE")
        assertFalse("tocar fuera del objetivo no debe elegir nada", "look" in session.plan.people.getValue(session.selected).skills)
        tapText("MIRAR")
        assertTrue("look" in session.plan.people.getValue(session.selected).skills)
        // Fase final: tocar el botón de lanzar marca el tutorial como completo y arranca la simulación.
        assertFalse(session.repo.tutorialDone)
        tapText("PONER EL PLAN EN MARCHA", substring = true)
        assertTrue(session.repo.tutorialDone)
        ui.waitUntil(5000) { session.simulation != null }
    }
}
