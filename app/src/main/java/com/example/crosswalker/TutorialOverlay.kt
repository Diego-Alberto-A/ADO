package com.example.crosswalker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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

/**
 * Velo oscuro a pantalla completa con un agujero por cada rect de `controller.spotlightRects`, la
 * manita parpadeando junto al primero de ellos, y toda la superficie capturando el toque: solo el
 * que cae dentro de algún `tapTargets` dispara su `onTap` real.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    controller.beginFrame()
    val holes = controller.spotlightRects
    val taps = controller.tapTargets
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val handAlpha by pulse.animateFloat(1f, .25f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "handAlpha")
    BoxWithConstraints(Modifier.fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = .55f))
            holes.forEach { r ->
                drawRoundRect(Color.Transparent, topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(8f, 8f), blendMode = BlendMode.Clear)
            }
        }
        .pointerInput(controller.step) {
            detectTapGestures { offset -> taps.firstOrNull { (rect, _) -> rect.contains(offset) }?.second?.invoke() }
        }
    ) {
        // `constraints.maxHeight` viene en píxeles, igual que los Rect de `boundsInRoot()`:
        // comparar ambos directamente evita cualquier conversión dp/px aquí.
        val first = holes.firstOrNull()
        if (first != null) {
            val halfHeightPx = constraints.maxHeight / 2f
            val pointsDown = first.top > halfHeightPx
            val xPx = first.center.x - 24f
            val yPx = if (pointsDown) first.top - 56f else first.bottom + 8f
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pointer_hand),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(xPx.toInt(), yPx.toInt()) }
                    .size(24.dp)
                    .alpha(handAlpha)
            )
        }
    }
}
