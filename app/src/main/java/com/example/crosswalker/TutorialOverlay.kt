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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
 * Qué paso está activo, y un registro persistente de "qué composable con qué id está dónde y qué
 * hace al tocarlo". El registro se mantiene actualizado SIEMPRE (no solo cuando su id coincide con
 * el paso activo): `onGloballyPositioned` solo se dispara cuando el layout cambia de verdad, nunca
 * solo porque cambió el paso activo, así que si solo registráramos "mientras coincide" perderíamos
 * la posición exacta en el momento en que un paso nuevo empieza. Cada composable usa su propio
 * token estable como parte de la clave, para que varios compartan el mismo id (los 3 cruces
 * comparten "crossing") sin pisarse entre sí.
 */
class TutorialController {
    var step: TutorialStep? by mutableStateOf(null)
    private val registry = mutableStateMapOf<Pair<String, Any>, Pair<Rect, () -> Unit>>()
    fun register(id: String, token: Any, rect: Rect, onTap: () -> Unit) { registry[id to token] = rect to onTap }
    fun unregister(token: Any) { registry.keys.filter { it.second == token }.forEach { registry.remove(it) } }
    fun entriesFor(id: String): List<Pair<Rect, () -> Unit>> = registry.filterKeys { it.first == id }.values.toList()
}

/**
 * Registra (y mantiene al día) la posición de este composable y su `onTap` real bajo `id`, sin
 * importar si `id` es el objetivo activo ahora mismo. `TutorialOverlay` decide, al leer
 * `controller.step`, cuáles de estos registros le importan en cada momento.
 */
fun Modifier.tutorialTarget(id: String, controller: TutorialController, onTap: () -> Unit): Modifier = composed {
    val token = remember { Any() }
    DisposableEffect(token) { onDispose { controller.unregister(token) } }
    this.onGloballyPositioned { coordinates ->
        controller.register(id, token, coordinates.boundsInRoot(), onTap)
    }
}

/**
 * Velo oscuro a pantalla completa con un agujero por cada rect registrado bajo el `spotlightId`
 * del paso activo, la manita parpadeando junto al primero de ellos, y toda la superficie
 * capturando el toque: solo el que cae dentro de algún rect de `tapId` dispara su `onTap` real.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    val step = controller.step
    // `boundsInRoot()` mide desde la raíz real de la ventana, pero este overlay vive dentro del
    // mismo `Box` con `systemBarsPadding()` que el resto de la pantalla: su propio origen local no
    // es (0,0) en esos términos. Se captura ese origen y se resta de cada rect para volver a
    // coordenadas locales a este overlay, que es el espacio en el que se dibuja y se detectan toques.
    var overlayOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val holes = step?.let { controller.entriesFor(it.spotlightId).map { (r, _) -> r.translate(-overlayOrigin) } } ?: emptyList()
    val taps = step?.let { controller.entriesFor(it.tapId).map { (r, onTap) -> r.translate(-overlayOrigin) to onTap } } ?: emptyList()
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val handAlpha by pulse.animateFloat(1f, .25f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "handAlpha")
    BoxWithConstraints(Modifier.fillMaxSize()
        .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft }
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = .55f))
            holes.forEach { r ->
                drawRoundRect(Color.Transparent, topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(8f, 8f), blendMode = BlendMode.Clear)
            }
        }
        .pointerInput(step) {
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
