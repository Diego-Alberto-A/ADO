package com.example.crosswalker

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** Deliberately cut corners; the same construction pattern is used throughout the UI. */
object CutPaper : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val nick = with(density) { 2.dp.toPx() }
        return Outline.Generic(Path().apply {
            moveTo(nick, nick); lineTo(w * .31f, 0f); lineTo(w * .69f, nick)
            lineTo(w - nick, 0f); lineTo(w, h * .37f); lineTo(w - nick, h - nick)
            lineTo(w * .61f, h); lineTo(w * .29f, h - nick); lineTo(0f, h)
            lineTo(nick, h * .44f); close()
        })
    }
}

@Composable fun Modifier.craftPaper(felt: Boolean = false): Modifier {
    val texture = painterResource(if (felt) R.drawable.material_felt else R.drawable.material_kraft)
    return this.clip(CutPaper).drawBehind {
        val side = maxOf(size.width, size.height)
        with(texture) { draw(size = Size(side, side)) }
    }
}

@Composable fun CraftChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    Box(Modifier.padding(vertical = 3.dp).heightIn(min = 42.dp).craftPaper(selected)
        .border(if (selected) 1.5.dp else .5.dp, if (selected) Paper else Ink.copy(alpha = .25f), CutPaper)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) Paper else Ink
        ) { label() }
    }
}

@Composable fun ToolArtwork(id: String) {
    val resource = when (id) {
        "look" -> R.drawable.icon_look
        "wait" -> R.drawable.icon_wait
        "eye" -> R.drawable.walker_blue
        "signal" -> R.drawable.icon_signal
        "visible" -> R.drawable.star
        "officer" -> R.drawable.stop_sign
        "school" -> R.drawable.house
        "bump" -> R.drawable.icon_bump
        else -> R.drawable.icon_crosswalk
    }
    Image(painterResource(resource), contentDescription = null, modifier = Modifier.size(30.dp))
}

@Composable fun PaperStars(count: Int, size: Dp = 18.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Image(painterResource(R.drawable.star),
            contentDescription = if (i < count) "Estrella obtenida" else "Estrella pendiente",
            modifier = Modifier.size(size), alpha = if (i < count) 1f else .23f) }
    }
}
