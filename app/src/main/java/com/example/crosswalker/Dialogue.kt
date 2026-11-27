package com.example.crosswalker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.*
import android.media.ToneGenerator
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Terms the dialogue marks by category. */
data class Lexicon(
    val people: Set<String> = emptySet(),
    val objects: Set<String> = emptySet(),
    val places: Set<String> = emptySet()
) {
    companion object {
        private val PLACES = setOf("calle", "cruce", "banqueta", "carril", "escuela",
            "ciudad", "barrio", "acera", "esquina", "camino", "paso")

        private val CONTROLS = setOf(
            "abrir plan", "cerrar plan", "poner el plan en marcha", "habilidades",
            "la calle", "pausa", "fichas", "pestaña", "panel", "mirar", "izquierda", "centro", "derecha"
        )

        fun of(level: com.example.crosswalker.game.Level,
               tools: List<com.example.crosswalker.game.Tool>) = Lexicon(
            people = level.walkers.map { it.name.normalise() }.toSet(),
            objects = tools.map { it.label.normalise() }.toSet() + CONTROLS.map { it.normalise() },
            places = PLACES + "mapa del cruce"
        )
    }
}

private fun String.normalise() = lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u')

private class DialogueMarker(lexicon: Lexicon) {
    private val colors = buildMap {
        lexicon.places.forEach { put(it, Sky.light) }
        lexicon.objects.forEach { put(it, Taxi.base) }
        lexicon.people.forEach { put(it, Tree.light) }
    }
    private val terms = colors.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val pattern = terms.takeIf { it.isNotEmpty() }?.let {
        Regex("(?<![\\p{L}])(?:$it)(?![\\p{L}])")
    }

    fun matches(text: String) = pattern?.findAll(text.normalise())?.toList().orEmpty()

    fun render(text: String, matches: List<MatchResult>, revealed: Int): AnnotatedString = buildAnnotatedString {
        append(text)
        matches.forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, end)
            if (end <= revealed) {
                val color = colors.getValue(match.value)
                val emphasis = if (revealed == text.length) 0f else (1f - (revealed - end) / 9f).coerceIn(0f, 1f)
                val marker = lerp(color.copy(alpha = .50f), color.copy(alpha = .95f), emphasis)
                addStyle(SpanStyle(background = marker), start, end)
            }
        }
        if (revealed < text.length) addStyle(SpanStyle(color = Color.Transparent), revealed, text.length)
    }
}

/**
 * A dialogue box in the Animal Crossing / classic Pokémon tradition: cream
 * bubble with a double border, a tilted name plate breaking out of the top-left
 * corner, text that types itself and an animated tap prompt.
 *
 * Tapping completes the line, as pressing A would.
 */
@Composable fun DialogueBox(
    text: String,
    speaker: String?,
    speakerColor: Accent,
    lexicon: Lexicon,
    modifier: Modifier = Modifier,
    blip: () -> Unit = {},
    onAdvance: (() -> Unit)? = null,
    voice: com.example.crosswalker.audio.SoundEngine? = null,
    showTapHint: Boolean = true
) {
    var revealed by remember(text) { mutableIntStateOf(0) }
    val done = revealed >= text.length
    val canTap = !done || onAdvance != null
    val marker = remember(lexicon) { DialogueMarker(lexicon) }
    val matches = remember(marker, text) { marker.matches(text) }
    val entry = remember(text) { Animatable(.9f) }

    LaunchedEffect(text) {
        entry.animateTo(1f, keyframes {
            durationMillis = 200
            .9f at 0; 1.05f at 120; 1f at 200
        })
    }
    // Roughly 40 characters a second, with a voice blip every third one, and a
    // murmullo de fondo (talkingsynth) mientras dura el tecleo.
    LaunchedEffect(text) {
        revealed = 0
        voice?.loopVoice(com.example.crosswalker.R.raw.voice_talking)
        while (revealed < text.length) {
            delay(25)
            revealed++
            if (revealed % 3 == 0) blip()
        }
        voice?.stopVoice()
    }
    DisposableEffect(Unit) { onDispose { voice?.stopVoice() } }

    Box(modifier.graphicsLayer {
        scaleX = entry.value; scaleY = entry.value
        transformOrigin = TransformOrigin(.5f, 1f)
    }) {
        Column(
            Modifier.fillMaxWidth()
                .pixelPanel(fill = Cream, innerBorder = CreamDark, corner = 4.dp)
                // Tapping completes the line; tapping a finished line turns the page.
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = canTap,
                    onClickLabel = if (done) "Siguiente página" else "Mostrar texto completo"
                ) {
                    if (done) onAdvance?.invoke() else revealed = text.length
                }
                .padding(start = 12.dp, end = 12.dp, top = if (speaker == null) 12.dp else 16.dp,
                    bottom = 12.dp)
                .semantics { contentDescription = text }
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(marker.render(text, matches, revealed), style = BodyText, color = Ink,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {})
                if (showTapHint) {
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.BottomEnd) {
                        if (canTap) {
                            val motion = rememberClickMotion()
                            val hand = ImageBitmap.imageResource(R.drawable.pointer_hand)
                            Canvas(Modifier.size(30.dp).semantics {
                                contentDescription = if (done) "Toca para avanzar" else "Toca para revelar el texto"
                            }) {
                                drawClickHand(hand, Offset(size.width * .15f, size.height * .84f), 19.dp.toPx(), motion.value)
                            }
                        }
                    }
                }
            }
        }
        // The name plate lands a beat after the box, leaning out of the corner.
        if (speaker != null) NamePlate(speaker, speakerColor,
            Modifier.align(Alignment.TopStart).absoluteOffset(x = 8.dp, y = (-10).dp))
    }
}

@Composable private fun NamePlate(name: String, accent: Accent, modifier: Modifier = Modifier) {
    val drop = remember(name) { Animatable(-2f) }
    LaunchedEffect(name) { delay(60); drop.animateTo(0f, tween(140, easing = EaseOutBack)) }
    Box(modifier.absoluteOffset(y = drop.value.dp).rotate(-4f)
        .pixelPanel(fill = accent.base, highlight = accent.light, shade = accent.dark, corner = 3.dp)
        .padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(name.uppercase(), style = HudStrong, color = textOn(accent.base))
    }
}


/** Plays one short blip; the host wires this to the tone generator. */
val LocalVoice = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Each speaker keeps the same pitch for the whole game. */
private val VOICES = listOf(
    ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_7,
    ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_0, ToneGenerator.TONE_DTMF_5
)

@Composable fun blip(speakerId: String): () -> Unit {
    val voice = LocalVoice.current
    val tone = VOICES[abs(speakerId.hashCode()) % VOICES.size]
    return remember(speakerId, voice) { { voice(tone) } }
}

/** A pedestrian's name plate takes the accent their sprite already wears. */
fun walkerAccent(sprite: String): Accent = when (sprite) {
    "walker_coral" -> Brick
    "walker_green" -> Tree
    "walker_chair" -> Orange
    "walker_slate" -> Taxi
    else -> Sky
}
