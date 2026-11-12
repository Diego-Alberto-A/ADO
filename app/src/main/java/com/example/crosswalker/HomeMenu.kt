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
            Eyebrow("UN PEQUEÑO MUNDO · GRANDES DECISIONES")
            BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(.8f)
                .clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = "Jugar: elegir nivel", onClick = play)) {
                PixelImage(R.drawable.home_diorama,
                    "Una calle con su paso de peatones, vista desde arriba",
                    Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .12f)
                    .fillMaxWidth(.82f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Logo(21.sp)
                    Text("El camino seguro empieza contigo.", color = Ink, style = BodyText)
                }
            }
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayButton(enabled = enabled, onClick = play)
                Text("Elige un nivel y empieza a explorar", color = Asphalt, style = BodyText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Row(Modifier.widthIn(max = 440.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    PaperButton("⚙  AJUSTES", Neutral, enabled = enabled) { click(); s.dialog = "settings" }
                }
                Box(Modifier.weight(1f)) {
                    PaperButton("★  LOGROS", Neutral, enabled = enabled) { click(); s.dialog = "achievements" }
                }
            }
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperButton("↻  REINICIAR PROGRESO", Neutral, enabled = enabled) {
                    click(); s.dialog = "resetProgress"
                }
                Text("Vuelve al primer cruce y juega desde cero", color = Asphalt, style = BodyText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

    }
}
