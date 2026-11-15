package com.example.crosswalker

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun CampaignCredits(onHome: () -> Unit) {
    var scene by remember { mutableStateOf(StreetScene.BARRIO) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12000)
            scene = StreetScene.entries[(scene.ordinal + 1) % StreetScene.entries.size]
        }
    }

    val scroll = rememberScrollState()
    var finished by rememberSaveable { mutableStateOf(false) }
    val pixelsPerSecond = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(pixelsPerSecond) {
        if (finished) return@LaunchedEffect
        delay(1500)
        while (scroll.maxValue == Int.MAX_VALUE) withFrameNanos { }
        val distance = scroll.maxValue - scroll.value
        if (distance > 0) {
            scroll.animateScrollTo(scroll.maxValue, tween(
                durationMillis = (distance * 1000f / pixelsPerSecond).toInt(),
                easing = LinearEasing
            ))
        }
        finished = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(InkShadow)) {
        val introPageHeight = maxHeight * .86f
        val endPageHeight = maxHeight * .72f
        Crossfade(scene, modifier = Modifier.matchParentSize(), animationSpec = tween(1800), label = "creditsStreet") {
            HomeStreet(Modifier.fillMaxSize(), it)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to InkShadow.copy(alpha = .85f),
            .5f to InkShadow.copy(alpha = .54f),
            1f to InkShadow.copy(alpha = .9f)
        )))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll, enabled = finished).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.heightIn(min = introPageHeight).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("¡Lo lograste!", style = TitleText, color = Taxi.light, textAlign = TextAlign.Center)
                Text("Completaste los 12 niveles de Crosswalker.", style = BodyText, color = CreamLight,
                    textAlign = TextAlign.Center)
                Text("En colaboración con", style = HudStrong, color = CreamLight)
                Image(
                    painterResource(R.drawable.fundacion_ado), contentDescription = "Fundación ADO",
                    modifier = Modifier.width(188.dp).pixelPanel(fill = CreamLight, corner = 6.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp).aspectRatio(354f / 190f)
                )
            }

            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CreditSection("Desarrollo") {
                    CreditText("Juan Pablo Carrera Martinez")
                    CreditText("Arturo Gutierez Lopez")
                    CreditText("Diego Alberto Antonio Espinosa")
                    CreditText("Shalom Israel Antonio Santiago")
                }
                CreditSection("Creación y dirección creativa") {
                    CreditText("Juan Pablo Carrera Martinez")
                }
                CreditSection("Arte y tipografías") {
                    CreditText("RPG Urban, Tiny City y Roguelike Modern City · Kenney · CC0")
                    CreditLink("kenney.nl", "https://www.kenney.nl")
                    CreditText("Silla de ruedas adaptada de [LPC Revised] Elders! · Eliza Wyatt, Stephen Challener, Durrani y BenCreating · OGA-BY 3.0")
                    CreditLink("Proyecto LPC", "https://github.com/ElizaWy/LPC")
                    CreditText("Jersey 10 y Jersey 25 · The Soft Type Project Authors")
                    CreditText("Pixelify Sans · The Pixelify Sans Project Authors")
                    CreditText("Silkscreen · The Silkscreen Project Authors")
                    CreditText("Tipografías bajo SIL Open Font License 1.1")
                }
                CreditSection("Música y sonido") {
                    CreditText("Jump and Run – Tropical Mix · bart · CC BY 3.0")
                    CreditLink("OpenGameArt.org", "https://opengameart.org/content/jump-and-run-tropical-mix")
                    CreditText("Market on the Sea · Compuesta por Jonathan Shaw")
                    CreditLink("www.jshaw.co.uk", "https://www.jshaw.co.uk")
                    CreditText("Talking Cute Chiptune · Alex McCulloch")
                    CreditText("RPG Town Ambience 1 · HitCtrl")
                    CreditText("Talking Synthesizer · tcarisland · CC BY 4.0")
                    CreditLink("Talking Synthesizer en OpenGameArt", "https://opengameart.org/content/talking-synthesizer")
                }
            }

            Column(
                Modifier.heightIn(min = endPageHeight).fillMaxWidth().padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelButton("VOLVER AL MENÚ DE INICIO", accent = Taxi, modifier = Modifier.widthIn(max = 360.dp),
                    onClick = onHome)
            }
        }
    }
}

@Composable
private fun CreditSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = TitleSmall, color = Taxi.light, textAlign = TextAlign.Center)
        content()
    }
}

@Composable
private fun CreditText(text: String) {
    Text(text, style = BodyText, color = CreamLight, textAlign = TextAlign.Center)
}

@Composable
private fun CreditLink(label: String, url: String) {
    val link = buildAnnotatedString {
        withLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(
            color = Sky.light, textDecoration = TextDecoration.Underline
        )))) { append(label) }
    }
    Text(link, style = BodyText, textAlign = TextAlign.Center)
}
