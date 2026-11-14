package com.example.crosswalker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crosswalker.ui.theme.*

@Composable private fun PlayButton(enabled: Boolean, onClick: () -> Unit) {
    PixelButton("JUGAR", Tree, enabled, onClickLabel = "Elegir nivel",
        leading = {
            Box(Modifier.size(28.dp).pixelSlot(), contentAlignment = Alignment.Center) {
                Text("▶", color = Tree.dark, style = HudStrong)
            }
        }, onClick = onClick)
}

@Composable fun Home(s: Session, enabled: Boolean, click: () -> Unit) {
    val play = { if (enabled) { click(); s.navigate("map") } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = enabled)
            .padding(horizontal = 24.dp, vertical = 30.dp)
            .then(if (!enabled) Modifier.clearAndSetSemantics {} else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(.8f)
                .clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = "Jugar: elegir nivel", onClick = play)) {
                PixelImage(R.drawable.home_diorama,
                    "Una calle con su paso de peatones, vista desde arriba",
                    Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .12f)
                    .fillMaxWidth(.82f)) {
                    Logo(21.sp)
                }
            }
            Box(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                PlayButton(enabled = enabled, onClick = play)
            }
            Row(Modifier.widthIn(max = 440.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    PaperButton("⚙  AJUSTES", Neutral, enabled = enabled) { click(); s.dialog = "settings" }
                }
                Box(Modifier.weight(1f)) {
                    PaperButton("★  LOGROS", Neutral, enabled = enabled) { click(); s.dialog = "achievements" }
                }
            }
            Box(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                PaperButton("↻  REINICIAR PROGRESO", Neutral, enabled = enabled) {
                    click(); s.dialog = "resetProgress"
                }
            }
        }

    }
}
