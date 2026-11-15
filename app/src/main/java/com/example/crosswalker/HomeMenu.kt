package com.example.crosswalker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import com.example.crosswalker.ui.theme.*
import kotlin.math.roundToInt


@Composable
private fun HomeButton(
    text: String,
    icon: String,
    accent: Accent,
    iconColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: () -> Unit
) {
    PixelButton(
        text = text,
        accent = accent,
        enabled = enabled,
        modifier = modifier,
        onClickLabel = onClickLabel,
        keepColorsWhenDisabled = true,
        leading = {
            Box(
                Modifier.size(28.dp)
                    .pixelPanel(fill = iconColor, highlight = CreamLight, shade = null, shadow = null),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Ink, style = TitleSmall.copy(fontSize = 18.sp, lineHeight = 20.sp))
            }
        },
        onClick = onClick
    )
}

@Composable fun Home(s: Session, enabled: Boolean, click: () -> Unit) {
    val play = { if (enabled) { click(); s.navigate("map") } }
    Box(Modifier.fillMaxSize().background(Color(0xFF327B69))) {
        HomeStreet(Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to InkShadow.copy(alpha = .30f),
            .28f to Color.Transparent,
            .68f to Color.Transparent,
            1f to InkShadow.copy(alpha = .84f)
        )))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = enabled)
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .then(if (enabled) Modifier else Modifier.clearAndSetSemantics {}),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeLogo()
                Spacer(Modifier.weight(1f).heightIn(min = 125.dp))
                Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeButton(
                        "JUGAR", "▶", Taxi, CreamLight, enabled,
                        modifier = Modifier.heightIn(min = 62.dp),
                        onClickLabel = "Elegir nivel",
                        onClick = play
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeButton("AJUSTES", "⚙", Neutral, Sky.light, enabled, Modifier.weight(1f)) {
                            click()
                            s.dialog = "settings"
                        }
                        HomeButton("LOGROS", "★", Neutral, Taxi.light, enabled, Modifier.weight(1f)) {
                            click()
                            s.dialog = "achievements"
                        }
                    }
                    HomeButton("REINICIAR PROGRESO", "↻", Neutral, Brick.light, enabled) {
                        click()
                        s.dialog = "resetProgress"
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLogo() {
    BoxWithConstraints(Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
        val letterSize = (maxWidth.value * .205f).coerceAtMost(66f).sp
        Column(
            Modifier.fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "Crosswalker" }
                .pixelPanel(fill = Ink, highlight = AsphaltEdge, shade = InkShadow,
                    innerBorder = Asphalt, corner = 8.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZebraMotif(Modifier.weight(1f).height(22.dp))
                Text("CROSS", color = CreamLight, style = TitleText.copy(
                    fontSize = letterSize, lineHeight = letterSize * .80f), textAlign = TextAlign.Center, maxLines = 1)
                ZebraMotif(Modifier.weight(1f).height(22.dp))
            }
            Text("WALKER", color = Taxi.light, style = TitleText.copy(
                fontSize = letterSize, lineHeight = letterSize * .84f), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun ZebraMotif(modifier: Modifier) {
    Canvas(modifier) {
        val width = minOf(24.dp.toPx(), size.width)
        val left = (size.width - width) / 2f
        repeat(3) { stripe ->
            drawRect(CreamLight, Offset(left, stripe * 8.dp.toPx()), Size(width, 4.dp.toPx()))
        }
    }
}

private val homeCrossings = listOf(68f, 180f, 288f)
private data class HomeWalker(val pose: Int, val steps: List<Int>, val delay: Float, val approachOffset: Float)
private val homeWalkers = listOf(
    HomeWalker(R.drawable.walker_coral, listOf(R.drawable.walker_coral_w0, R.drawable.walker_coral_w1, R.drawable.walker_coral_w2), .25f, -25f),
    HomeWalker(R.drawable.walker_green, listOf(R.drawable.walker_green_w0, R.drawable.walker_green_w1, R.drawable.walker_green_w2), .8f, 16f),
    HomeWalker(R.drawable.walker_rust, listOf(R.drawable.walker_rust_w0, R.drawable.walker_rust_w1, R.drawable.walker_rust_w2), 1.35f, 25f)
)

internal enum class StreetScene { BARRIO, CAMPUS_RAIN, CITY_NIGHT }

@Composable
internal fun HomeStreet(modifier: Modifier, scene: StreetScene = StreetScene.BARRIO) {
    val resources = LocalResources.current
    val art = remember(resources) {
        listOf(
            R.drawable.ground_grass, R.drawable.ground_walk, R.drawable.ground_road,
            R.drawable.house, R.drawable.tree, R.drawable.tree_autumn,
            R.drawable.decor_fence_barrio, R.drawable.decor_mailbox_barrio,
            R.drawable.car_coral, R.drawable.car_yellow,
            R.drawable.decor_building_campus, R.drawable.decor_bikerack_campus,
            R.drawable.decor_building_city, R.drawable.decor_busstop_city
        ).plus(homeWalkers.flatMap { listOf(it.pose) + it.steps }).associateWith { ImageBitmap.imageResource(resources, it) }
    }
    val cycle = rememberInfiniteTransition(label = "homeStreet")
    val time = cycle.animateFloat(0f, 12f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "streetTime")

    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val width = 360f
        val height = size.height * width / size.width
        val roadTop = height * .44f
        val roadBottom = height * .64f
        val t = time.value

        scale(size.width / width, size.width / width, pivot = Offset.Zero) {
            fun sprite(id: Int, x: Float, y: Float, w: Float, h: Float) {
                val bitmap = art.getValue(id)
                val factor = minOf(w / bitmap.width, h / bitmap.height)
                val dw = bitmap.width * factor
                val dh = bitmap.height * factor
                drawImage(bitmap, dstOffset = IntOffset((x + (w - dw) / 2).roundToInt(), (y + h - dh).roundToInt()),
                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.None)
            }
            fun tile(id: Int, top: Float, bottom: Float) {
                clipRect(0f, top, width, bottom) {
                    for (y in top.toInt()..bottom.toInt() step 24) {
                        for (x in 0..360 step 24) sprite(id, x.toFloat(), y.toFloat(), 24f, 24f)
                    }
                }
            }

            fun paint(x: Float, y: Float, w: Float, h: Float, color: Color) {
                drawRect(color, Offset(x, y), Size(w, h))
            }

            tile(R.drawable.ground_grass, 0f, height)
            val plazaDepth = minOf(105f, roadTop * .36f)
            val plazaTop = roadTop - 24f - plazaDepth
            val fountainBase = plazaTop + plazaDepth * .48f
            fun walkway(left: Float, top: Float, pathWidth: Float, bottom: Float) {
                clipRect(left, top, left + pathWidth, bottom) {
                    tile(R.drawable.ground_walk, top, bottom)
                }
            }

            // The center walkway finishes in a small square around the fountain.
            walkway(164f, fountainBase, 32f, roadTop)
            paint(132f, plazaTop, 96f, plazaDepth * .77f, Color(0xFF297C6C))
            walkway(136f, plazaTop + 3f, 88f, plazaTop + plazaDepth * .72f)
            paint(158f, fountainBase - 17f, 44f, 19f, Ink)
            paint(155f, fountainBase - 13f, 50f, 10f, Ink)
            paint(158f, fountainBase - 12f, 44f, 8f, Color(0xFF7185A3))
            paint(161f, fountainBase - 15f, 38f, 9f, Sky.dark)
            paint(165f, fountainBase - 13f, 30f, 5f, Sky.base)
            paint(178f, fountainBase - 19f, 4f, 10f, CreamDark)
            paint(175f, fountainBase - 21f, 10f, 3f, CreamLight)
            val spray = ((t * 4f).toInt() % 3).toFloat()
            paint(171f - spray, fountainBase - 18f + spray, 2f, 2f, Sky.light)
            paint(188f + spray, fountainBase - 18f + spray, 2f, 2f, Sky.light)
            paint(166f, fountainBase - 9f, 8f, 1f, Sky.light)
            paint(186f, fountainBase - 11f, 7f, 1f, Sky.light)

            val gardenTop = roadTop - 155f
            if (gardenTop > 65f) {
                for (left in listOf(28f, 258f)) {
                    paint(left + 3f, gardenTop + 22f, 73f, 17f, Color(0xFF278A76))
                    paint(left, gardenTop + 20f, 73f, 15f, Color(0xFF3E705C))
                    for (flower in 0..8) {
                        val x = left + 5f + flower * 8f
                        val y = gardenTop + 23f + (flower % 2) * 4f
                        paint(x - 1f, y + 2f, 6f, 5f, Color(0xFF61AE7D))
                        paint(x, y, 4f, 3f, if (flower % 2 == 0) Taxi.light else Brick.light)
                    }
                }
            }
            tile(R.drawable.ground_walk, roadTop - 24f, roadTop)
            tile(R.drawable.ground_road, roadTop, roadBottom)
            tile(R.drawable.ground_walk, roadBottom, roadBottom + 24f)
            val median = (roadTop + roadBottom) / 2f
            for (x in 12..336 step 30) {
                if (homeCrossings.none { center -> x.toFloat() < center + 20f && x + 16f > center - 20f }) {
                    paint(x.toFloat(), median, 16f, 2f, Color(0xFFA09CCA))
                }
            }
            for (center in homeCrossings) {
                repeat(8) { row ->
                    val interval = (roadBottom - roadTop) / 8f
                    paint(center - 19f, roadTop + (row + .2f) * interval, 38f, interval * .55f, CreamLight)
                }
            }
            when (scene) {
                StreetScene.BARRIO -> {
                    sprite(R.drawable.house, 25f, roadTop - 127f, 86f, 103f)
                    sprite(R.drawable.house, 243f, roadTop - 118f, 89f, 94f)
                    sprite(R.drawable.decor_mailbox_barrio, 81f, roadTop - 39f, 35f, 17f)
                    sprite(R.drawable.decor_fence_barrio, 13f, roadBottom + 27f, 69f, 23f)
                }
                StreetScene.CAMPUS_RAIN -> {
                    sprite(R.drawable.decor_building_campus, 16f, roadTop - 154f, 108f, 130f)
                    sprite(R.drawable.decor_building_campus, 244f, roadTop - 139f, 94f, 115f)
                    sprite(R.drawable.decor_bikerack_campus, 23f, roadBottom + 20f, 68f, 34f)
                }
                StreetScene.CITY_NIGHT -> {
                    sprite(R.drawable.decor_building_city, 5f, roadTop - 170f, 125f, 147f)
                    sprite(R.drawable.decor_building_city, 225f, roadTop - 174f, 130f, 150f)
                    sprite(R.drawable.decor_busstop_city, 25f, roadBottom + 12f, 68f, 57f)
                }
            }
            sprite(R.drawable.tree, 117f, roadTop - 87f, 35f, 62f)
            sprite(R.drawable.tree_autumn, 197f, roadTop - 83f, 43f, 58f)
            sprite(R.drawable.tree, 296f, roadBottom + 28f, 45f, 67f)

            fun car(id: Int, start: Float, y: Float, reverse: Boolean) {
                val pass = (t - start) / 2.7f
                if (pass !in 0f..1f) return
                val x = if (reverse) 414f - pass * 468f else -54f + pass * 468f
                if (reverse) scale(-1f, 1f, pivot = Offset(x, y)) {
                    sprite(id, x - 24f, y - 18f, 48f, 36f)
                } else sprite(id, x - 24f, y - 18f, 48f, 36f)
            }
            car(R.drawable.car_coral, 0f, (roadTop + median) / 2f, reverse = false)
            car(R.drawable.car_yellow, .9f, (roadBottom + median) / 2f, reverse = true)
            car(R.drawable.car_coral, 8.9f, (roadTop + median) / 2f, reverse = false)

            // The last walker clears the far lane before traffic returns at 8.9s.
            homeWalkers.forEachIndexed { index, walker ->
                val clock = (t - walker.delay + 12f) % 12f
                val approach = (clock / 2f).coerceIn(0f, 1f)
                val crossing = ((clock - 3.5f) / 3.1f).coerceIn(0f, 1f)
                val arrival = ((clock - 6.6f) / 2f).coerceIn(0f, 1f)
                val beyondRoad = if (index == 1) roadTop - fountainBase - 8f else 24f
                val foot = roadBottom + 52f - approach * 39f -
                    crossing * (roadBottom - roadTop + 18f) - arrival * (beyondRoad - 5f)
                val x = homeCrossings[index] + (1f - approach) * walker.approachOffset
                val alpha = minOf(clock / .5f, (9.4f - clock) / .65f, 1f).coerceAtLeast(0f)
                if (alpha > 0f) {
                    val walking = clock < 2f || clock in 3.5f..8.6f
                    val id = if (walking) walker.steps[(clock * 8).toInt() % walker.steps.size] else walker.pose
                    val bitmap = art.getValue(id)
                    drawImage(bitmap, dstOffset = IntOffset((x - 12f).roundToInt(), (foot - 28f).roundToInt()),
                        dstSize = IntSize(24, 28), alpha = alpha, filterQuality = FilterQuality.None)
                }
            }

            val boardSize = Size(width, height)
            if (scene == StreetScene.CITY_NIGHT) {
                drawRect(Color(0xFF111534).copy(alpha = .64f), size = boardSize)
                for (x in listOf(40f, 180f, 320f)) {
                    val lamp = Offset(x, roadTop - 12f)
                    drawCircle(Brush.radialGradient(
                        listOf(Color(0xFFFFD997).copy(alpha = .48f), Color.Transparent),
                        center = lamp, radius = 64f
                    ), radius = 64f, center = lamp)
                    paint(x - 1f, lamp.y, 2f, 23f, InkShadow)
                    paint(x - 4f, lamp.y - 2f, 8f, 4f, Taxi.light)
                }
            }
            if (scene == StreetScene.CAMPUS_RAIN) {
                drawRect(Color(0xFF253654).copy(alpha = .26f), size = boardSize)
                // A repeating grid keeps the rain continuous when the street clock loops.
                val rainRows = (height / 46f).toInt() + 2
                for (row in 0 until rainRows) for (column in 0..11) {
                    val y = (row * height / rainRows + t * height / 3f) % height
                    val x = (column * 31f + row % 3 * 10f - y * .16f + width) % width
                    drawLine(Color(0xFFBCE4FF).copy(alpha = .64f), Offset(x, y),
                        Offset(x - 4f, y + 13f), strokeWidth = 1.25f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
