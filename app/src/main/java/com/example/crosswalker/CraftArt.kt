package com.example.crosswalker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.*

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

@Composable fun WalkerArtwork(walker: com.example.crosswalker.game.Walker, size: Dp = 30.dp) {
    val resource = when (walker.sprite) {
        "walker_coral" -> R.drawable.walker_coral
        "walker_green" -> R.drawable.walker_green
        "walker_chair" -> R.drawable.walker_chair
        "walker_slate" -> R.drawable.walker_slate
        "walker_rust" -> R.drawable.walker_rust
        else -> R.drawable.walker_blue
    }
    Box(Modifier.size(size)) {
        PixelImage(resource, walker.name, Modifier.size(size))
        // A pedestrian glued to a phone wears it as a badge wherever the UI names them.
        if (walker.kind == "phone") PixelImage(R.drawable.phone, "con celular",
            Modifier.size(size * .45f).align(Alignment.TopEnd))
    }
}

@Composable fun ToolArtwork(id: String) {
    val resource = when (id) {
        "look" -> R.drawable.icon_look
        "refuge" -> R.drawable.icon_wait
        "eye" -> R.drawable.icon_officer
        "signal" -> R.drawable.icon_signal
        "visible" -> R.drawable.star
        "lighting" -> R.drawable.star
        "speed_zone" -> R.drawable.icon_bump
        "phone_away" -> R.drawable.icon_phone_away
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
