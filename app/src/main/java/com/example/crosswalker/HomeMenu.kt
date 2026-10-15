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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crosswalker.ui.theme.Jersey25

@Composable private fun PlayButton(enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().shadow(5.dp, CutPaper).craftPaper(true)
        .border(1.dp, Paper.copy(alpha = .65f), CutPaper)
        .clickable(enabled = enabled, role = Role.Button,
            onClickLabel = "Elegir nivel", onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Paper, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Text("▶", color = Green, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text("JUGAR", color = Paper, fontFamily = Jersey25, fontSize = 30.sp,
            letterSpacing = 2.sp)
    }
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
                Image(painterResource(R.drawable.home_diorama),
                    contentDescription = "Una ciudad hecha a mano dentro de una caja de cartón",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .12f)
                    .fillMaxWidth(.82f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Logo(21.sp)
                    Text("El camino seguro empieza contigo.", color = Ink,
                        fontSize = 11.sp, fontFamily = Jersey25)
                }
            }
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayButton(enabled = enabled, onClick = play)
                Text("Elige un nivel y empieza a explorar", color = Ink.copy(alpha = .7f),
                    fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Row(Modifier.widthIn(max = 440.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    PaperButton("⚙  AJUSTES", Kraft, enabled = enabled) { click(); s.dialog = "settings" }
                }
                Box(Modifier.weight(1f)) {
                    PaperButton("★  LOGROS", Kraft, enabled = enabled) { click(); s.dialog = "achievements" }
                }
            }
        }

    }
}
