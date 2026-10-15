package com.example.crosswalker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

// Modal buttons defer their action until the paper card has finished leaving.
val LocalModalAction = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { action -> action() } }

enum class ScreenMotion { FORWARD, BACKWARD, WORLD_FORWARD, WORLD_BACKWARD }

@Composable fun ScreenTransitions(s: Session, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    var backwards by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }

    DisposableEffect(s) {
        s.transition = { motion, action ->
            if (!s.transitioning) {
                s.transitioning = true
                scope.launch {
                    try {
                        backwards = motion == ScreenMotion.BACKWARD || motion == ScreenMotion.WORLD_BACKWARD
                        sliding = motion == ScreenMotion.WORLD_FORWARD || motion == ScreenMotion.WORLD_BACKWARD
                        progress.snapTo(0f)
                        // Freeze the last screen before changing any level or simulation state.
                        previous = layer.toImageBitmap()
                        action()
                        withFrameNanos { }
                        progress.animateTo(1f, tween(if (sliding) 420 else 700, easing = FastOutSlowInEasing))
                    } finally {
                        previous = null
                        s.transitioning = false
                    }
                }
            }
        }
        onDispose { s.transition = { _, action -> action() }; s.transitioning = false }
    }
    BackHandler(enabled = s.transitioning) { }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .then(if (s.transitioning) Modifier.clearAndSetSemantics {} else Modifier)
            .drawWithContent {
                val snapshot = previous
                if (!s.transitioning) {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                } else if (snapshot == null) {
                    drawContent()
                } else {
                    fun drawPrevious() = drawImage(snapshot,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    if (sliding) {
                        // Higher worlds enter from the right; lower worlds enter from the left.
                        val direction = if (backwards) -1f else 1f
                        clipRect {
                            translate(left = -direction * size.width * progress.value) {
                                drawPrevious()
                            }
                            translate(left = direction * size.width * (1f - progress.value)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    } else {
                    val fraction = if (backwards) 1f - progress.value else progress.value
                    val radius = hypot(size.width, size.height) * .5f * fraction
                    val opening = Path().apply {
                        addOval(Rect(center.x - radius, center.y - radius,
                            center.x + radius, center.y + radius))
                    }
                    if (backwards) {
                        drawContent()
                        if (radius > 0f) {
                            drawCircle(Paper, radius + 9.dp.toPx())
                            clipPath(opening) { drawPrevious() }
                        }
                    } else {
                        drawPrevious()
                        if (radius > 0f) {
                            drawCircle(Paper, radius + 9.dp.toPx())
                            clipPath(opening) { this@drawWithContent.drawContent() }
                        }
                    }
                    }
                }
            }) { PaperGrain(); content() }
        if (s.transitioning) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {}
                .clearAndSetSemantics {})
        }
    }
}
