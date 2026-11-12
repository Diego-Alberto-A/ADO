# Audio real y overlay de tutorial guiado — Design

**Goal:** Integrar el pack de assets `latestAssets/pointingHand` (música por mundo, efectos de
interfaz, voz de diálogo, efecto de choque y de victoria) y reemplazar el tutorial actual
(narración + avance libre) por un flujo guiado: velo oscuro que resalta solo el elemento que el
jugador debe tocar, una manita que parpadea sobre él, y toques bloqueados a cualquier otro lugar
hasta completar ese paso.

**Architecture:** Dos subsistemas nuevos, independientes entre sí:
1. Un motor de audio (`SoundEngine`, `MediaPlayer` + `SoundPool`, sin dependencias nuevas) que
   sustituye el `ToneGenerator` para música, SFX de interfaz y los dos eventos narrativos (choque,
   victoria); los blips de diálogo por letra se quedan como están, con una voz de fondo añadida.
2. Un `TutorialOverlay` genérico (velo con agujero + puntero + enrutamiento de toques) que
   reemplaza el avance libre del nivel 1 por una secuencia de pasos obligatorios.

Ambos reutilizan la misma idea visual — velo semitransparente con un hueco recortado — pero en dos
superficies de dibujo distintas: dentro de `Board.kt` (canvas nativo `android.graphics.Canvas`, ya
usado para noche/choque) para el choque y la victoria, y en una capa Compose normal (`graphicsLayer`
+ `BlendMode.Clear`) para el overlay de tutorial, porque los botones que debe señalar viven fuera
del `Canvas` del tablero.

**Tech Stack:** Kotlin, Jetpack Compose, Android `MediaPlayer`/`SoundPool` (API 24+, sin librerías
nuevas), `org.json` (ya en uso).

**Spec:** este documento.

## Global Constraints

- No se generan assets nuevos: todo el arte y audio sale de `latestAssets/pointingHand` y de los
  sprites ya empaquetados (`walker_*` de frente ya existen; el corazón "♥" de victoria también).
- `latestAssets/` nunca se commitea (se añade a `.gitignore`, igual que `NewAssets/`); solo los
  archivos procesados llegan a `app/src/main/res` y `app/src/main/assets/licenses`.
- `SFX.zip` (explosiones, saltos, power-ups) no se integra ni se copia: no corresponde a ninguna
  mecánica de este juego.
- El interruptor "Sonidos" existente en Ajustes sigue siendo el único control: gobierna música y
  SFX por igual. No se añade un control de volumen separado.
- El tono del juego sigue siendo apropiado para niños: el choque se muestra de forma literal (como
  pidió el usuario) pero sin un retrato dedicado ni sangre — se reusa el sprite de frente existente,
  congelado, con el velo oscuro como único recurso dramático.
- El overlay de tutorial es genérico pero solo el nivel 1 lo usa hoy (único `tutorial=true`).
  PAUSA y el botón de retroceso del sistema siempre funcionan, incluso durante un paso resaltado.

## File Structure

- `.gitignore` — gana una línea para `/latestAssets`.
- `tools/package_audio_assets.py` — nuevo. Copia y renombra audio de
  `latestAssets/pointingHand/audios/*` a `app/src/main/res/raw/*`, y
  `latestAssets/pointingHand/images/pointer/hand.png` a
  `app/src/main/res/drawable-nodpi/pointer_hand.webp` (upscalado nearest-neighbour, igual que los
  demás sprites). Escribe `app/src/main/assets/licenses/audio-credits.txt` con las atribuciones
  requeridas (opengameart.org, Alex McCulloch) y las permisivas, a partir de los `.txt` de licencia
  del pack.
- `app/src/main/res/raw/` — nuevo: `music_menu.ogg`, `music_world1.mp3`, `music_world2.mp3`,
  `music_world3.mp3`, `sfx_button.mp3`, `sfx_enter_level.mp3`, `sfx_collapse.wav`,
  `music_gameover.mp3`, `music_win.mp3`, `voice_talking.mp3`.
- `app/src/main/res/drawable-nodpi/pointer_hand.webp` — nuevo.
- `app/src/main/java/com/example/crosswalker/audio/SoundEngine.kt` — nuevo: motor de audio.
- `app/src/main/java/com/example/crosswalker/game/Simulation.kt` — `Simulation` gana
  `collapsedWalkerId: String?` y `collapsedLane: Lane?`, puestos en el momento de la colisión;
  `reason` cambia de texto (reconoce el choque en vez de "nos detuvimos a tiempo").
- `app/src/main/java/com/example/crosswalker/Board.kt` — dibuja el velo-con-agujero para
  `Phase.RETRY` (resalta al peatón golpeado y su carril) y para `Phase.WON` (resalta a todos los
  peatones y sus corazones); el peatón golpeado cambia a su sprite de frente.
- `app/src/main/java/com/example/crosswalker/Dialogue.kt` — `DialogueBox` arranca/detiene la voz de
  fondo (`voice_talking`) junto con el typewriter existente.
- `app/src/main/java/com/example/crosswalker/TutorialOverlay.kt` — nuevo: `TutorialStep`,
  `TutorialController` (estado: paso actual, rect del objetivo activo) y el composable
  `TutorialOverlay` (velo + puntero + enrutamiento de toques).
- `app/src/main/java/com/example/crosswalker/MainActivity.kt` — `Session` gana el estado del
  tutorial guiado (reemplaza el uso simple de `introIndex` para el nivel 1); `Crosswalker()`
  instancia `SoundEngine` y reacciona a cambios de pantalla/mundo/nivel para música; `Game()`
  reporta los rects de PAUSA/pestañas/cruce/ficha MIRAR/botón de lanzar y gatilla el retraso de
  "mitad de canción" antes de mostrar el modal de victoria.
- `app/src/test/java/com/example/crosswalker/SimulationTest.kt` — nuevos tests para
  `collapsedWalkerId`/`collapsedLane`.
- `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt` (ya existe, se reescribe) —
  ahora verifica que tocar fuera del objetivo activo no hace nada y que el paso correcto avanza.

## Section A — Pipeline de assets

`tools/package_audio_assets.py` sigue el patrón de `package_kenney_assets.py`: constantes de rutas
origen/destino, una función por tipo de archivo. No recomprime audio (ya viene comprimido); solo
copia y renombra. El `.wav` (`collapsePedestrian.wav`) se copia tal cual — `SoundPool`/`MediaPlayer`
decodifican WAV sin problema. `hand.png` (23×26) se trata como el resto de sprites pixel-art: PNG
de entrada, salida WebP sin pérdida, escalado nearest-neighbour por el mismo factor que usa
`package_kenney_assets.py` para iconos pequeños (8×).

Mapeo origen → destino:

| Origen | Destino |
|---|---|
| `audios/menuSong/jump and run - tropics.ogg` | `res/raw/music_menu.ogg` |
| `audios/world1Song/Market on the Sea - MP3.mp3` | `res/raw/music_world1.mp3` |
| `audios/world2Song/TalkingCuteChiptune.mp3` | `res/raw/music_world2.mp3` |
| `audios/world3Sonng/RPG_Town_Ambience_1.mp3` | `res/raw/music_world3.mp3` |
| `audios/UISOUNDS/buttonPressing.mp3` | `res/raw/sfx_button.mp3` |
| `audios/UISOUNDS/enteringLevel.mp3` | `res/raw/sfx_enter_level.mp3` |
| `audios/gameOverEffect/collapsePedestrian.wav` | `res/raw/sfx_collapse.wav` |
| `audios/gameOverEffect/songGameOver.mp3` | `res/raw/music_gameover.mp3` |
| `audios/winningEffect/win.mp3` | `res/raw/music_win.mp3` |
| `audios/characterSpeaking/talkingsynth.mp3` | `res/raw/voice_talking.mp3` |
| `images/pointer/hand.png` | `res/drawable-nodpi/pointer_hand.webp` |

`audios/SFX.zip` no aparece en esta tabla: se ignora explícitamente.

## Section B — `SoundEngine`

Una clase plana (no `ViewModel`, vive en `remember{}` dentro de `Crosswalker()`, con el mismo ciclo
de vida que el `ToneGenerator` de hoy — liberada en `onDispose`):

```kotlin
class SoundEngine(private val context: Context, private val enabled: () -> Boolean) {
    private var music: MediaPlayer? = null          // una sola pista de fondo a la vez
    private var voice: MediaPlayer? = null           // murmullo de diálogo, independiente de music
    private val pool = SoundPool.Builder().setMaxStreams(4).build()
    private val shortClips: Map<Int, Int>            // resId -> soundId cargado en el pool

    fun playMusic(@RawRes resId: Int, loop: Boolean = true) { /* detiene la anterior, arranca nueva */ }
    fun stopMusic() { }
    fun playOneShot(@RawRes resId: Int, onHalfway: (() -> Unit)? = null): MediaPlayer
    fun playSfx(@RawRes resId: Int) { if (enabled()) pool.play(...) }
    fun loopVoice(@RawRes resId: Int) { }
    fun stopVoice() { }
    fun release() { }
}
```

- `playMusic`/`stopMusic` cambian de pista sin crossfade (igual que el resto de la app privilegia
  simplicidad sobre pulido sonoro).
- `playOneShot` se usa para `music_gameover` y `music_win`: arma un `MediaPlayer`, y si se pide
  `onHalfway`, en `setOnPreparedListener` programa (con `Handler`/coroutine `delay`) el callback a
  `duration/2` milisegundos.
- `playSfx` usa `SoundPool` para los clips cortos (`sfx_button`, `sfx_enter_level`,
  `sfx_collapse`) — baja latencia, pueden solaparse.
- `loopVoice`/`stopVoice` usan un `MediaPlayer` propio en bucle, independiente de `music`, para que
  la voz de diálogo no corte la música de fondo del mundo.
- Todo método de reproducción consulta `enabled()` (que lee `s.repo.sound`) antes de sonar.

## Section C — Mapeo de eventos

- **Pantallas:** `menuSong` suena en `"home"` y `"map"` (cualquier pestaña de Mundo). Al entrar a
  `"game"`, cambia a `music_world{N}` según `level.world`. Al volver al mapa, vuelve a `menuSong`.
  Esto se dispara con un `LaunchedEffect(s.screen, s.level.world)` dentro de `Crosswalker()`.
- **Botones:** `click()` (ya usado en todas partes) llama `sound.playSfx(R.raw.sfx_button)` en vez
  del beep de `ToneGenerator`. El beep del `ToneGenerator` para clics desaparece; el `ToneGenerator`
  se conserva solo para los blips de diálogo por letra (necesitan el tono distinto por hablante que
  ya existe en `VOICES`).
- **Entrar a nivel:** `Session.enter(l)` dispara `sound.playSfx(R.raw.sfx_enter_level)` justo antes
  de la transición.
- **Diálogo:** `DialogueBox` arranca `sound.loopVoice(R.raw.voice_talking)` en el mismo
  `LaunchedEffect(text)` que ya tipea el texto, y lo detiene cuando `revealed>=text.length` o al
  desmontarse. Los blips DTMF por letra (`blip()`) se quedan sin cambios, sonando encima.
- **Choque (`Phase.RETRY` por colisión, no por timeout):** al entrar en esa rama de `tick()`,
  `Simulation` guarda `collapsedWalkerId`/`collapsedLane` antes de devolver. El composable `Game()`
  detecta la transición a `Phase.RETRY` con colisión (`collapsedWalkerId != null`) y: para la
  música del mundo (`sound.stopMusic()`), reproduce `sfx_collapse` de inmediato, y tras 1 segundo
  (`delay(1000)` en un `LaunchedEffect`) reproduce `music_gameover` con `playOneShot` (sin
  `onHalfway`: el modal de reintento aparece de inmediato, como hoy).
- **Victoria (`Phase.WON`):** `Game()` detecta la transición y llama
  `sound.playOneShot(R.raw.music_win, onHalfway = { s.showResultDialog = true })`. El bloque que
  hoy muestra el `PaperDialog` de resultado (`if(sim?.phase==Phase.WON || sim?.phase==Phase.RETRY)`)
  pasa a condicionarse también a `s.showResultDialog` cuando `phase==WON` (para `RETRY` se muestra
  de inmediato, sin esperar). `s.showResultDialog` se reinicia a `false` en `Session.reset()`.

## Section D — Choque literal en `Board.kt`

`Simulation.tick()` ya detecta la colisión dentro de un bucle anidado
(`for(p in people) { ... for(l in level.lanes) { if(near && ...) { phase=Phase.RETRY; ... } } }`).
En ese punto, antes del `return`, se añade:

```kotlin
collapsedWalkerId = p.walker.id
collapsedLane = l
```

(ambos `var ...; private set`, expuestos de solo lectura, igual que `reason`).

En `Board.kt`, dentro del bucle de peatones (línea ~138), si
`sim?.phase==Phase.RETRY && w.id==sim.collapsedWalkerId`: en vez de `"${w.sprite}_w${...}"` se dibuja
`w.sprite` (el sprite de frente, ya existente) en la posición congelada donde ocurrió el golpe, sin
avanzar más. Después de dibujar normalmente lanes/casas/peatones (el orden actual no cambia), se
añade, justo antes de la sección de noche:

```kotlin
if (sim?.phase == Phase.RETRY && sim.collapsedWalkerId != null) {
    val saveId = c.saveLayer(0f, 0f, 360f, 460f, null)
    color("#000000"); p.alpha = 140; c.drawRect(0f, 0f, 360f, 460f, p); p.alpha = 255
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    // Agujero 1: el peatón golpeado.
    c.drawRect(walkerHoleRect, p)
    // Agujero 2: el carril/auto implicado.
    c.drawRect(laneHoleRect, p)
    p.xfermode = null
    c.restoreToCount(saveId)
}
```

Esto reutiliza exactamente el patrón `saveLayer` + `PorterDuff.CLEAR` que ya es idiomático en
Android para "perforar" un velo — no hace falta una librería nueva, y encaja con el estilo de
`Board.kt` (dibuja todo con `android.graphics.Canvas`/`Paint` directo).

**Copy actualizado** (en `GameRepository`/`campaign.json` no cambia nada — estos textos viven en
`Simulation.kt` como literales):
- `reason` cuando hay colisión: `"$NOMBRE no alcanzó a cruzar a tiempo: el auto no pudo frenar. Mira antes de cruzar o cambia el punto de cruce."` (noche: variante con "usa el reflector").
- El texto fijo en `Game()` para `!won`: cambia de `"Nos detenemos a tiempo."` a
  `"¡Cuidado! Un auto golpeó a alguien."` — sigue sin ser gráfico, pero ya no contradice el velo+
  sprite de frente que se acaba de mostrar.

## Section E — Victoria en `Board.kt`

Mismo recurso `saveLayer`+`CLEAR`, pero con tantos agujeros como peatones (todos terminan la carrera
a la vez en la práctica, pero el código itera `level.walkers` por si alguno sigue en pantalla) y SIN
agujero de auto. El agujero de cada peatón cubre su sprite y su burbuja "♥" ya existente (la
burbuja se sigue dibujando con el código actual; el velo simplemente se añade después). Disparado
por `sim?.phase == Phase.WON`.

## Section F — `TutorialOverlay`

**Datos:**

```kotlin
data class TutorialStep(val spotlightId: String, val tapId: String = spotlightId)
```

Ningún paso guarda su propia acción: cada composable candidato ya tiene su `onClick` real (PAUSA
pausa, el chip de cruce fija `crossing=i`, la ficha MIRAR llama `s.toggle(tool)`, DialogueBox avanza
`introIndex`...). El overlay no reimplementa esa lógica — solo decide, en base al id activo, qué
composable tiene permiso de recibir el toque esta vez. `spotlightId` y `tapId` casi siempre
coinciden (el objetivo que se señala es el que hay que tocar). Difieren solo en los pasos
puramente informativos (3-5 abajo): se señala PAUSA/FICHAS/la pestaña, pero el toque que de verdad
avanza sigue siendo la caja de diálogo. Un id puede corresponder a más de un composable a la vez
(las 3 pestañas de cruce comparten `"crossing"`): el controlador guarda listas, no un solo rect/
lambda, y acepta el toque que caiga dentro de cualquiera de ellos.

Lista hardcodeada en `MainActivity.kt` (no en `campaign.json`: solo existe para el nivel 1 hoy, y
forzar un esquema de datos genérico para un único caso sería sobre-ingeniería). Ocho pasos en total,
uno por cada `introLine` (5 narrativas/informativas, `introLines[0..4]`) más tres acciones reales:

1. `introLines[0]` → `spotlightId="dialogue"` ("¡Hola! Soy Ana.")
2. `introLines[1]` → `spotlightId="dialogue"` ("Vamos paso a paso.")
3. `introLines[2]` → `spotlightId="pause"`, `tapId="dialogue"` (resalta Ⅱ, no fuerza a pausar)
4. `introLines[3]` → `spotlightId="tokens"`, `tapId="dialogue"` (resalta el contador de FICHAS)
5. `introLines[4]` → `spotlightId="tab_skill"`, `tapId="dialogue"` (resalta la pestaña HABILIDADES)
6. `introLines[5]` ("Elige por dónde cruzo...") → `spotlightId=tapId="crossing"` (las 3 pestañas de
   cruce comparten el id; cualquiera de las tres es un toque válido, y ESE toque hace la elección
   real — no hay paso de diálogo aparte, esta línea ya es la acción)
7. tras `introDone` (fase `lesson`) → `spotlightId=tapId="tool_look"` (ficha MIRAR); tocarla ejecuta
   `s.toggle(tool)` de verdad
8. tras elegir MIRAR (fase `readyMessage`) → `spotlightId=tapId="launch"` (botón "PONER EL PLAN EN
   MARCHA"); tocarlo arranca la simulación

El paso activo (`controller.step`) no es un índice paralelo nuevo: se deriva de estado que ya
existe (`s.introIndex`, y si `"look"` ya está en las habilidades del peatón seleccionado) —
pasos 1-6 son `introIndex` 0 a 5, paso 7 es `introDone && "look" !in skills`, paso 8 es
`introDone && "look" in skills`. Esto evita llevar dos contadores de progreso del mismo tutorial.

**Reporte de posición + toque real:** cada composable candidato se envuelve con un modifier pequeño
que, mientras su id coincide con el paso activo, reporta su rect y entrega su propio `onClick` al
controlador — en vez de (o además de) su `clickable` normal:

```kotlin
fun Modifier.tutorialTarget(id: String, controller: TutorialController, onTap: () -> Unit) =
    this.onGloballyPositioned {
        val rect = it.boundsInWindow()
        val step = controller.step ?: return@onGloballyPositioned
        if (step.spotlightId == id) controller.spotlightRects += rect
        if (step.tapId == id) controller.tapTargets += rect to onTap
    }
```

`DialogueBox` se envuelve igual (`id="dialogue"`, `onTap` = lo que corresponda avanzar en ese punto:
incrementar `introIndex`, o nada si el paso ya es una acción real sin línea de diálogo propia).
`spotlightRects`/`tapTargets` se vacían y rellenan en cada recomposición del paso activo — los
composables cuyo id no coincide no hacen nada, así que el costo fuera del tutorial es cero.

**El overlay** (composable `TutorialOverlay`, montado en `Crosswalker()` al mismo nivel que los
`PaperDialog` existentes, visible cuando `s.level.tutorial && !s.tutorialDone`):

- Un `Box` a pantalla completa con `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
  .drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha=.55f));
  controller.spotlightRects.forEach { r -> drawRoundRect(Color.Transparent, topLeft=r.topLeft,
  size=r.size, blendMode=BlendMode.Clear) } }` — el mismo truco de velo-con-agujero que en
  `Board.kt`, en su variante Compose, con un agujero por rect de `spotlightRects` (normalmente 1;
  3 en el paso del cruce).
- La manita (`pointer_hand.webp`) posicionada junto al primer rect de `spotlightRects` (centrada
  encima si hay espacio arriba, debajo si el objetivo está en la franja superior de la pantalla),
  con una animación de alpha pulsante (`rememberInfiniteTransition`, igual patrón que
  `MoreTextArrow`).
- Captura de toques: `Modifier.pointerInput(controller.step) { detectTapGestures { offset ->
  controller.tapTargets.firstOrNull { (rect,_) -> rect.contains(offset) }?.let { (_,onTap) ->
  onTap(); sound.playSfx(R.raw.sfx_button) } } }` en el `Box` completo — un toque fuera de todos los
  rects registrados no hace nada (ni siquiera llega a los composables de abajo: el `Box` está encima
  en el árbol y consume el gesto). Cada `onTap` es el `onClick` real que ese composable ya tenía
  (fijar el cruce, alternar la ficha, pausar el diálogo, lanzar el plan): el overlay solo arbitra
  CUÁL de ellos puede dispararse ahora mismo, nunca reimplementa qué hace.
- PAUSA y el `BackHandler` del sistema permanecen activos siempre (no pasan por el overlay: la
  pausa congela el overlay igual que hoy congela el juego, y al reanudar se retoma el mismo paso).

**Fin del tutorial:** tras el paso `launch`, `s.tutorialDone=true` (persistido en
`GameRepository`/`SharedPreferences` para no repetirlo si el jugador reinicia el nivel 1 — hoy
`reset()` ya pone `introIndex=0`; se añade que si `tutorialDone` ya es `true`, el overlay no vuelve
a aparecer en reintentos del nivel 1, solo la primera vez).

## Testing

- `SimulationTest.kt`: un test que fuerza una colisión y verifica `collapsedWalkerId`/`collapsedLane`
  no nulos y correctos.
- `TutorialTest.kt` (instrumentado, reescrito): verifica que tocar fuera del rect del paso activo no
  cambia el estado (`introIndex`/plan intactos), y que tocar dentro sí avanza; que PAUSA sigue
  funcionando a mitad de un paso.
- No hay test automatizado de audio (requiere dispositivo/emulador con salida de audio real); se
  verifica manualmente en el playtest final, igual que se hizo con el rediseño de niveles.
