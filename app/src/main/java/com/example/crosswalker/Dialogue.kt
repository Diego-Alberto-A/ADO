package com.example.crosswalker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.crosswalker.ui.theme.*
import android.media.ToneGenerator
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Words the dialogue tints: people in Tree, objects in Taxi, places in Sky. */
data class Lexicon(
    val people: Set<String> = emptySet(),
    val objects: Set<String> = emptySet(),
    val places: Set<String> = emptySet()
) {
    companion object {
        private val PLACES = setOf("calle", "cruce", "banqueta", "carril", "escuela",
            "ciudad", "barrio", "acera", "esquina", "camino", "paso")

        /** Names come from the level, tool words from the catalogue: no hard-coded copy. */
        fun of(level: com.example.crosswalker.game.Level,
               tools: List<com.example.crosswalker.game.Tool>) = Lexicon(
            people = level.walkers.map { it.name.normalise() }.toSet(),
            objects = tools.flatMap { it.label.split(" ") }.map { it.normalise() }
                .filter { it.length > 3 }.toSet(),
            places = PLACES
        )
    }
}

private fun String.normalise() = lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u')

private val WORD = Regex("[\\p{L}]+")

/** Tints the keywords it recognises; everything else stays ink. */
fun colorize(text: String, lexicon: Lexicon): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in WORD.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        val word = match.value
        val accent = when (word.normalise()) {
            in lexicon.people -> Tree.dark
            in lexicon.objects -> Orange.dark
            in lexicon.places -> Sky.dark
            else -> null
        }
        if (accent == null) append(word)
        else withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(word) }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/**
 * A dialogue box in the Animal Crossing / classic Pokémon tradition: cream
 * bubble with a double border, a tilted name plate breaking out of the top-left
 * corner, text that types itself and a blinking arrow while it does.
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
    voice: com.example.crosswalker.audio.SoundEngine? = null
) {
    var revealed by remember(text) { mutableIntStateOf(0) }
    val done = revealed >= text.length
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
                .clickable(interactionSource = null, indication = null) {
                    if (done && onAdvance != null) onAdvance() else revealed = text.length
                }
                .padding(start = 12.dp, end = 12.dp, top = if (speaker == null) 12.dp else 16.dp,
                    bottom = 12.dp)
                .semantics { contentDescription = text }
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(colorize(text.take(revealed), lexicon), style = BodyText, color = Ink,
                    maxLines = 3, modifier = Modifier.weight(1f).clearAndSetSemantics {})
                // The arrow blinks while typing, and keeps blinking when there is a next page.
                if (!done || onAdvance != null) Box(Modifier.padding(start = 8.dp)) { MoreTextArrow() }
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
