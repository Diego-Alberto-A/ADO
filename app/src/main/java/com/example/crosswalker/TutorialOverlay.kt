package com.example.crosswalker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Qué paso está activo y qué composables reportaron su posición/acción para él en la
 * recomposición actual. `beginFrame()` se llama una vez por recomposición, antes de que los
 * composables hijos vuelvan a reportar: así nunca se acumulan rects de un paso anterior.
 */
class TutorialController {
    var step: TutorialStep? by mutableStateOf(null)
    // `by mutableStateOf` en las tres: `TutorialOverlay` lee `spotlightRects`/`tapTargets` para
    // decidir qué dibujar, y los hijos los escriben desde `onGloballyPositioned` (que corre en la
    // fase de layout, después de la composición) — si fueran `var` planas sin estado de Compose,
    // escribirlas ahí nunca dispararía una recomposición del overlay.
    var spotlightRects: List<Rect> by mutableStateOf(emptyList())
    var tapTargets: List<Pair<Rect, () -> Unit>> by mutableStateOf(emptyList())
    fun beginFrame() { spotlightRects = emptyList(); tapTargets = emptyList() }
}

/**
 * Mientras `id` coincide con el `spotlightId`/`tapId` del paso activo, este composable reporta su
 * rect (para el agujero del velo y/o para aceptar el toque) y entrega `onTap` — su propio
 * `onClick` real — como la acción que el overlay puede disparar. Si el composable no coincide con
 * el paso activo, no hace nada: el coste fuera del tutorial es cero.
 */
fun Modifier.tutorialTarget(id: String, controller: TutorialController, onTap: () -> Unit): Modifier =
    this.onGloballyPositioned { coordinates ->
        val step = controller.step ?: return@onGloballyPositioned
        val rect = coordinates.boundsInRoot()
        if (step.spotlightId == id) controller.spotlightRects = controller.spotlightRects + rect
        if (step.tapId == id) controller.tapTargets = controller.tapTargets + (rect to onTap)
    }
