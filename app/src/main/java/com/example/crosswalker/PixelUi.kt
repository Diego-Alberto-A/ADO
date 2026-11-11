package com.example.crosswalker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crosswalker.ui.theme.*

/**
 * One art pixel, in layout units. Every border, shadow and offset is a whole
 * number of these so nothing lands on a half pixel.
 */
val PX = 1.dp

/** Pixel art never eases through fractional positions; 80–200 ms, overshooting. */
val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun aaOff() = Paint().apply { isAntiAlias = false; isFilterBitmap = false; isDither = false }

/**
 * A pixel-art rounded rectangle: the corner is a two-step stair, the way a
 * 3–4 px bubble corner is drawn by hand.
 */
private fun pixelPath(w: Float, h: Float, r: Float): android.graphics.Path {
    val u = (r / 3f).coerceAtLeast(1f)
    val c = u * 3f
    return android.graphics.Path().apply {
        moveTo(c, 0f); lineTo(w - c, 0f)
        lineTo(w - u, 0f); lineTo(w - u, u); lineTo(w, u); lineTo(w, c)
        lineTo(w, h - c)
        lineTo(w, h - u); lineTo(w - u, h - u); lineTo(w - u, h); lineTo(w - c, h)
        lineTo(c, h)
        lineTo(u, h); lineTo(u, h - u); lineTo(0f, h - u); lineTo(0f, h - c)
        lineTo(0f, c)
        lineTo(0f, u); lineTo(u, u); lineTo(u, 0f); lineTo(c, 0f)
        close()
    }
}

private fun DrawScope.drawPixelPanel(
    size: Size, fill: Color, outline: Color, shadow: Color?,
    highlight: Color?, shade: Color?, innerBorder: Color?, cornerPx: Float, outlinePx: Float
) {
    val canvas = drawContext.canvas.nativeCanvas
    val p = aaOff()
    val w = size.width
    val h = size.height
    if (w <= outlinePx * 2 || h <= outlinePx * 2) return

    shadow?.let {
        p.color = it.toArgb()
        canvas.save(); canvas.translate(outlinePx, outlinePx)
        canvas.drawPath(pixelPath(w, h, cornerPx), p); canvas.restore()
    }
    p.color = outline.toArgb()
    canvas.drawPath(pixelPath(w, h, cornerPx), p)

    val iw = w - outlinePx * 2
    val ih = h - outlinePx * 2
    p.color = fill.toArgb()
    canvas.save(); canvas.translate(outlinePx, outlinePx)
    canvas.drawPath(pixelPath(iw, ih, cornerPx), p)
    // Light from above: one lit row on top, one shaded row at the bottom.
    highlight?.let { p.color = it.toArgb(); canvas.drawRect(cornerPx, 0f, iw - cornerPx, outlinePx, p) }
    shade?.let { p.color = it.toArgb(); canvas.drawRect(cornerPx, ih - outlinePx, iw - cornerPx, ih, p) }
    innerBorder?.let {
        p.color = it.toArgb()
        p.style = Paint.Style.STROKE; p.strokeWidth = outlinePx
        canvas.drawPath(pixelPath(iw - outlinePx, ih - outlinePx, cornerPx), p)
        p.style = Paint.Style.FILL
    }
    canvas.restore()
}

/** Light panel: cream face, ink outline, solid shadow, lit top edge. */
fun Modifier.pixelPanel(
    fill: Color = Cream,
    outline: Color = Ink,
    shadow: Color? = InkShadow,
    highlight: Color? = CreamLight,
    shade: Color? = CreamDark,
    innerBorder: Color? = null,
    corner: Dp = 3.dp
) = drawBehind {
    val px = PX.toPx()
    drawPixelPanel(size, fill, outline, shadow, highlight, shade, innerBorder,
        corner.toPx(), px)
}

/** Dark panel for the HUD: asphalt face with a lighter inner edge. */
fun Modifier.pixelPanelDark(corner: Dp = 3.dp) =
    pixelPanel(fill = Asphalt, highlight = AsphaltEdge, shade = InkShadow,
        innerBorder = null, corner = corner)

/** Sunken slot: the face sits a pixel below the frame. */
fun Modifier.pixelSlot(corner: Dp = 2.dp) =
    pixelPanel(fill = CreamDark, highlight = null, shade = CreamLight,
        shadow = null, corner = corner)

/**
 * A 2×2 checkerboard veil. Pixel art dims things by dropping every other
 * pixel, never by fading alpha.
 */
@Composable fun rememberDitherShader(color: Color): BitmapShader = remember(color) {
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val argb = color.toArgb()
    bitmap.setPixel(0, 0, argb); bitmap.setPixel(1, 1, argb)
    bitmap.setPixel(1, 0, 0); bitmap.setPixel(0, 1, 0)
    BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

fun Modifier.ditherVeil(shader: BitmapShader, scale: Float = 2f): Modifier = this.then(
    object : DrawModifier {
        override fun ContentDrawScope.draw() {
            drawContent()
            val canvas = drawContext.canvas.nativeCanvas
            val p = aaOff().apply {
                this.shader = shader
                shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale) })
            }
            canvas.drawRect(0f, 0f, size.width, size.height, p)
        }
    }
)

/**
 * A button with a 2 px base of its own shade: the face rests on top of it and
 * drops onto it when pressed, so the press reads as a physical squash.
 */
@Composable fun PixelButton(
    text: String,
    accent: Accent = Tree,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val modalAction = LocalModalAction.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face = if (enabled) accent.base else KerbLight
    val base = if (enabled) accent.dark else Kerb
    val lift = if (pressed || !enabled) 0.dp else 2.dp
    val onFace = if (enabled) textOn(accent.base) else Ink.copy(alpha = .55f)

    Box(modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        // The base is the full-height slab; the face rides 2 px above it.
        Box(Modifier.matchParentSize().padding(top = 2.dp).pixelPanel(
            fill = base, highlight = null, shade = null, shadow = InkShadow, corner = 3.dp))
        Box(
            Modifier.matchParentSize().padding(bottom = 2.dp).absoluteOffset(y = -lift + 2.dp)
                .graphicsLayer {
                    scaleY = if (pressed && enabled) .96f else 1f
                    transformOrigin = TransformOrigin(.5f, 1f)
                }
                .pixelPanel(fill = face, highlight = accent.light.takeIf { enabled },
                    shade = accent.dark.takeIf { enabled }, shadow = null, corner = 3.dp)
                .clickable(interactionSource = interaction, indication = LocalIndication.current,
                    enabled = enabled, role = Role.Button, onClickLabel = onClickLabel,
                    onClick = { modalAction(onClick) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leading?.invoke()
                Text(text, color = onFace, style = TitleSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Cream faces take ink; the saturated accents take the light cream. */
fun textOn(face: Color): Color =
    if (face == Cream || face == CreamLight || face == CreamDark || face == Taxi.base) Ink else CreamLight

/** A progress/budget bar: asphalt channel, accent fill with a lit top row. */
@Composable fun PixelBar(progress: Float, accent: Accent, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier.height(10.dp).pixelPanel(fill = Asphalt, highlight = null,
        shade = null, shadow = null, corner = 2.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(clamped).padding(PX)
            .pixelPanel(fill = accent.base, highlight = accent.light, shade = accent.dark,
                shadow = null, corner = 2.dp))
    }
}

/** A cursor that never sits still: one pixel of travel, always visible. */
@Composable fun FocusCursor(color: Color = Taxi.base, glyph: String = "\u25B6") {
    val transition = rememberInfiniteTransition(label = "cursor")
    val nudge by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(520, easing = EaseOutBack), RepeatMode.Reverse), label = "nudge")
    Text(glyph, color = color, style = HudStrong, modifier = Modifier.absoluteOffset(x = PX * nudge))
}

/** Blinking \u25BC: there is more to read. */
@Composable fun MoreTextArrow(color: Color = Ink) {
    val transition = rememberInfiniteTransition(label = "more")
    val phase by transition.animateFloat(0f, 2f,
        infiniteRepeatable(tween(900, easing = { it }), RepeatMode.Restart), label = "blink")
    val on = phase < 1.2f
    Text("\u25BC", color = if (on) color else Color.Transparent, style = HudStrong,
        modifier = Modifier.absoluteOffset(y = if (on) 0.dp else PX))
}

/**
 * A compact square button for the HUD that floats over the board: cream face
 * on its own 2 px base, so it stays legible over grass, asphalt or brick.
 */
@Composable fun HudButton(glyph: String, contentDescription: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift = if (pressed) 0.dp else 2.dp
    Box(Modifier.size(48.dp)) {
        Box(Modifier.matchParentSize().padding(top = 2.dp)
            .pixelPanel(fill = CreamDark, highlight = null, shade = null, corner = 3.dp))
        Box(Modifier.matchParentSize().padding(bottom = 2.dp).absoluteOffset(y = -lift + 2.dp)
            .pixelPanel(fill = Cream, highlight = CreamLight, shade = CreamDark,
                shadow = null, corner = 3.dp)
            .clickable(interactionSource = interaction, indication = LocalIndication.current,
                role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
            contentAlignment = Alignment.Center) {
            Text(glyph, color = Ink, fontSize = 22.sp)
        }
    }
}

/** One token: a gold coin while you still hold it, an empty socket once spent. */
@Composable private fun TokenDot(held: Boolean) {
    Box(Modifier.size(11.dp).pixelPanel(
        fill = if (held) Orange.base else InkShadow,
        highlight = if (held) Orange.light else null,
        shade = if (held) Orange.dark else AsphaltEdge,
        shadow = null, corner = 3.dp))
}

/**
 * The budget, read at a glance: one coin per token, lit for the ones still in
 * hand. No arithmetic needed to see what is left.
 */
@Composable fun TokenCounter(remaining: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.pixelPanelDark(corner = 3.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Te quedan $remaining de $total fichas"
            }) {
            repeat(total) { i -> TokenDot(i < remaining) }
        }
        Spacer(Modifier.height(3.dp))
        Text("FICHAS", style = HudStrong, color = CreamLight, letterSpacing = 2.sp)
    }
}
