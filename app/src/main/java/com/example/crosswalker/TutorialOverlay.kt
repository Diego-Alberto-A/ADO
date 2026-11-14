package com.example.crosswalker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.crosswalker.ui.theme.Taxi

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
    val elevated = step != null && (id == step.spotlightId || id == "dialogue" || id == "pause" || id == "planning_panel")
    DisposableEffect(token) { onDispose { controller.unregister(token) } }
    this.onGloballyPositioned { coordinates ->
            controller.register(id, token, TutorialEntry(Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())), { currentTap() }, layer))
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            if (!elevated || id == "board") drawLayer(layer)
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
 *    y la manita, que simula un toque desde la esquina superior derecha.
 *
 * Toda la superficie captura el toque: solo el que cae dentro de algún rect de `tapId` dispara su
 * `onTap` real; cualquier otro no hace nada. PAUSA y la pestaña del plan siguen disponibles durante toda la guía.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    val step = controller.step ?: return
    // `positionInRoot()` mide desde la raíz real de la ventana, pero este overlay vive dentro del
    // mismo `Box` con `systemBarsPadding()` que el resto de la pantalla: su propio origen local no
    // es (0,0) en esos términos. Se captura ese origen y se resta de cada rect para volver a
    // coordenadas locales a este overlay, que es el espacio en el que se dibuja y se detectan toques.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    fun local(e: TutorialEntry) = e.rect.translate(-overlayOrigin)
    val spotlight = controller.entriesFor(step.spotlightId)
    // El diálogo se mantiene legible siempre, sea o no el objetivo de este paso en particular.
    val panelTabs = controller.entriesFor("planning_panel")
    val elevated = (spotlight + controller.entriesFor("dialogue") + controller.entriesFor("pause") + panelTabs).distinct()
    // La pestaña y PAUSA siguen funcionando durante todos los pasos.
    val stepTaps = controller.entriesFor(step.tapId)
    val helpTaps = if (step.tapId == "tool_look") controller.entriesFor("tool_help") else emptyList()
    val taps = (controller.entriesFor("pause") + panelTabs + helpTaps + stepTaps).distinct()
    val currentTaps by rememberUpdatedState(taps)
    val marked = when (step.spotlightId) {
        "board" -> panelTabs
        "dialogue" -> emptyList()
        else -> spotlight
    }
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val emphasis = pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Restart),
        label = "spotlightEmphasis"
    )
    val tapTarget = stepTaps.firstOrNull()
    val clickMotion = rememberClickMotion()
    val hand = ImageBitmap.imageResource(R.drawable.pointer_hand)

    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }) {
        Canvas(Modifier.fillMaxSize().pointerInput(step) {
            detectTapGestures { offset -> currentTaps.firstOrNull { local(it).contains(offset) }?.onTap?.invoke() }
        }) {
            // Capa 2: el velo completo.
            drawRect(Color.Black.copy(alpha = .40f))
            // Capa 3: cada elemento resaltado, pintado de nuevo encima del velo en su mismo sitio.
            elevated.forEach { e ->
                val at = local(e).topLeft
                if (step.spotlightId == "board" && e in spotlight) {
                    val panelTop = panelTabs.minOfOrNull { local(it).top } ?: size.height
                    clipRect(bottom = panelTop) { translate(at.x, at.y) { drawLayer(e.layer) } }
                } else {
                    translate(at.x, at.y) { drawLayer(e.layer) }
                }
            }
            marked.forEach { entry ->
                val target = local(entry)
                val padding = (3.dp + 7.dp * emphasis.value).toPx()
                drawRoundRect(
                    color = Taxi.light.copy(alpha = 1f - .54f * emphasis.value),
                    topLeft = Offset(target.left - padding, target.top - padding),
                    size = Size(target.width + 2 * padding, target.height + 2 * padding),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(3.dp.toPx())
                )
            }
            tapTarget?.let {
                val target = local(it)
                val inset = 48.dp.toPx()
                val contact = Offset(
                    (target.right - minOf(20.dp.toPx(), target.width / 2))
                        .coerceIn(0f, (size.width - inset).coerceAtLeast(0f)),
                    (target.top + minOf(14.dp.toPx(), target.height / 2))
                        .coerceIn(inset.coerceAtMost(size.height), size.height)
                )
                drawClickHand(hand, contact, 42.dp.toPx(), clickMotion.value)
            }
        }
    }
}

@Composable
internal fun rememberClickMotion(): State<Float> {
    val transition = rememberInfiniteTransition(label = "pointerClick")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 960
            0f at 180
            1f at 400
            1f at 470
            0f at 730
        }),
        label = "pointerPress"
    )
}

internal fun DrawScope.drawClickHand(hand: ImageBitmap, contact: Offset, width: Float, press: Float) {
    val retreat = width * .28f * (1f - press)
    val tip = contact + Offset(retreat, -retreat)
    val pixels = width.roundToInt().coerceAtLeast(1)
    // Rotate around the fingertip so it lands on the same spot every time.
    rotate(degrees = 225f, pivot = tip) {
        drawImage(
            image = hand,
            dstOffset = IntOffset((tip.x - width * .36f).roundToInt(), (tip.y - width * .04f).roundToInt()),
            dstSize = IntSize(pixels, pixels),
            filterQuality = FilterQuality.None
        )
    }
}

/** Highlights a new mechanic without intercepting touches on the planning controls. */
internal fun Modifier.mechanicHint(active: Boolean): Modifier {
    if (!active) return this
    return composed {
        val pulse = rememberInfiniteTransition(label = "mechanicHint")
        val phase = pulse.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
            label = "mechanicHintEmphasis"
        )
        drawWithContent {
            drawContent()
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = Taxi.dark.copy(alpha = .55f + .45f * phase.value),
                topLeft = Offset(inset, inset),
                size = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(5.dp.toPx()),
                style = Stroke((2.dp + 2.dp * phase.value).toPx())
            )
        }
    }
}
