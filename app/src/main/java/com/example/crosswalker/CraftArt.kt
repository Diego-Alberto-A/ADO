package com.example.crosswalker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.*
import com.example.crosswalker.game.Walker

/** Pixel art is never smoothed: Compose scales these sprites nearest-neighbour. */
@Composable fun PixelImage(id: Int, contentDescription: String?, modifier: Modifier = Modifier,
                           alpha: Float = 1f, contentScale: ContentScale = ContentScale.Fit) {
    Image(bitmap = ImageBitmap.imageResource(id), contentDescription = contentDescription,
        modifier = modifier, contentScale = contentScale, alpha = alpha,
        filterQuality = FilterQuality.None)
}

/** Two bolts, the way a plate is fixed to its post. */
private fun Modifier.signBolts(color: Color) = drawBehind {
    val px = PX.toPx()
    val d = px * 3
    val y = size.height / 2 - px
    listOf(d, size.width - d * 2).forEach { x ->
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x, y),
            size = androidx.compose.ui.geometry.Size(d, d))
    }
}

/** A tab shaped like a street sign; the live one is Taxi yellow. */
@Composable fun CraftChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val face = if (selected) Taxi.base else Cream
    Box(Modifier.padding(vertical = 4.dp).heightIn(min = 42.dp)
        .pixelPanel(fill = face,
            highlight = if (selected) Taxi.light else CreamLight,
            shade = if (selected) Taxi.dark else CreamDark)
        .signBolts(if (selected) Taxi.dark else CreamDark)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Ink
        ) { androidx.compose.material3.ProvideTextStyle(HudLabel) { label() } }
    }
}

internal fun Walker.hasHeadphonesOn(skills: Set<String>): Boolean =
    kind == "headphones" && "headphones_off" !in skills

internal fun headphonesFit(width: Float, height: Float, walking: Boolean = false): Rect {
    val top = height * if (walking) .025f else .06f
    return Rect(width * .06f, top, width * .94f, top + height * .36f)
}

internal const val CHILD_ART_SCALE = .84f

/** Worn on the back in the walking frames, with the straps visible from the front. */
internal fun drawSchoolBackpack(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    bounds: android.graphics.RectF,
    fromBack: Boolean
) {
    canvas.save()
    canvas.translate(bounds.left, bounds.top)
    canvas.scale(bounds.width() / 12f, bounds.height() / 14f)
    fun pixels(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        paint.color = color
        canvas.drawRect(left, top, right, bottom, paint)
    }
    val outline = 0xFF2B2540.toInt()
    val yellow = 0xFFFFC23D.toInt()
    val light = 0xFFFFE07A.toInt()
    if (fromBack) {
        pixels(5f, 8f, 7f, 9f, outline)
        pixels(3f, 9f, 9f, 13f, outline)
        pixels(4f, 9f, 8f, 12f, yellow)
        pixels(4f, 10.5f, 8f, 12f, light)
        pixels(5.5f, 11f, 6.5f, 12f, outline)
    } else {
        pixels(1.5f, 8.5f, 3.5f, 12.5f, outline)
        pixels(8.5f, 8.5f, 10.5f, 12.5f, outline)
        pixels(2f, 8.5f, 3f, 12f, yellow)
        pixels(9f, 8.5f, 10f, 12f, yellow)
        pixels(1f, 10f, 2f, 12f, light)
        pixels(10f, 10f, 11f, 12f, light)
    }
    canvas.restore()
}

/** School crossing pictogram with a small walker beside a taller companion. */
internal fun drawSchoolCrossingBadge(canvas: android.graphics.Canvas, paint: android.graphics.Paint,
                                    left: Float, top: Float, size: Float) {
    canvas.save()
    canvas.translate(left, top)
    canvas.scale(size / 24f, size / 24f)
    val diamond = android.graphics.Path().apply {
        moveTo(12f, 0f)
        lineTo(24f, 12f)
        lineTo(12f, 24f)
        lineTo(0f, 12f)
        close()
    }
    paint.color = Ink.toArgb()
    canvas.drawPath(diamond, paint)
    canvas.save()
    canvas.scale(.83f, .83f, 12f, 12f)
    paint.color = Taxi.light.toArgb()
    canvas.drawPath(diamond, paint)
    canvas.restore()

    paint.color = Ink.toArgb()
    canvas.drawRect(8f, 6f, 11f, 9f, paint)
    canvas.drawRect(7f, 9f, 11f, 14f, paint)
    canvas.drawRect(6f, 14f, 8f, 17f, paint)
    canvas.drawRect(9f, 14f, 11f, 18f, paint)
    canvas.drawRect(14f, 9f, 17f, 12f, paint)
    canvas.drawRect(14f, 12f, 17f, 16f, paint)
    canvas.drawRect(13f, 15f, 15f, 18f, paint)
    canvas.drawRect(16f, 15f, 18f, 17f, paint)
    canvas.drawRect(11f, 11f, 13f, 13f, paint)
    canvas.drawRect(12f, 12f, 14f, 14f, paint)
    canvas.restore()
}

@Composable
private fun SchoolCrossingBadge(size: Dp) {
    val paint = androidx.compose.runtime.remember { android.graphics.Paint() }
    Canvas(Modifier.size(size)) {
        drawIntoCanvas { drawSchoolCrossingBadge(it.nativeCanvas, paint, 0f, 0f, this.size.width) }
    }
}

@Composable fun WalkerArtwork(
    walker: Walker,
    size: Dp = 30.dp,
    skills: Set<String> = emptySet(),
    child: Boolean = false
) {
    val resource = when (walker.sprite) {
        "walker_coral" -> R.drawable.walker_coral
        "walker_green" -> R.drawable.walker_green
        "walker_chair" -> R.drawable.walker_chair
        "walker_slate" -> R.drawable.walker_slate
        "walker_rust" -> R.drawable.walker_rust
        else -> R.drawable.walker_blue
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size)) {
            val artSize = if (child) size * CHILD_ART_SCALE else size
            Box(Modifier.align(Alignment.BottomCenter).size(artSize)) {
                PixelImage(resource, if (child) "${walker.name}, niña" else walker.name, Modifier.matchParentSize())
                if (child) {
                    val bitmap = ImageBitmap.imageResource(resource)
                    val paint = androidx.compose.runtime.remember { android.graphics.Paint() }
                    Canvas(Modifier.matchParentSize()) {
                        val scale = minOf(this.size.width / bitmap.width, this.size.height / bitmap.height)
                        val width = bitmap.width * scale
                        val height = bitmap.height * scale
                        val left = (this.size.width - width) / 2f
                        val top = (this.size.height - height) / 2f
                        drawIntoCanvas {
                            drawSchoolBackpack(it.nativeCanvas, paint,
                                android.graphics.RectF(left, top, left + width, top + height), fromBack = false)
                        }
                    }
                }
            }
            // A pedestrian glued to a phone wears it as a badge wherever the UI names them.
            if (walker.kind == "phone") PixelImage(R.drawable.phone, "con celular",
                Modifier.size(size * .45f).align(Alignment.TopEnd))
            if (walker.hasHeadphonesOn(skills)) {
                val bitmap = ImageBitmap.imageResource(resource)
                val scale = size.value / maxOf(bitmap.width, bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val fit = headphonesFit(width, height)
                PixelImage(
                    R.drawable.headphones, "con audífonos",
                    Modifier.align(Alignment.TopCenter).offset(y = ((size.value - height) / 2 + fit.top).dp).size(fit.width.dp, fit.height.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        if (child) SchoolCrossingBadge(maxOf(16.dp, size * .5f))
    }
}

@Composable private fun SlowMotionArtwork() {
    Canvas(Modifier.size(30.dp)) {
        val px = size.width / 30f
        val violet = Color(0xFF9333EA)
        val center = Offset(13f * px, 17f * px)
        drawOval(violet.copy(alpha = .22f), topLeft = Offset(1f * px, 8f * px), size = Size(25f * px, 15f * px))
        drawRect(Color(0xFF7E22CE), Offset(3f * px, 15f * px), Size(19f * px, 7f * px))
        drawRect(Color(0xFFD8B4FE), Offset(8f * px, 11f * px), Size(9f * px, 5f * px))
        drawCircle(Color(0xFF35204A), 2.5f * px, Offset(8f * px, 23f * px))
        drawCircle(Color(0xFF35204A), 2.5f * px, Offset(19f * px, 23f * px))
        val clock = Offset(24f * px, 8f * px)
        drawCircle(Color(0xFFF3E8FF), 6f * px, clock)
        drawCircle(violet, 6f * px, clock, style = androidx.compose.ui.graphics.drawscope.Stroke(1.7f * px))
        drawLine(violet, clock, Offset(24f * px, 4f * px), 1.5f * px)
        drawLine(violet, clock, Offset(27f * px, 9f * px), 1.5f * px)
    }
}

@Composable fun ToolArtwork(id: String) {
    if (id == "speed_zone") { SlowMotionArtwork(); return }
    val resource = when (id) {
        "look" -> R.drawable.icon_look
        "visible" -> R.drawable.icon_reflector
        "lighting" -> R.drawable.icon_streetlamp
        "car_lights" -> R.drawable.icon_headlights
        "escort" -> R.drawable.icon_escort
        "phone_away" -> R.drawable.icon_phone_away
        "headphones_off" -> R.drawable.icon_headphones_off
        "crosswalk" -> R.drawable.icon_crosswalk
        else -> R.drawable.icon_crosswalk
    }
    PixelImage(resource, null, Modifier.size(30.dp))
}

/** Earned stars sit in their sunken slot; pending ones are dithered out. */
@Composable fun PaperStars(count: Int, size: Dp = 18.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val dither = rememberDitherShader(Cream)
        repeat(3) { i ->
            val earned = i < count
            val slot = Modifier.size(size)
            PixelImage(R.drawable.star,
                if (earned) "Estrella obtenida" else "Estrella pendiente",
                if (earned) slot else slot.ditherVeil(dither))
        }
    }
}
