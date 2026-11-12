package com.example.crosswalker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Velo guiado del tutorial, en capas explícitas de abajo hacia arriba:
 *
 * 1. La pantalla real del juego (un hermano anterior de este composable, no algo que dibuje aquí)
 *    queda siempre debajo, oscurecida por el velo.
 * 2. El velo oscuro semitransparente, con un agujero recortado (totalmente nítido, sin oscurecer)
 *    por cada elemento que debe verse con claridad ahora: el objetivo señalado de este paso Y,
 *    siempre, la caja de diálogo — así el jugador nunca pierde de vista lo que tiene que hacer.
 * 3. Un recuadro de borde resaltando el objetivo señalado de este paso (no la caja de diálogo,
 *    salvo que ella misma sea el objetivo), dibujado encima del velo.
 * 4. La manita, siempre encima del velo y colocada por debajo del recuadro que de verdad hay que
 *    tocar para avanzar (el objetivo del toque, no necesariamente el mismo que el señalado).
 *
 * Toda la superficie captura el toque: solo el que cae dentro de algún rect de `tapId` dispara su
 * `onTap` real; cualquier otro, incluido uno dentro del agujero "siempre visible" del diálogo
 * cuando ese paso no lo pide, no hace nada.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    val step = controller.step
    // `boundsInRoot()` mide desde la raíz real de la ventana, pero este overlay vive dentro del
    // mismo `Box` con `systemBarsPadding()` que el resto de la pantalla: su propio origen local no
    // es (0,0) en esos términos. Se captura ese origen y se resta de cada rect para volver a
    // coordenadas locales a este overlay, que es el espacio en el que se dibuja y se detectan toques.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val spotlightHoles = step?.let { controller.entriesFor(it.spotlightId).map { (r, _) -> r.translate(-overlayOrigin) } } ?: emptyList()
    // El diálogo se mantiene legible siempre, sea o no el objetivo de este paso en particular.
    val dialogueHoles = controller.entriesFor("dialogue").map { (r, _) -> r.translate(-overlayOrigin) }
    val visibleHoles = (spotlightHoles + dialogueHoles).distinct()
    val taps = step?.let { controller.entriesFor(it.tapId).map { (r, onTap) -> r.translate(-overlayOrigin) to onTap } } ?: emptyList()
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val handAlpha by pulse.animateFloat(1f, .25f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "handAlpha")
    val hand = ImageBitmap.imageResource(R.drawable.pointer_hand)

    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft }) {
        // Capas 1-3: el velo, sus agujeros y el recuadro de borde, todos en un mismo Canvas — cada
        // llamada posterior dentro de él compone por encima de la anterior, así que el borde
        // (dibujado al final) queda por encima del velo sin necesidad de un composable aparte.
        Canvas(Modifier.fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .pointerInput(step) {
                detectTapGestures { offset -> taps.firstOrNull { (rect, _) -> rect.contains(offset) }?.second?.invoke() }
            }
        ) {
            drawRect(Color.Black.copy(alpha = .55f))
            visibleHoles.forEach { r ->
                drawRoundRect(Color.Transparent, topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(8f, 8f), blendMode = BlendMode.Clear)
            }
            spotlightHoles.forEach { r ->
                drawRoundRect(Color(0xFFFFE07A), topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 4f))
            }
        }
        // Capa 4: la manita, siempre encima del velo y de su recuadro, colocada debajo del
        // objetivo que de verdad hay que tocar (el de `tapId`, que en los pasos puramente
        // informativos es la caja de diálogo, no el elemento señalado).
        val tapTarget = taps.firstOrNull()?.first
        if (tapTarget != null) {
            Image(
                bitmap = hand,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset((tapTarget.center.x - 24f).toInt(), (tapTarget.bottom + 8f).toInt()) }
                    .size(24.dp)
                    .alpha(handAlpha)
            )
        }
    }
}
