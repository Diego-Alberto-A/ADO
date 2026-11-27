package com.example.crosswalker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.Cream
import com.example.crosswalker.ui.theme.InkShadow
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

// Modal buttons defer their action until the paper card has finished leaving.
val LocalModalAction = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { action -> action() } }

enum class ScreenMotion { FORWARD, BACKWARD, FADE }

@Composable fun ScreenTransitions(s: Session, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    var introSeen by rememberSaveable { mutableStateOf(false) }
    val intro = remember { Animatable(if (introSeen || s.screen != "home") 1f else 0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(820, easing = FastOutSlowInEasing))
        introSeen = true
    }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    var backwards by remember { mutableStateOf(false) }
    var fading by remember { mutableStateOf(false) }

    DisposableEffect(s) {
        s.transition = { motion, action ->
            if (!s.transitioning) {
                s.transitioning = true
                scope.launch {
                    try {
                        backwards = motion == ScreenMotion.BACKWARD
                        fading = motion == ScreenMotion.FADE
                        progress.snapTo(0f)
                        // Freeze the last screen before changing any level or simulation state.
                        previous = layer.toImageBitmap()
                        action()
                        withFrameNanos { }
                        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
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
            .drawWithContent {
                if (intro.value >= 1f) {
                    drawContent()
                } else {
                    drawRect(InkShadow)
                    val radius = hypot(size.width, size.height) * .5f * intro.value
                    val opening = Path().apply {
                        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                    }
                    clipPath(opening) { this@drawWithContent.drawContent() }
                }
            }
            .then(if (s.transitioning) Modifier.clearAndSetSemantics {} else Modifier)
            .drawWithContent {
                val snapshot = previous
                if (!s.transitioning) {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                } else if (snapshot == null) {
                    drawContent()
                } else {
                    fun drawPrevious(alpha: Float = 1f) = drawImage(snapshot,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = alpha)
                    if (fading) {
                        drawContent()
                        drawPrevious(1f - progress.value)
                    } else {
                    val closing = backwards && s.screen != "home"
                    val fraction = if (closing) 1f - progress.value else progress.value
                    val radius = hypot(size.width, size.height) * .5f * fraction
                    val opening = Path().apply {
                        addOval(Rect(center.x - radius, center.y - radius,
                            center.x + radius, center.y + radius))
                    }
                    if (closing) {
                        drawContent()
                        if (radius > 0f) {
                            drawCircle(Cream, radius + 9.dp.toPx())
                            clipPath(opening) { drawPrevious() }
                        }
                    } else {
                        drawPrevious()
                        if (radius > 0f) {
                            drawCircle(Cream, radius + 9.dp.toPx())
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
