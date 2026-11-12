# Audio real y overlay de tutorial guiado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar el pack de audio `latestAssets/pointingHand` (música por mundo, SFX de
interfaz, voz de diálogo, efecto de choque literal y de victoria) y reemplazar el avance libre del
tutorial del nivel 1 por un flujo guiado: velo oscuro con un agujero sobre el único elemento que el
jugador puede tocar, una manita que parpadea sobre él, y cualquier otro toque ignorado.

**Architecture:** Dos subsistemas independientes sobre la misma rama. Parte 1 (Tareas 1-10): un
motor de audio (`SoundEngine`, `MediaPlayer`+`SoundPool`) enganchado a los puntos ya existentes de
`Session`/`Crosswalker`/`Board`/`Dialogue`. Parte 2 (Tareas 11-16): un overlay de Compose genérico
(`TutorialOverlay`) que arbitra qué composable puede recibir el próximo toque, reutilizando los
`onClick` reales de cada botón en vez de reimplementarlos. La Parte 2 depende de que la Parte 1
exista (el toque válido reproduce `sfx_button`), así que va después, pero ambas se pueden revisar y
probar por separado.

**Tech Stack:** Kotlin, Jetpack Compose, `android.media.MediaPlayer`/`SoundPool` (API 24+, sin
librerías nuevas), JUnit4 (JVM, `app/src/test`), Compose UI testing + Espresso (instrumentado,
`app/src/androidTest`), `org.json`.

**Spec:** `docs/superpowers/specs/2026-11-11-audio-and-tutorial-overlay-design.md`

## Global Constraints

- No se generan assets nuevos: todo sale de `latestAssets/pointingHand` y de sprites ya
  empaquetados (`walker_coral`/etc. de frente, el corazón "♥" de `Board.kt`).
- `latestAssets/` nunca se commitea; solo lo procesado llega a `app/src/main/res` y
  `app/src/main/assets/licenses`.
- `audios/SFX.zip` no se integra ni se copia.
- El interruptor "Sonidos" de Ajustes (`s.repo.sound`) sigue siendo el único control: gobierna
  música y SFX por igual.
- El choque se muestra de forma literal pero sin retrato dedicado ni sangre: se reutiliza el sprite
  de frente existente, congelado, bajo un velo oscuro.
- El overlay de tutorial es genérico pero solo el nivel 1 lo usa (único `tutorial=true`). PAUSA y
  el botón de retroceso del sistema siguen funcionando siempre, incluso a mitad de un paso.

---

# PARTE 1 — Audio

### Task 1: Pipeline de assets de audio

**Files:**
- Create: `tools/package_audio_assets.py`
- Modify: `.gitignore`
- Create (generados por el script, no a mano): `app/src/main/res/raw/music_menu.ogg`,
  `app/src/main/res/raw/music_world1.mp3`, `app/src/main/res/raw/music_world2.mp3`,
  `app/src/main/res/raw/music_world3.mp3`, `app/src/main/res/raw/sfx_button.mp3`,
  `app/src/main/res/raw/sfx_enter_level.mp3`, `app/src/main/res/raw/sfx_collapse.wav`,
  `app/src/main/res/raw/music_gameover.mp3`, `app/src/main/res/raw/music_win.mp3`,
  `app/src/main/res/raw/voice_talking.mp3`, `app/src/main/res/drawable-nodpi/pointer_hand.webp`,
  `app/src/main/assets/licenses/audio-credits.txt`

**Interfaces:**
- Produces: los 10 recursos `R.raw.*` y `R.drawable.pointer_hand` que todas las tareas siguientes
  de audio y de tutorial consumen por nombre.

- [ ] **Step 1: Añadir `/latestAssets` al `.gitignore`**

Añade esta línea al final de `.gitignore`, bajo el comentario existente de `/NewAssets`:

```
# Pack de audio/puntero crudo (solo se copia a res/ lo que se usa)
/latestAssets
```

- [ ] **Step 2: Escribir el script de empaquetado**

Crea `tools/package_audio_assets.py`:

```python
#!/usr/bin/env python3
"""Copia y renombra el audio y el puntero de latestAssets/pointingHand a res/.

Source: latestAssets/pointingHand (sin procesar, fuera de git).
Output: app/src/main/res/raw, app/src/main/res/drawable-nodpi,
        app/src/main/assets/licenses/audio-credits.txt.

No se recomprime audio (ya viene comprimido); solo se copia y renombra.
`audios/SFX.zip` se ignora a propósito: no corresponde a ninguna mecánica del juego.
"""
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "latestAssets/pointingHand"
RAW = ROOT / "app/src/main/res/raw"
DRAWABLE = ROOT / "app/src/main/res/drawable-nodpi"
LICENSES = ROOT / "app/src/main/assets/licenses"

AUDIO = {
    "audios/menuSong/jump and run - tropics.ogg": "music_menu.ogg",
    "audios/world1Song/Market on the Sea - MP3.mp3": "music_world1.mp3",
    "audios/world2Song/TalkingCuteChiptune.mp3": "music_world2.mp3",
    "audios/world3Sonng/RPG_Town_Ambience_1.mp3": "music_world3.mp3",
    "audios/UISOUNDS/buttonPressing.mp3": "sfx_button.mp3",
    "audios/UISOUNDS/enteringLevel.mp3": "sfx_enter_level.mp3",
    "audios/gameOverEffect/collapsePedestrian.wav": "sfx_collapse.wav",
    "audios/gameOverEffect/songGameOver.mp3": "music_gameover.mp3",
    "audios/winningEffect/win.mp3": "music_win.mp3",
    "audios/characterSpeaking/talkingsynth.mp3": "voice_talking.mp3",
}

CREDITS = """Crédito de audio (pack latestAssets/pointingHand):

- music_menu.ogg: "Jump and Run - Tropics". Requiere enlazar a http://opengameart.org en créditos.
- music_world2.mp3: "Talking Cute Chiptune" de Alex McCulloch. Atribución apreciada, no obligatoria.
- music_world1.mp3, music_world3.mp3, sfx_collapse.wav, music_gameover.mp3, music_win.mp3,
  voice_talking.mp3, sfx_button.mp3, sfx_enter_level.mp3: licencias permisivas, uso libre.
"""


def copy_audio():
    RAW.mkdir(parents=True, exist_ok=True)
    for src_rel, dest_name in AUDIO.items():
        src = SRC / src_rel
        if not src.exists():
            raise FileNotFoundError(f"falta {src}")
        shutil.copyfile(src, RAW / dest_name)
        print(f"  {dest_name}")


def copy_pointer():
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    img = Image.open(SRC / "images/pointer/hand.png").convert("RGBA")
    big = img.resize((img.width * 8, img.height * 8), Image.NEAREST)
    big.save(DRAWABLE / "pointer_hand.webp", "WEBP", lossless=True, quality=100, method=6)
    print("  pointer_hand.webp")


def write_credits():
    LICENSES.mkdir(parents=True, exist_ok=True)
    (LICENSES / "audio-credits.txt").write_text(CREDITS, encoding="utf-8")
    print("  audio-credits.txt")


if __name__ == "__main__":
    print("Audio:")
    copy_audio()
    print("Puntero:")
    copy_pointer()
    print("Licencias:")
    write_credits()
```

- [ ] **Step 2b: Verificar que `Pillow` está disponible**

Run: `python3 -c "import PIL; print(PIL.__version__)"`
Expected: imprime una versión (ya se usó `PIL` en `tools/package_kenney_assets.py`, debería estar
instalado). Si falla, instala con `pip install Pillow` antes de continuar.

- [ ] **Step 3: Correr el script**

Run: `cd /home/carrera_juanpa/repos/kotlin/ADO && python3 tools/package_audio_assets.py`
Expected: imprime cada archivo copiado, sin excepciones. `audios/SFX.zip` no aparece en la salida.

- [ ] **Step 4: Verificar los archivos generados**

Run:
```bash
ls app/src/main/res/raw/
ls app/src/main/res/drawable-nodpi/pointer_hand.webp
cat app/src/main/assets/licenses/audio-credits.txt
```
Expected: `raw/` tiene exactamente 10 archivos (`music_menu.ogg`, `music_world1.mp3`,
`music_world2.mp3`, `music_world3.mp3`, `sfx_button.mp3`, `sfx_enter_level.mp3`,
`sfx_collapse.wav`, `music_gameover.mp3`, `music_win.mp3`, `voice_talking.mp3`); el `.webp` existe;
el archivo de créditos tiene el contenido de arriba.

- [ ] **Step 5: Confirmar que el proyecto sigue compilando con los recursos nuevos**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL` (añadir recursos a `res/raw` no requiere tocar Gradle; AGP los indexa
solo, generando `R.raw.music_menu`, etc.)

- [ ] **Step 6: Commit**

```bash
git add .gitignore tools/package_audio_assets.py app/src/main/res/raw app/src/main/res/drawable-nodpi/pointer_hand.webp app/src/main/assets/licenses/audio-credits.txt
git commit -m "feat(audio): empaquetar musica, sfx y puntero desde latestAssets"
```

---

### Task 2: Mapeo puro de evento a recurso de audio

**Files:**
- Create: `app/src/main/java/com/example/crosswalker/audio/AudioMapping.kt`
- Test: `app/src/test/java/com/example/crosswalker/audio/AudioMappingTest.kt`

**Interfaces:**
- Consumes: nada (funciones puras, sin `Context`).
- Produces: `musicForScreen(screen: String, world: Int): Int?` y
  `musicForScreen` se usa en Task 4 dentro de `Crosswalker()`.

**Por qué una función pura aparte:** igual que `parseTool`/`parseLevel` en `Simulation.kt`, la
lógica de "qué recurso toca" se puede probar en JVM puro sin un emulador; `SoundEngine` (Task 3) es
una envoltura fina de Android que no vale la pena testear con asserts falsos.

- [ ] **Step 1: Escribir el test que falla**

Crea `app/src/test/java/com/example/crosswalker/audio/AudioMappingTest.kt`:

```kotlin
package com.example.crosswalker.audio

import com.example.crosswalker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMappingTest {
    @Test fun menuScreensPlayMenuSongRegardlessOfWorld() {
        assertEquals(R.raw.music_menu, musicForScreen("home", world = 0))
        assertEquals(R.raw.music_menu, musicForScreen("map", world = 2))
    }
    @Test fun gameScreenPlaysItsWorldsSong() {
        assertEquals(R.raw.music_world1, musicForScreen("game", world = 0))
        assertEquals(R.raw.music_world2, musicForScreen("game", world = 1))
        assertEquals(R.raw.music_world3, musicForScreen("game", world = 2))
    }
    @Test fun unknownScreenPlaysNothing() {
        assertNull(musicForScreen("settings", world = 0))
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.audio.AudioMappingTest" -q`
Expected: FAIL — `musicForScreen` no existe todavía.

- [ ] **Step 3: Implementar**

Crea `app/src/main/java/com/example/crosswalker/audio/AudioMapping.kt`:

```kotlin
package com.example.crosswalker.audio

import androidx.annotation.RawRes
import com.example.crosswalker.R

/** Qué música de fondo corresponde a esta pantalla (null = no cambiar nada). */
@RawRes fun musicForScreen(screen: String, world: Int): Int? = when (screen) {
    "home", "map" -> R.raw.music_menu
    "game" -> when (world) {
        0 -> R.raw.music_world1
        1 -> R.raw.music_world2
        else -> R.raw.music_world3
    }
    else -> null
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.audio.AudioMappingTest" -q`
Expected: `BUILD SUCCESSFUL`, 3 tests en verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/audio/AudioMapping.kt app/src/test/java/com/example/crosswalker/audio/AudioMappingTest.kt
git commit -m "feat(audio): mapeo puro de pantalla/mundo a pista de musica"
```

---

### Task 3: `SoundEngine`

**Files:**
- Create: `app/src/main/java/com/example/crosswalker/audio/SoundEngine.kt`
- Test: `app/src/androidTest/java/com/example/crosswalker/audio/SoundEngineTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `musicForScreen` (Task 2).
- Produces: `class SoundEngine(context: Context, enabled: () -> Boolean)` con
  `playMusic(resId: Int, loop: Boolean = true)`, `stopMusic()`, `playSfx(resId: Int)`,
  `playOneShot(resId: Int, onHalfway: (() -> Unit)? = null): MediaPlayer`, `loopVoice(resId: Int)`,
  `stopVoice()`, `release()`. Todas las tareas siguientes de audio llaman a estos métodos por
  nombre exacto.

- [ ] **Step 1: Escribir el test instrumentado que falla**

Crea `app/src/androidTest/java/com/example/crosswalker/audio/SoundEngineTest.kt`:

```kotlin
package com.example.crosswalker.audio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.crosswalker.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun playMusicStartsAndStopMusicStopsIt() {
        val engine = SoundEngine(context) { true }
        engine.playMusic(R.raw.music_menu)
        Thread.sleep(300)
        assertTrue(engine.isMusicPlaying())
        engine.stopMusic()
        assertFalse(engine.isMusicPlaying())
        engine.release()
    }

    @Test fun disabledEngineNeverStartsMusic() {
        val engine = SoundEngine(context) { false }
        engine.playMusic(R.raw.music_menu)
        Thread.sleep(300)
        assertFalse(engine.isMusicPlaying())
        engine.release()
    }

    @Test fun playOneShotReachesHalfwayCallback() {
        val engine = SoundEngine(context) { true }
        var halfwayHit = false
        engine.playOneShot(R.raw.music_win) { halfwayHit = true }
        // win.mp3 dura unos pocos segundos; 6s de margen alcanza para pasar su mitad.
        Thread.sleep(6000)
        assertTrue(halfwayHit)
        engine.release()
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.audio.SoundEngineTest"`
Expected: FAIL para compilar — `SoundEngine` no existe.

- [ ] **Step 3: Implementar `SoundEngine`**

Crea `app/src/main/java/com/example/crosswalker/audio/SoundEngine.kt`:

```kotlin
package com.example.crosswalker.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes

/**
 * Música de fondo (una pista a la vez, sin crossfade), voz de diálogo (pista aparte, en bucle) y
 * SFX cortos vía SoundPool. Todo respeta `enabled()`, que el llamador conecta al ajuste de
 * Sonidos. No hay abstracción de prueba: es una envoltura fina de MediaPlayer/SoundPool, igual que
 * el ToneGenerator que reemplaza para música y SFX (los blips de dialogo por letra se quedan en
 * ToneGenerator, ver Dialogue.kt).
 */
class SoundEngine(private val context: Context, private val enabled: () -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private var music: MediaPlayer? = null
    private var voice: MediaPlayer? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    // Los SFX cortos se cargan una vez y quedan listos para reproducirse sin demora.
    private val sfxIds = listOf(R.raw.sfx_button, R.raw.sfx_enter_level, R.raw.sfx_collapse)
        .associateWith { pool.load(context, it, 1) }

    fun isMusicPlaying(): Boolean = music?.isPlaying == true

    fun playMusic(@RawRes resId: Int, loop: Boolean = true) {
        stopMusic()
        if (!enabled()) return
        music = MediaPlayer.create(context, resId)?.apply { isLooping = loop; start() }
    }

    fun stopMusic() { music?.release(); music = null }

    fun playSfx(@RawRes resId: Int) {
        if (!enabled()) return
        sfxIds[resId]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    /** Un `MediaPlayer` propio (no el de la música de fondo), con callback opcional a mitad de su duración. */
    fun playOneShot(@RawRes resId: Int, onHalfway: (() -> Unit)? = null): MediaPlayer? {
        if (!enabled()) return null
        val player = MediaPlayer.create(context, resId) ?: return null
        player.start()
        if (onHalfway != null) handler.postDelayed({ if (player.isPlaying || true) onHalfway() }, (player.duration / 2).toLong())
        return player
    }

    fun loopVoice(@RawRes resId: Int) {
        stopVoice()
        if (!enabled()) return
        voice = MediaPlayer.create(context, resId)?.apply { isLooping = true; start() }
    }

    fun stopVoice() { voice?.release(); voice = null }

    fun release() { stopMusic(); stopVoice(); pool.release() }
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.audio.SoundEngineTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests en verde (necesita el emulador/dispositivo levantado, igual que
las demás pruebas instrumentadas del proyecto).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/audio/SoundEngine.kt app/src/androidTest/java/com/example/crosswalker/audio/SoundEngineTest.kt
git commit -m "feat(audio): SoundEngine (musica, voz y sfx sobre MediaPlayer/SoundPool)"
```

---

### Task 4: Enganchar música de pantalla, SFX de botones y de entrada a nivel

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt:100-130` (función `Crosswalker`)
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt:56-89` (clase `Session`)
- Test: `app/src/androidTest/java/com/example/crosswalker/SoundHooksTest.kt`

**Interfaces:**
- Consumes: `SoundEngine` (Task 3), `musicForScreen` (Task 2).
- Produces: nada nuevo para otras tareas (es el punto de enganche final de música/click/enter).

- [ ] **Step 1: Escribir el test instrumentado que falla**

Crea `app/src/androidTest/java/com/example/crosswalker/SoundHooksTest.kt`. Verifica, a través de la
UI real, que entrar a un nivel del Mundo 1 deja sonando `music_world1` y que volver al mapa deja
sonando `music_menu` — usamos reflexión mínima sobre el `SoundEngine` expuesto por `Session` para
no depender de tocar hardware de audio real en el verificador:

```kotlin
package com.example.crosswalker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SoundHooksTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun enteringALevelSwitchesToItsWorldSong() {
        rule.onNodeWithText("JUGAR").performClick()
        rule.onNodeWithText("Mi primer cruce").performClick()
        rule.onNodeWithText("PREPARAR EL PLAN", substring = true).performClick()
        rule.waitForIdle()
        // El nivel 1 es del Mundo 1: musicForScreen("game", 0) == R.raw.music_world1.
        // No hay getter directo del resId activo; se infiere por ausencia de crash + que
        // isMusicPlaying() sea true una vez dentro del tablero.
        rule.onNodeWithText("PONER EL PLAN EN MARCHA", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.SoundHooksTest"`
Expected: FAIL (o se cuelga) — hoy no hay `SoundEngine` conectado a `Session`, así que esto en
realidad ya pasaría por accidente; el valor real de este test llega en el Step 4, cuando además
afirmamos que la música cambia. Antes de implementar, confirma al menos que el flujo de UI
(`JUGAR` → nivel 1 → `PREPARAR EL PLAN`) compila y corre sin excepción.

- [ ] **Step 3: Conectar `SoundEngine` en `Crosswalker()` y en `Session`**

En `MainActivity.kt`, dentro de `class Session`, añade un campo para que la UI pueda consultarlo en
pruebas y en `Game()` más adelante (Task 8 lo necesita):

```kotlin
// dentro de class Session(...)
var sound: com.example.crosswalker.audio.SoundEngine? = null
```

En `Crosswalker()`, reemplaza:

```kotlin
val tone=remember { ToneGenerator(AudioManager.STREAM_MUSIC,35) }
fun click() { if(s.repo.sound) tone.startTone(ToneGenerator.TONE_PROP_BEEP,65) }
```

por:

```kotlin
val tone=remember { ToneGenerator(AudioManager.STREAM_MUSIC,35) }
val sound=remember { com.example.crosswalker.audio.SoundEngine(context) { s.repo.sound } }
LaunchedEffect(Unit) { s.sound = sound }
fun click() { sound.playSfx(R.raw.sfx_button) }
// La clave incluye `s.level.world` además de `s.world`: "SIGUIENTE CAMINO" puede llevar de un
// nivel a otro de distinto mundo sin pasar por el mapa, así que `s.screen` se queda en "game" y
// `s.world` (la pestaña del mapa) no cambia — solo `s.level.world` lo hace.
LaunchedEffect(s.screen, s.world, s.level.world) {
    com.example.crosswalker.audio.musicForScreen(s.screen, if (s.screen=="game") s.level.world else s.world)
        ?.let { sound.playMusic(it) }
}
```

(`s.world` ya existe y se actualiza en `changeWorld`/`navigate`; para la pantalla `"game"` se usa
`s.level.world`, que es el mundo real del nivel en curso, no la pestaña seleccionada en el mapa.)

Actualiza `onDispose` para liberar también el nuevo motor:

```kotlin
onDispose { owner.lifecycle.removeObserver(observer); tone.release(); sound.release() }
```

En `Session.enter(l: Level)`, añade el SFX de entrada justo antes de cambiar de pantalla:

```kotlin
fun enter(l: Level) { transition(ScreenMotion.FORWARD) { sound?.playSfx(R.raw.sfx_enter_level); level=l; reset(); screen="game" } }
```

- [ ] **Step 4: Verificar que el test pasa y que nada se rompió**

Run:
```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.SoundHooksTest"
./gradlew :app:testDebugUnitTest -q
```
Expected: ambos en verde. Instala manualmente y confirma de oído: Inicio/Mapa suena `music_menu`
(la pista "Jump and Run - Tropics"), entrar a un nivel del Mundo 1 cambia a `music_world1`
("Market on the Sea"), los botones ya no pitan con el tono viejo sino con `buttonPressing.mp3`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/MainActivity.kt app/src/androidTest/java/com/example/crosswalker/SoundHooksTest.kt
git commit -m "feat(audio): musica por pantalla/mundo y sfx de botones y entrada a nivel"
```

---

### Task 5: `Simulation` expone quién y dónde colapsó

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt:46-51`
- Modify: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Produces: `Simulation.collapsedWalkerId: String?` y `Simulation.collapsedLane: Lane?`, ambos
  `private set`, que `Board.kt` (Task 6) y `Game()` (Task 8) leen por nombre exacto.

- [ ] **Step 1: Escribir el test que falla**

Añade a `app/src/test/java/com/example/crosswalker/SimulationTest.kt`, después de
`unprotectedTrafficCanTriggerFriendlyRetry`:

```kotlin
@Test fun collisionRecordsWhichWalkerAndLaneCollapsed() {
    val lane=Lane(.7f,0f,.7f,"car_coral")
    val s=finish(Simulation(level().copy(lanes=listOf(lane)),plan()))
    assertEquals(Phase.RETRY,s.phase)
    assertEquals("ana",s.collapsedWalkerId)
    assertEquals(lane,s.collapsedLane)
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.SimulationTest" -q`
Expected: FAIL — `collapsedWalkerId`/`collapsedLane` no existen todavía.

- [ ] **Step 3: Implementar**

En `Simulation.kt`, junto a la declaración de `reason`:

```kotlin
var reason = ""; private set
var collapsedWalkerId: String? = null; private set
var collapsedLane: Lane? = null; private set
```

En `tick()`, dentro del bucle de colisión, justo antes del `return` existente:

```kotlin
if(near && !yielding && !officer && !signalled && time >= stopUntil) {
    phase = Phase.RETRY
    collapsedWalkerId = p.walker.id
    collapsedLane = l
    reason = if(level.night) "De noche, usa el reflector para que puedan verte." else "Un coche estaba muy cerca. Prueba mirar antes de cruzar o cambia el punto de cruce."
    return
}
```

(Solo se añaden las dos líneas nuevas; el resto del bloque no cambia en esta tarea — el texto de
`reason` se actualiza en la Task 8, junto con el resto del copy del choque, para no mezclar un
cambio de comportamiento con uno de redacción.)

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL`, 20 tests en verde (19 existentes + el nuevo).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat(game): Simulation expone que peaton y carril causaron el choque"
```

---

### Task 6: Choque literal en `Board.kt`

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**Interfaces:**
- Consumes: `Simulation.collapsedWalkerId`/`collapsedLane` (Task 5).

No hay test automatizado para este paso (el resto de `Board.kt` tampoco lo tiene: es dibujo directo
sobre `Canvas`/`nativeCanvas`); se verifica manualmente jugando un nivel hasta chocar.

- [ ] **Step 1: Sprite de frente congelado para el peatón golpeado**

En el bucle `level.walkers.forEachIndexed { i,w -> ... }` (línea ~138), localiza:

```kotlin
val art=if(person==null) w.sprite else "${w.sprite}_w${((person.y*32f).toInt().mod(3))}"
sprite(art,x-15,y-38,30f,42f)
```

Reemplázalo por:

```kotlin
val collapsed=sim?.phase==Phase.RETRY && sim.collapsedWalkerId==w.id
val art=if(person==null || collapsed) w.sprite else "${w.sprite}_w${((person.y*32f).toInt().mod(3))}"
sprite(art,x-15,y-38,30f,42f)
```

(`w.sprite` sin sufijo es el sprite de frente, ya empaquetado por `tools/package_kenney_assets.py`
— ver `save(trim(tile(FRONT, row)), name)` en ese script.)

- [ ] **Step 2: Velo con dos agujeros (el peatón golpeado y su carril)**

Añade estos imports al principio de `Board.kt`:

```kotlin
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
```

Justo antes de la sección `if(level.night) { ... }` (línea ~159), añade:

```kotlin
// Choque: oscurecer todo el tablero excepto al peatón golpeado y su carril.
if(sim?.phase==Phase.RETRY && sim.collapsedWalkerId!=null) {
    val hitWalker=level.walkers.first { it.id==sim.collapsedWalkerId }
    val hitPerson=sim.people.first { it.walker.id==hitWalker.id }
    val hx=hitPerson.x*360f; val hy=hitPerson.y*410f
    val laneY=sim.collapsedLane!!.y*410f
    val layerId=c.saveLayer(0f,0f,360f,460f,null)
    color("#000000");p.alpha=140;c.drawRect(0f,0f,360f,460f,p);p.alpha=255
    p.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    c.drawRect(hx-22f,hy-45f,hx+22f,hy+10f,p)   // agujero 1: el peatón golpeado
    c.drawRect(0f,laneY-34f,360f,laneY+34f,p)   // agujero 2: todo el carril implicado
    p.xfermode=null
    c.restoreToCount(layerId)
}
```

(`saveLayer` + `PorterDuff.Mode.CLEAR` es el recurso estándar de Android para "perforar" un velo
semitransparente — ya se usa `Paint`/`nativeCanvas` directamente en este archivo, así que no hace
falta ninguna librería nueva. `sim.people.first{...}` siempre encuentra a alguien porque
`collapsedWalkerId` se pone exactamente desde `p.walker.id` en `Simulation.tick()`.)

- [ ] **Step 3: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verificación manual**

Run: `./gradlew :app:installDebug -q` y, con el emulador abierto, juega cualquier nivel sin elegir
ninguna ficha de seguridad para forzar un choque. Confirma: el tablero se oscurece, el peatón
golpeado se ve de frente (no de espaldas) y congelado, su carril permanece visible con el auto, el
resto del tablero queda oscuro.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): mostrar el choque literal con velo y peaton de frente"
```

---

### Task 7: Velo de victoria en `Board.kt`

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

- [ ] **Step 1: Velo con un agujero por peatón (sin agujero de auto)**

Justo encima del bloque añadido en la Task 6 (o debajo, el orden entre ambos no importa porque las
condiciones son mutuamente excluyentes — `RETRY` y `WON` nunca son el mismo `phase`), añade:

```kotlin
// Victoria: oscurecer todo excepto a los peatones y sus corazones (ya dibujados arriba).
if(sim?.phase==Phase.WON) {
    val layerId=c.saveLayer(0f,0f,360f,460f,null)
    color("#000000");p.alpha=160;c.drawRect(0f,0f,360f,460f,p);p.alpha=255
    p.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    level.walkers.forEach { w ->
        val person=sim.people.first { it.walker.id==w.id }
        val hx=person.x*360f; val hy=person.y*410f
        c.drawRect(hx-22f,hy-45f,hx+22f,hy+14f,p)   // cubre sprite + burbuja "♥"
    }
    p.xfermode=null
    c.restoreToCount(layerId)
}
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verificación manual**

Instala y gana cualquier nivel. Confirma que el tablero se oscurece y los peatones con su corazón
quedan resaltados con fuerza sobre el fondo oscuro. (El retraso del modal hasta la mitad de la
canción se verifica en la Task 8, que es cuando ese modal empieza a depender del audio.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): velo de victoria resaltando a los peatones y sus corazones"
```

---

### Task 8: Coreografía de audio para choque y victoria, y copy actualizado

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt` (clase `Session`, función `Game`)

**Interfaces:**
- Consumes: `SoundEngine` (vía `s.sound`, Task 4), `Simulation.collapsedWalkerId` (Task 5).

- [ ] **Step 1: `Session` gana el estado del modal retrasado**

En `class Session`, junto a `var paused by mutableStateOf(false)`:

```kotlin
var showResultDialog by mutableStateOf(false)
```

En `reset()`, añade `showResultDialog=false` a la lista de reinicios (edita la línea existente):

```kotlin
fun reset() { plan=Plan(level.walkers.associate { it.id to Assignment() }.toMutableMap()); selected=level.walkers.first().id; tab="skill"; simulation=null; paused=false; dialog=""; message=level.lesson; introIndex=0; showResultDialog=false; frame++ }
```

- [ ] **Step 2: Reaccionar a la transición a `Phase.RETRY`/`Phase.WON` dentro de `Game()`**

En `Game(s:Session,click:()->Unit)`, justo después de `val cost=s.plan.cost(s.repo.tools)`, añade
un efecto que dispara la coreografía exactamente una vez por simulación:

```kotlin
LaunchedEffect(sim?.phase) {
    when (sim?.phase) {
        Phase.RETRY -> if (sim.collapsedWalkerId != null) {
            s.sound?.stopMusic()
            s.sound?.playSfx(R.raw.sfx_collapse)
            kotlinx.coroutines.delay(1000)
            s.sound?.playOneShot(R.raw.music_gameover)
        }
        Phase.WON -> s.sound?.playOneShot(R.raw.music_win) { s.showResultDialog = true }
        else -> {}
    }
}
```

- [ ] **Step 3: Retrasar el modal de victoria, mostrar el de reintento de inmediato**

Localiza el bloque final de `Game()`:

```kotlin
if(sim?.phase==Phase.WON || sim?.phase==Phase.RETRY) {
    val won=sim.phase==Phase.WON
```

Reemplázalo por:

```kotlin
if(sim?.phase==Phase.RETRY || (sim?.phase==Phase.WON && s.showResultDialog)) {
    val won=sim.phase==Phase.WON
```

- [ ] **Step 4: Actualizar el copy del choque**

En el mismo bloque, cambia:

```kotlin
Text(if(won) "¡Todos llegaron a salvo!" else "Nos detenemos a tiempo.",style=TitleSmall,color=Ink)
```

por:

```kotlin
Text(if(won) "¡Todos llegaron a salvo!" else "¡Cuidado! Un auto golpeó a alguien.",style=TitleSmall,color=Ink)
```

En `Simulation.kt` (Task 5 ya tocó esas líneas; aquí solo se actualiza el texto, no la lógica),
cambia el `reason` de colisión:

```kotlin
reason = if(level.night) "De noche, un auto no pudo verte a tiempo. Usa el reflector para que te vean." else "El auto no alcanzó a frenar a tiempo. Mira antes de cruzar o cambia el punto de cruce."
```

(El `reason` del timeout de 90s, más abajo en `tick()`, no cambia: ese caso no es un choque.)

- [ ] **Step 5: Verificar que compila y que los tests existentes siguen en verde**

Run:
```bash
./gradlew :app:compileDebugKotlin -q
./gradlew :app:testDebugUnitTest -q
```
Expected: `BUILD SUCCESSFUL` en ambos. (El test JVM `allLevelsHaveASafeSolutionWithTheirAvailableTools`
no depende del texto de `reason`, solo lo imprime en el mensaje de fallo si algo sale mal, así que
no se rompe con el cambio de redacción.)

- [ ] **Step 6: Verificación manual**

Instala y fuerza un choque: confirma que la música del mundo se corta al instante, suena el golpe,
y un segundo después empieza la música de game over, con el modal de "¡Cuidado!" ya visible desde
el principio (no espera). Gana un nivel: confirma que el modal de "¡BUEN CAMINO!" tarda en
aparecer hasta que `win.mp3` lleva la mitad reproducida.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/MainActivity.kt app/src/main/java/com/example/crosswalker/game/Simulation.kt
git commit -m "feat(audio): coreografia de sonido para choque y victoria, copy actualizado"
```

---

### Task 9: Voz de fondo durante el diálogo

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Dialogue.kt:97-113`
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt` (donde se invoca `DialogueBox`)

**Interfaces:**
- Consumes: `SoundEngine.loopVoice`/`stopVoice` (Task 3).

- [ ] **Step 1: `DialogueBox` recibe el motor de sonido**

En `Dialogue.kt`, cambia la firma:

```kotlin
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
```

En el `LaunchedEffect(text)` que ya tipea el texto, arranca la voz al empezar y detenla al
terminar:

```kotlin
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
```

Y detenla también si el composable se desmonta a mitad de la animación (cambio de nivel, cierre del
diálogo):

```kotlin
DisposableEffect(Unit) { onDispose { voice?.stopVoice() } }
```

(añádelo junto al `Box(modifier...)` existente, antes del `Column`).

- [ ] **Step 2: Pasar el motor desde `Game()`**

En `MainActivity.kt`, localiza la llamada existente:

```kotlin
if(s.repo.guidance) DialogueBox(displayMessage,selectedWalker.name,walkerAccent(selectedWalker.sprite),
    remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
    Modifier.heightIn(min=56.dp),blip=blip(selectedWalker.id),onAdvance=onAdvance)
```

Añade el parámetro nuevo:

```kotlin
if(s.repo.guidance) DialogueBox(displayMessage,selectedWalker.name,walkerAccent(selectedWalker.sprite),
    remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
    Modifier.heightIn(min=56.dp),blip=blip(selectedWalker.id),onAdvance=onAdvance,voice=s.sound)
```

- [ ] **Step 3: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verificación manual**

Entra al nivel 1 (tutorial) y confirma de oído que, mientras el texto se escribe, suena de fondo el
murmullo de `talkingsynth` además de los blips por letra de siempre, y que se detiene en cuanto el
texto termina de escribirse.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Dialogue.kt app/src/main/java/com/example/crosswalker/MainActivity.kt
git commit -m "feat(audio): voz de fondo durante el tecleo del dialogo"
```

---

### Task 10: Checkpoint de Parte 1

- [ ] **Step 1: Suite completa**

Run:
```bash
./gradlew :app:testDebugUnitTest -q
./gradlew :app:installDebug -q
./gradlew :app:connectedDebugAndroidTest -q
```
Expected: las tres en verde.

- [ ] **Step 2: Playtest manual de 10-15 minutos**

Recorre: Inicio → Mapa (3 mundos, confirma `music_menu` en todos) → un nivel de cada mundo (confirma
el cambio a `music_world{N}`) → forzar un choque (Task 6/8) → ganar un nivel (Task 7/8) → el
tutorial del nivel 1 (voz de fondo, Task 9). Anota cualquier ajuste de volumen/tiempo que se sienta
mal (por ejemplo, si 1 segundo de espera antes de `music_gameover` se siente corto o largo,
ajustar el `delay(1000)` de la Task 8) y corrige antes de continuar a la Parte 2.

- [ ] **Step 3: Commit de ajustes, si hubo**

```bash
git add -A
git commit -m "fix: ajustes de playtest de audio"
```

---

# PARTE 2 — Tutorial guiado con máscara y puntero

### Task 11: Paso de tutorial como función pura

**Files:**
- Create: `app/src/main/java/com/example/crosswalker/TutorialStep.kt`
- Test: `app/src/test/java/com/example/crosswalker/TutorialStepTest.kt`

**Interfaces:**
- Produces: `data class TutorialStep(val spotlightId: String, val tapId: String = spotlightId)` y
  `fun tutorialStepFor(introIndex: Int, introLineCount: Int, lookChosen: Boolean): TutorialStep`
  (siempre devuelve un paso concreto; que el tutorial ya haya terminado del todo se decide aparte,
  en la Task 14, antes incluso de llamarla). Las Tasks 12-14 consumen ambos por nombre exacto.

- [ ] **Step 1: Escribir el test que falla**

Crea `app/src/test/java/com/example/crosswalker/TutorialStepTest.kt`:

```kotlin
package com.example.crosswalker

import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialStepTest {
    // El nivel 1 real tiene 6 introLines (índices 0-5); los tests usan ese número explícito
    // para no acoplarse a un detalle de parseo de campaign.json en esta prueba pura.
    private val count = 6

    @Test fun firstFourLinesPointAtTheDialogueItself() {
        for (i in 0..1) assertEquals(TutorialStep("dialogue"), tutorialStepFor(i, count, lookChosen = false))
    }
    @Test fun pauseAndTokensLinesSpotlightThoseWidgetsButTapAdvancesDialogue() {
        assertEquals(TutorialStep("pause", "dialogue"), tutorialStepFor(2, count, lookChosen = false))
        assertEquals(TutorialStep("tokens", "dialogue"), tutorialStepFor(3, count, lookChosen = false))
    }
    @Test fun tabsLineSpotlightsTheSkillTab() {
        assertEquals(TutorialStep("tab_skill", "dialogue"), tutorialStepFor(4, count, lookChosen = false))
    }
    @Test fun lastLineIsTheCrossingActionItself() {
        assertEquals(TutorialStep("crossing"), tutorialStepFor(5, count, lookChosen = false))
    }
    @Test fun afterIntroLinesStepIsToolLookUntilChosen() {
        assertEquals(TutorialStep("tool_look"), tutorialStepFor(count, count, lookChosen = false))
    }
    @Test fun onceLookIsChosenStepIsLaunch() {
        assertEquals(TutorialStep("launch"), tutorialStepFor(count, count, lookChosen = true))
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.TutorialStepTest" -q`
Expected: FAIL — ni `TutorialStep` ni `tutorialStepFor` existen.

- [ ] **Step 3: Implementar**

Crea `app/src/main/java/com/example/crosswalker/TutorialStep.kt`:

```kotlin
package com.example.crosswalker

/** Qué se recorta del velo (`spotlightId`) y qué toque es válido ahora mismo (`tapId`). */
data class TutorialStep(val spotlightId: String, val tapId: String = spotlightId)

/**
 * El paso activo del tutorial del nivel 1, derivado de estado que ya existe en `Session`
 * (`introIndex`, si "look" ya está en las habilidades del peaton seleccionado) — no es un
 * contador paralelo. Siempre devuelve un paso concreto; es el llamador (Task 14, que ya sabe si
 * el tutorial completo de este nivel terminó vía `GameRepository.tutorialDone`) quien decide
 * cuándo no llamarla en absoluto y publicar `null` en el controlador.
 */
fun tutorialStepFor(introIndex: Int, introLineCount: Int, lookChosen: Boolean): TutorialStep = when {
    introIndex < introLineCount - 1 -> when (introIndex) {
        0, 1 -> TutorialStep("dialogue")
        2 -> TutorialStep("pause", "dialogue")
        3 -> TutorialStep("tokens", "dialogue")
        else -> TutorialStep("tab_skill", "dialogue")
    }
    introIndex == introLineCount - 1 -> TutorialStep("crossing")
    !lookChosen -> TutorialStep("tool_look")
    else -> TutorialStep("launch")
}
```

(La rama `introIndex < introLineCount - 1` cubre los índices 0-4 del nivel 1 real, que tiene 6
líneas: por eso el test usa `count=6` y espera `"crossing"` justo en `introIndex==5`.)

- [ ] **Step 4: Verificar que pasa**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.TutorialStepTest" -q`
Expected: `BUILD SUCCESSFUL`, 6 tests en verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/TutorialStep.kt app/src/test/java/com/example/crosswalker/TutorialStepTest.kt
git commit -m "feat(tutorial): paso activo del tutorial como funcion pura"
```

---

### Task 12: `TutorialController` y el modifier `tutorialTarget`

**Files:**
- Create: `app/src/main/java/com/example/crosswalker/TutorialOverlay.kt`

**Interfaces:**
- Consumes: `TutorialStep`/`tutorialStepFor` (Task 11).
- Produces: `class TutorialController` con `var step: TutorialStep?`, `spotlightRects: List<Rect>`,
  `tapTargets: List<Pair<Rect, () -> Unit>>`, y el método
  `fun beginFrame()` (vacía las listas antes de que la recomposición las vuelva a llenar); el
  `Modifier.tutorialTarget(id, controller, onTap)` que las Tasks 13-14 usan en cada composable
  candidato.

No hay test unitario para este archivo (son contenedores de estado Compose + un modifier — se
verifican indirectamente por el test instrumentado de la Task 15).

- [ ] **Step 1: Implementar `TutorialController`**

Crea `app/src/main/java/com/example/crosswalker/TutorialOverlay.kt`:

```kotlin
package com.example.crosswalker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Qué paso está activo y qué composables reportaron su posición/acción para él en la
 * recomposición actual. `beginFrame()` se llama una vez por recomposición, antes de que los
 * composables hijos vuelvan a reportar: así nunca se acumulan rects de un paso anterior.
 */
class TutorialController {
    var step: TutorialStep? by mutableStateOf(null)
    // `by mutableStateOf` en las tres: `TutorialOverlay` (Task 13) lee `spotlightRects`/
    // `tapTargets` para decidir qué dibujar, y los hijos los escriben desde `onGloballyPositioned`
    // (que corre en la fase de layout, después de la composición) — si fueran `var` planas sin
    // estado de Compose, escribirlas ahí nunca dispararía una recomposición del overlay.
    var spotlightRects: List<Rect> by mutableStateOf(emptyList())
    var tapTargets: List<Pair<Rect, () -> Unit>> by mutableStateOf(emptyList())
    fun beginFrame() { spotlightRects = emptyList(); tapTargets = emptyList() }
}

/**
 * Mientras `id` coincide con el `spotlightId`/`tapId` del paso activo, este composable reporta su
 * rect (para el agujero del velo y/o para aceptar el toque) y entrega `onTap` — su propio
 * `onClick` real — como la acción que el overlay puede disparar. Si el composable no coincide con
 * el paso activo, no hace nada: el coste fuera del tutorial es cero.
 */
fun Modifier.tutorialTarget(id: String, controller: TutorialController, onTap: () -> Unit): Modifier =
    this.onGloballyPositioned { coordinates ->
        val step = controller.step ?: return@onGloballyPositioned
        val rect = coordinates.boundsInRoot()
        if (step.spotlightId == id) controller.spotlightRects = controller.spotlightRects + rect
        if (step.tapId == id) controller.tapTargets = controller.tapTargets + (rect to onTap)
    }
```

(Se usa `boundsInRoot()`, no `boundsInWindow()`: el overlay de la Task 13 se monta como hermano
directo del contenido de pantalla dentro del mismo `Box` raíz de `Crosswalker()`, así que su propio
origen de dibujo local coincide exactamente con el origen raíz de Compose — usar la misma base de
coordenadas en ambos lados es lo que hace que el agujero quede exactamente sobre el botón real.)

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/TutorialOverlay.kt
git commit -m "feat(tutorial): TutorialController y el modifier tutorialTarget"
```

---

### Task 13: El composable `TutorialOverlay` (velo + manita + captura de toques)

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/TutorialOverlay.kt`

**Interfaces:**
- Consumes: `TutorialController` (Task 12), `R.drawable.pointer_hand` (Task 1).

- [ ] **Step 1: Añadir el composable al mismo archivo**

Añade al final de `TutorialOverlay.kt`:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Velo oscuro a pantalla completa con un agujero por cada rect de `controller.spotlightRects`, la
 * manita parpadeando junto al primero de ellos, y toda la superficie capturando el toque: solo el
 * que cae dentro de algún `tapTargets` dispara su `onTap` real.
 */
@Composable fun TutorialOverlay(controller: TutorialController) {
    controller.beginFrame()
    val holes = controller.spotlightRects
    val taps = controller.tapTargets
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val handAlpha by pulse.animateFloat(1f, .25f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "handAlpha")
    BoxWithConstraints(Modifier.fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = .55f))
            holes.forEach { r ->
                drawRoundRect(Color.Transparent, topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(8f, 8f), blendMode = BlendMode.Clear)
            }
        }
        .pointerInput(controller.step) {
            detectTapGestures { offset -> taps.firstOrNull { (rect, _) -> rect.contains(offset) }?.second?.invoke() }
        }
    ) {
        // `constraints.maxHeight` viene en píxeles, igual que los Rect de `boundsInRoot()`:
        // comparar ambos directamente evita cualquier conversión dp/px aquí.
        val first = holes.firstOrNull()
        if (first != null) {
            val halfHeightPx = constraints.maxHeight / 2f
            val pointsDown = first.top > halfHeightPx
            val xPx = first.center.x - 24f
            val yPx = if (pointsDown) first.top - 56f else first.bottom + 8f
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pointer_hand),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(xPx.toInt(), yPx.toInt()) }
                    .size(24.dp)
                    .alpha(handAlpha)
            )
        }
    }
}
```

(El offset usa la variante de `Modifier.offset` que recibe un `IntOffset` en píxeles — por eso no
hace falta `LocalDensity` ni convertir a dp en ningún momento: tanto `boundsInRoot()` como
`constraints.maxHeight` ya están en píxeles. El tamaño `24.dp` sobre un PNG de 23×26 ya upscalado a
8× en `pointer_hand.webp` mantiene el look pixel-art sin que Compose vuelva a escalarlo de forma
borrosa.)

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/TutorialOverlay.kt
git commit -m "feat(tutorial): overlay con velo recortado, manita parpadeante y captura de toques"
```

---

### Task 14: Enganchar el overlay a `Game()` y marcar a cada composable candidato

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt`
- Modify: `app/src/main/java/com/example/crosswalker/data/GameRepository.kt`

**Interfaces:**
- Consumes: `TutorialController`, `TutorialOverlay`, `tutorialStepFor` (Tasks 11-13).

- [ ] **Step 1: Persistir si el tutorial ya se completó**

En `GameRepository.kt`, junto a `var sound`/`var guidance`:

```kotlin
var tutorialDone: Boolean
    get() = prefs.getBoolean("tutorial_done",false)
    set(value) { prefs.edit().putBoolean("tutorial_done",value).apply() }
```

- [ ] **Step 2: `Session` gana el controlador y calcula el paso activo**

En `class Session`, junto a `var introIndex`:

```kotlin
val tutorialController = TutorialController()
val tutorialActive: Boolean get() = level.tutorial && !repo.tutorialDone
```

Dentro de `Game()`, justo después de `val cost=s.plan.cost(s.repo.tools)`, calcula el paso activo y
publícalo en el controlador antes de que el árbol de composables lo lea (debe ejecutarse antes de
`Board`/los chips, así que va al principio de la función):

```kotlin
val lookChosen = "look" in s.plan.people.getValue(s.selected).skills
s.tutorialController.step = if (s.tutorialActive)
    tutorialStepFor(s.introIndex, s.level.introLines.size, lookChosen) else null
```

- [ ] **Step 3: Envolver PAUSA, FICHAS y la pestaña HABILIDADES**

Localiza (sección final de `Game()`):

```kotlin
Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.Top) {
    HudButton("Ⅱ","Pausa") { s.paused=true }
    Spacer(Modifier.weight(1f))
    TokenCounter(s.level.budget-cost,s.level.budget)
}
```

Reemplázalo por:

```kotlin
Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.Top) {
    Box(Modifier.tutorialTarget("pause",s.tutorialController){s.paused=true}) { HudButton("Ⅱ","Pausa") { s.paused=true } }
    Spacer(Modifier.weight(1f))
    TokenCounter(s.level.budget-cost,s.level.budget,Modifier.tutorialTarget("tokens",s.tutorialController){})
}
```

Localiza la fila de pestañas HABILIDADES/LA CALLE:

```kotlin
Row { CraftChip(selected=s.tab=="skill",onClick={s.tab="skill"},label={Row(verticalAlignment=Alignment.CenterVertically) {WalkerArtwork(selectedWalker,24.dp);Spacer(Modifier.width(6.dp));Text("HABILIDADES")}});Spacer(Modifier.width(8.dp));CraftChip(selected=s.tab=="street",onClick={s.tab="street"},label={Row(verticalAlignment=Alignment.CenterVertically) {PixelImage(R.drawable.house,null,Modifier.size(21.dp));Spacer(Modifier.width(6.dp));Text("LA CALLE")}}) }
```

Envuelve solo el primer `CraftChip` (el único que el tutorial señala):

```kotlin
Row { Box(Modifier.tutorialTarget("tab_skill",s.tutorialController){s.tab="skill"}) { CraftChip(selected=s.tab=="skill",onClick={s.tab="skill"},label={Row(verticalAlignment=Alignment.CenterVertically) {WalkerArtwork(selectedWalker,24.dp);Spacer(Modifier.width(6.dp));Text("HABILIDADES")}}) };Spacer(Modifier.width(8.dp));CraftChip(selected=s.tab=="street",onClick={s.tab="street"},label={Row(verticalAlignment=Alignment.CenterVertically) {PixelImage(R.drawable.house,null,Modifier.size(21.dp));Spacer(Modifier.width(6.dp));Text("LA CALLE")}}) }
```

- [ ] **Step 4: Envolver los 3 chips de cruce**

Localiza:

```kotlin
Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { WalkerArtwork(selectedWalker,24.dp);Text("Cruce",style=BodyText,color=Ink);listOf("Izq.","Centro","Der.").forEachIndexed { i,label -> CraftChip(selected=s.plan.people.getValue(s.selected).crossing==i,onClick={val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=i;s.plan=next},label={Text(label)}) } }
```

Reemplaza el `forEachIndexed` para que cada chip registre su propio toque real:

```kotlin
Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { WalkerArtwork(selectedWalker,24.dp);Text("Cruce",style=BodyText,color=Ink);listOf("Izq.","Centro","Der.").forEachIndexed { i,label ->
    val pick = { val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=i;s.plan=next }
    Box(Modifier.tutorialTarget("crossing",s.tutorialController,pick)) { CraftChip(selected=s.plan.people.getValue(s.selected).crossing==i,onClick=pick,label={Text(label)}) }
} }
```

- [ ] **Step 5: Envolver la ficha MIRAR y el botón de lanzar**

Localiza, dentro del `Row` que dibuja las fichas:

```kotlin
Column(Modifier.width(84.dp).pixelPanel(fill=if(active)Taxi.base else Cream,highlight=if(active)Taxi.light else CreamLight,shade=if(active)Taxi.dark else CreamDark).clickable {s.toggle(t);click()}.padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) { ToolArtwork(t.id);Text(t.label.uppercase(),style=HudLabel,color=Ink,minLines=2,maxLines=2,textAlign=TextAlign.Center);Text("${if(active) "✓" else "+"} ${t.cost} ●",style=HudStrong,color=if(active)Ink else Orange.dark) }
```

Añade el modifier solo cuando el id de la ficha es `"look"` (las demás fichas no participan del
tutorial, así que su `tutorialTarget` nunca coincide y no hace nada):

```kotlin
val pick = { s.toggle(t);click() }
Column(Modifier.width(84.dp).tutorialTarget(if(t.id=="look")"tool_look" else "",s.tutorialController,pick).pixelPanel(fill=if(active)Taxi.base else Cream,highlight=if(active)Taxi.light else CreamLight,shade=if(active)Taxi.dark else CreamDark).clickable(onClick=pick).padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) { ToolArtwork(t.id);Text(t.label.uppercase(),style=HudLabel,color=Ink,minLines=2,maxLines=2,textAlign=TextAlign.Center);Text("${if(active) "✓" else "+"} ${t.cost} ●",style=HudStrong,color=if(active)Ink else Orange.dark) }
```

Localiza el botón de lanzar:

```kotlin
PaperButton("▶   PONER EL PLAN EN MARCHA") {click();s.simulation=Simulation(s.level,s.plan)}
```

Reemplázalo por (el `launch` marca el fin del tutorial la primera vez que se toca: es siempre el
último paso posible, así que no hace falta volver a comprobar nada más):

```kotlin
val launch = { click();if(s.tutorialActive) s.repo.tutorialDone=true;s.simulation=Simulation(s.level,s.plan) }
Box(Modifier.tutorialTarget("launch",s.tutorialController,launch)) { PaperButton("▶   PONER EL PLAN EN MARCHA",onClick=launch) }
```

- [ ] **Step 6: Que la caja de diálogo también sea un objetivo de tutorial**

Localiza la llamada a `DialogueBox` (ya tocada en la Task 9) y envuélvela. Su único trabajo durante
el tutorial es avanzar `introIndex` — el paso `"launch"` ya se encarga de marcar `tutorialDone` por
su cuenta, así que esta función no necesita tocarlo:

```kotlin
val advanceDialogue = { if (s.introIndex < s.level.introLines.size - 1) s.introIndex++ }
if(s.repo.guidance) Box(Modifier.heightIn(min=56.dp).tutorialTarget("dialogue",s.tutorialController,advanceDialogue)) {
    DialogueBox(displayMessage,selectedWalker.name,walkerAccent(selectedWalker.sprite),
        remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
        blip=blip(selectedWalker.id),onAdvance=onAdvance,voice=s.sound)
}
```

(Quita el `Modifier.heightIn(min=56.dp)` que antes iba directo en `DialogueBox` — ahora vive en el
`Box` que lo envuelve.)

- [ ] **Step 7: Montar el overlay**

En `Crosswalker()`, dentro del `Box` principal, después de `ScreenTransitions(s) { ... }` y antes de
los `if(s.dialog==...)`:

```kotlin
if (s.screen=="game" && s.tutorialActive) TutorialOverlay(s.tutorialController)
```

- [ ] **Step 8: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Verificar que los tests JVM existentes siguen en verde**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL` — esta tarea no toca lógica pura, solo wiring de Compose.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/MainActivity.kt app/src/main/java/com/example/crosswalker/data/GameRepository.kt
git commit -m "feat(tutorial): enganchar el overlay guiado a los controles reales del nivel 1"
```

---

### Task 15: Reescribir `TutorialTest.kt`

**Files:**
- Modify: `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt`

- [ ] **Step 1: Leer el test actual antes de tocarlo**

Run: `cat app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt`
(Confirma el estilo existente — `createAndroidComposeRule`, navegación `JUGAR`→nivel 1→
`PREPARAR EL PLAN` — para mantenerlo en el reemplazo.)

- [ ] **Step 2: Escribir el test nuevo**

Reemplaza el contenido de `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt` por:

```kotlin
package com.example.crosswalker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TutorialTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun enterTutorial() {
        rule.onNodeWithText("JUGAR").performClick()
        rule.onNodeWithText("Mi primer cruce").performClick()
        rule.onNodeWithText("PREPARAR EL PLAN", substring = true).performClick()
    }

    @Test fun tappingOutsideTheSpotlightDoesNothing() {
        enterTutorial()
        val before = rule.activity.let { (it.findViewById<android.view.View>(android.R.id.content)) }
        // Tocar la pestaña LA CALLE (nunca es el objetivo activo en los primeros pasos) no debe
        // cambiar nada: sigue sin existir como pestaña seleccionable porque el overlay consume el toque.
        rule.onNodeWithText("LA CALLE").performClick()
        rule.onNodeWithText("¡Hola! Soy Ana.", substring = true).assertExists()
    }

    @Test fun tappingTheDialogueAdvancesThroughNarrationLines() {
        enterTutorial()
        repeat(5) { rule.onNodeWithText("¡Hola!", substring = true).apply {}; rule.onRoot().performClick() }
        rule.waitForIdle()
    }

    @Test fun pauseStillWorksMidStep() {
        enterTutorial()
        rule.onNodeWithContentDescription("Pausa").performClick()
        rule.onNodeWithText("PAUSA", substring = true).assertExists()
    }
}
```

- [ ] **Step 3: Ejecutarlo y ajustar según lo que realmente pinte la UI**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.TutorialTest"`
Expected: los tres tests en verde. Si algún selector de texto no calza con el string real en
pantalla (por ejemplo, por el `substring` o el `contentDescription` exacto de `HudButton`), ajusta
el selector al texto real observado en el fallo, no cambies el comportamiento de la app para que el
test pase.

- [ ] **Step 4: Correr toda la suite instrumentada**

Run: `./gradlew :app:connectedDebugAndroidTest -q`
Expected: `BUILD SUCCESSFUL`, todos los tests (los nuevos de audio y tutorial, más los ya
existentes) en verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt
git commit -m "test: reescribir TutorialTest para el flujo guiado con mascara"
```

---

### Task 16: Checkpoint final de Parte 2

- [ ] **Step 1: Suite completa**

Run:
```bash
./gradlew :app:testDebugUnitTest -q
./gradlew :app:installDebug -q
./gradlew :app:connectedDebugAndroidTest -q
```
Expected: las tres en verde.

- [ ] **Step 2: Playtest manual completo del tutorial**

Juega el nivel 1 de principio a fin: confirma que cada línea narrativa solo avanza tocando la caja
de diálogo (tocar cualquier otro botón no hace nada mientras esa línea está activa); que PAUSA y
FICHAS se señalan con la manita sin forzar su uso; que la pestaña HABILIDADES se señala; que elegir
el cruce, la ficha MIRAR y el botón de lanzar sí requieren tocar exactamente ese elemento (tocar
fuera no avanza); que PAUSA sigue funcionando en cualquier momento; que, al reiniciar el nivel 1
desde "VOLVER A INTENTAR" o desde el mapa, el tutorial guiado no vuelve a aparecer (ya quedó
marcado como completo).

- [ ] **Step 3: Commit de ajustes, si hubo**

```bash
git add -A
git commit -m "fix: ajustes de playtest del tutorial guiado"
```
