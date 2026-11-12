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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Lo que un composable candidato registra: dónde está, qué hace al tocarlo y cómo se ve. */
class TutorialEntry(val rect: Rect, val onTap: () -> Unit, val layer: GraphicsLayer)

/**
 * Qué paso está activo, y un registro persistente de los composables candidatos. El registro se
 * mantiene actualizado SIEMPRE (no solo cuando su id coincide con el paso activo):
 * `onGloballyPositioned` solo se dispara cuando el layout cambia de verdad, nunca solo porque
 * cambió el paso activo. Cada composable usa su propio token estable como parte de la clave, para
 * que varios compartan el mismo id (los 3 cruces comparten "crossing") sin pisarse entre sí.
 */
class TutorialController {
    var step: TutorialStep? by mutableStateOf(null)
    private val registry = mutableStateMapOf<Pair<String, Any>, TutorialEntry>()
    fun register(id: String, token: Any, entry: TutorialEntry) { registry[id to token] = entry }
    fun unregister(token: Any) { registry.keys.filter { it.second == token }.forEach { registry.remove(it) } }
    fun entriesFor(id: String): List<TutorialEntry> = registry.filterKeys { it.first == id }.values.toList()
}

/**
 * Registra (y mantiene al día) este composable bajo `id`: su posición, su `onTap` real y una
 * `GraphicsLayer` en la que graba su propio dibujo en cada fotograma. Esa capa es lo que permite a
 * `TutorialOverlay` volver a pintar el elemento, píxel por píxel, por encima del velo oscuro — sin
 * recortar el velo ni duplicar el composable (y su estado). El elemento elevado deja de dibujarse debajo del velo para conservar su alfa original.
 */
fun Modifier.tutorialTarget(id: String, controller: TutorialController, onTap: () -> Unit): Modifier = composed {
    val token = remember { Any() }
    val layer = rememberGraphicsLayer()
    val currentTap by rememberUpdatedState(onTap)
    val step = controller.step
    val elevated = step != null && (id == step.spotlightId || id == "dialogue" || id == "pause")
    DisposableEffect(token) { onDispose { controller.unregister(token) } }
    this.onGloballyPositioned { coordinates ->
            controller.register(id, token, TutorialEntry(Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())), { currentTap() }, layer))
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            if (!elevated) drawLayer(layer)
        }
}

/**
 * Velo guiado del tutorial, en tres capas de abajo hacia arriba:
 *
 * 1. La pantalla real del juego (un hermano anterior de este composable) queda debajo, entera:
 *    se ve a través del velo, atenuada, y no recibe toques.
 * 2. El velo negro semitransparente cubriendo toda la pantalla, sin recortes.
 * 3. Encima del velo, y por eso resaltados por sí solos: una copia exacta de cada elemento que
 *    toca ver con claridad ahora — el objetivo señalado del paso y, siempre, la caja de diálogo —
 *    y la manita, colocada debajo del objetivo que de verdad hay que tocar.
 *
 * Toda la superficie captura el toque: solo el que cae dentro de algún rect de `tapId` dispara su
 * `onTap` real; cualquier otro no hace nada. Única excepción: PAUSA siempre es un toque válido,
 * sin importar el paso activo — igual que el botón de retroceso del sistema, que ni pasa por aquí.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    val step = controller.step ?: return
    // `boundsInRoot()` mide desde la raíz real de la ventana, pero este overlay vive dentro del
    // mismo `Box` con `systemBarsPadding()` que el resto de la pantalla: su propio origen local no
    // es (0,0) en esos términos. Se captura ese origen y se resta de cada rect para volver a
    // coordenadas locales a este overlay, que es el espacio en el que se dibuja y se detectan toques.
    var overlayHeight by remember { mutableStateOf(0f) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    fun local(e: TutorialEntry) = e.rect.translate(-overlayOrigin)
    val spotlight = step?.let { controller.entriesFor(it.spotlightId) } ?: emptyList()
    // El diálogo se mantiene legible siempre, sea o no el objetivo de este paso en particular.
    val elevated = (spotlight + controller.entriesFor("dialogue") + controller.entriesFor("pause")).distinct()
    // PAUSA sigue funcionando en todo momento, aunque el paso activo señale otra cosa.
    val stepTaps = step?.let { controller.entriesFor(it.tapId) } ?: emptyList()
    val taps = (controller.entriesFor("pause") + stepTaps).distinct()
    val currentTaps by rememberUpdatedState(taps)
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val handAlpha by pulse.animateFloat(1f, .25f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "handAlpha")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handSize = with(density) { 64.dp.toPx() }
    val hand = ImageBitmap.imageResource(R.drawable.pointer_hand)

    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot(); overlayHeight = it.size.height.toFloat() }) {
        Canvas(Modifier.fillMaxSize().pointerInput(step) {
            detectTapGestures { offset -> currentTaps.firstOrNull { local(it).contains(offset) }?.onTap?.invoke() }
        }) {
            // Capa 2: el velo completo.
            drawRect(Color.Black.copy(alpha = .55f))
            // Capa 3: cada elemento resaltado, pintado de nuevo encima del velo en su mismo sitio.
            elevated.forEach { e ->
                val at = local(e).topLeft
                translate(at.x, at.y) { drawLayer(e.layer) }
            }
        }
        // La manita, encima de todo, debajo del objetivo que de verdad hay que tocar (el de
        // `tapId`, que en los pasos puramente informativos es la caja de diálogo, no el elemento
        // señalado). Se usa `stepTaps`, no `taps`: PAUSA encabeza `taps` por ser la excepción global.
        val tapTarget = stepTaps.firstOrNull()?.let { local(it) }
        if (tapTarget != null) {
            Image(
                bitmap = hand,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset((tapTarget.center.x - handSize / 2).toInt(), (tapTarget.bottom - handSize * .2f).coerceIn(0f, (overlayHeight - handSize).coerceAtLeast(0f)).toInt()) }
                    .size(64.dp)
                    .alpha(handAlpha)
            )
        }
    }
}
