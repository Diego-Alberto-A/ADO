# Rediseño de intuitividad, jugabilidad y lecciones reales (ronda 2) Implementation Plan

**Goal:** Corregir problemas reales de intuitividad (no cosméticos), balance (`look` domina todo), y alinear cada mecánica con una lección de seguridad peatonal real y reconocida — todo pensado para niños de 5 a 13 años, con retroalimentación visual inmediata.

**Architecture:** Jubilar `eye` (redundante por diseño, no por accidente); un mapeo de íconos completo y correcto en `CraftArt.kt` (el real, no el campo JSON muerto); una mecánica nueva (`car_lights`) con la misma forma que `blindSpot`/`escort` de la ronda 1; ajustes de física de frenado y clima en el mismo estilo del motor existente; re-encuadre narrativo de 3 niveles hacia reglas reales citables; y una auditoría de necesidad por ficha en cada nivel que introduce algo nuevo.

**Tech Stack:** Kotlin (Simulation.kt, Board.kt, CraftArt.kt), JVM tests (JUnit), Pillow (Python) para los íconos nuevos — mismo flujo que las 8 decoraciones pixel-art de la sesión de rediseño anterior.

**Depende de / construye sobre:** `docs/design/plans/2026-11-12-level-and-card-redesign.md` (ronda 1, ya implementada: `Lane.blindSpot`, ficha `escort`, penalización de `impulsive`, fixes visuales del auto estacionado). Esta ronda retoca varias de esas decisiones (ver Tarea 8).

## Global Constraints

- Niveles 1-3: concepto intacto. Se les quita `eye` (ya era redundante ahí también) sin tocar su lección.
- Nivel 4 en adelante: terreno libre, mismos 3 mundos × 4 niveles, mismos IDs.
- Cada ficha nueva se enseña SOLA primero — nunca combinada con otra novedad en el mismo nivel. Antes de cerrar cada nivel que introduce algo, confirmar con un test: "sin la ficha nueva, falla; con ella, gana."
- Cada mecánica debe corresponder a una regla real de seguridad peatonal infantil (mirar antes de cruzar, no correr tras una pelota/mascota, nunca cruzar justo antes/después de un camión detenido, ser visto de noche, los autos deben prender luces de noche, la lluvia alarga el frenado, etc.) — nada de mecánica "porque sí".
- Sin arte nueva más allá de íconos pequeños (30dp) para la UI de fichas — generados con Pillow, mismo estilo/paleta que los sprites ya existentes. Los objetos que bloquean la vista reusan decoraciones de mundo que YA EXISTEN (`tree`, `decor_bikerack_campus`, `decor_busstop_city`) — cero arte nueva ahí.
- Toda la suite `SimulationTest`/`ParsingTest` debe quedar en verde después de cada tarea.
- Nunca commitear sin que el usuario lo pida explícitamente.

---

## Task 1: Generar los íconos de ficha que faltan

**Files:**
- Create: `assets/game/res/drawable-nodpi/icon_reflector.webp`
- Create: `assets/game/res/drawable-nodpi/icon_streetlamp.webp`
- Create: `assets/game/res/drawable-nodpi/icon_headlights.webp`
- Create: `assets/game/res/drawable-nodpi/icon_island.webp`
- Create: `assets/game/res/drawable-nodpi/icon_escort.webp`
- Create (temp, not committed): `tools/generate_tool_icons.py` — script that produces the five above

**Por qué estos 5 y no otros:** `visible` y `lighting` hoy comparten literalmente el mismo ícono (`star`, que ADEMÁS es el ícono de calificación de estrellas) — un niño no puede distinguirlos. `refuge` usa un ícono de "esperar" sin relación visual con una isla. `escort` (ficha nueva de la ronda 1) nunca tuvo ícono asignado y cae al ícono de rampa por accidente. `car_lights` es la ficha nueva de esta ronda.

- [ ] **Step 1: Escribir el script generador**

Seguir el mismo patrón que ya se usó para las 8 decoraciones pixel-art de la ronda anterior (grilla de 8px, paleta extraída de los sprites existentes — leer `assets/game/res/drawable-nodpi/star.webp` con Pillow para tomar su paleta exacta de contorno/relleno antes de dibujar los nuevos, así los 5 íconos nuevos se ven como parte de la misma familia visual, no insertados).

```python
from PIL import Image
import struct

SIZE = 32  # 30dp target, exportado a 32x32 para alinearse a la grilla de 8px
INK = (43, 37, 64, 255)      # mismo contorno que el resto del juego (ver INK en Board.kt)

def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

def save(img, name):
    img.save(f"assets/game/res/drawable-nodpi/{name}.webp", "WEBP", lossless=True)

# icon_reflector: una banda/chaleco diagonal con una franja reflectiva brillante,
# distinto de la estrella de puntaje en forma Y en color (naranja/plata, no amarillo).
def reflector():
    img = canvas(); px = img.load()
    # franja diagonal gris oscuro (chaleco) con una línea plateada brillante encima
    for y in range(SIZE):
        for x in range(SIZE):
            d = x - y
            if -3 <= d <= 9: px[x, y] = (90, 95, 110, 255)
            if -1 <= d <= 3: px[x, y] = (224, 228, 235, 255)
    return img

# icon_streetlamp: un poste con la cabeza del farol encendida (círculo amarillo cálido),
# silueta vertical reconocible — nada que ver con una estrella.
def streetlamp():
    img = canvas(); px = img.load()
    for y in range(10, SIZE - 2):
        for x in range(14, 18): px[x, y] = INK
    for y in range(2, 11):
        for x in range(9, 23):
            cx, cy = 16, 6
            if (x - cx) ** 2 + (y - cy) ** 2 <= 36: px[x, y] = (255, 224, 122, 255)
    return img

# icon_headlights: silueta simple de un auto de frente con dos faros encendidos
# (dos círculos amarillos), distinto del farol (horizontal, no vertical).
def headlights():
    img = canvas(); px = img.load()
    for y in range(14, 26):
        for x in range(4, 28): px[x, y] = (138, 143, 158, 255)
    for cx in (9, 23):
        for y in range(16, 24):
            for x in range(cx - 4, cx + 4):
                if (x - cx) ** 2 + (y - 20) ** 2 <= 16: px[x, y] = (255, 224, 122, 255)
    return img

# icon_island: una isla con pasto arriba de una franja de agua/asfalto, forma
# de "montículo", nada que ver con "esperar".
def island():
    img = canvas(); px = img.load()
    for y in range(20, 24):
        for x in range(2, 30): px[x, y] = (167, 207, 221, 255)
    for y in range(8, 20):
        for x in range(SIZE):
            if (x - 16) ** 2 / 180 + (y - 18) ** 2 / 60 <= 1: px[x, y] = (136, 201, 152, 255)
    return img

# icon_escort: dos figuras simples (grande + chica) tomadas de la mano.
def escort():
    img = canvas(); px = img.load()
    def person(cx, top, w, h, color):
        for y in range(top, top + h):
            for x in range(cx - w // 2, cx + w // 2): px[x, y] = color
    person(10, 6, 6, 20, (224, 122, 95, 255))
    person(22, 12, 5, 14, (122, 161, 224, 255))
    for x in range(13, 19): px[x, 20] = INK
    return img

for name, fn in [("icon_reflector", reflector), ("icon_streetlamp", streetlamp),
                  ("icon_headlights", headlights), ("icon_island", island),
                  ("icon_escort", escort)]:
    save(fn(), name)
```

- [ ] **Step 2: Ejecutarlo**

Run: `python3 tools/generate_tool_icons.py` desde la raíz del repo.
Expected: los 5 archivos `.webp` aparecen en `assets/game/res/drawable-nodpi/`.

- [ ] **Step 3: Confirmar que Android los reconoce como recursos válidos**

Run: `./gradlew :app:compileDebugKotlin` (fuerza `generateDebugRFile`, que falla si un `.webp` está corrupto o el nombre no es válido como identificador de recurso).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Borrar el script temporal**

```bash
rm tools/generate_tool_icons.py
```

(No es parte del código del juego — es una herramienta de un solo uso, igual que el script de búsqueda de `oncomingOffset` de la sesión anterior.)

- [ ] **Step 5: Commit**

```bash
git add assets/game/res/drawable-nodpi/icon_reflector.webp assets/game/res/drawable-nodpi/icon_streetlamp.webp assets/game/res/drawable-nodpi/icon_headlights.webp assets/game/res/drawable-nodpi/icon_island.webp assets/game/res/drawable-nodpi/icon_escort.webp
git commit -m "feat(art): nuevos iconos para reflector, farol, luces de auto, isla y acompañar"
```

---

## Task 2: Corregir el mapeo real de íconos y jubilar el ícono de `eye`

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/CraftArt.kt:74-87` (`ToolArtwork`)

**Interfaces:**
- Consumes: los 5 drawables de la Tarea 1.

- [ ] **Step 1: Reescribir el `when`**

```kotlin
@Composable fun ToolArtwork(id: String) {
    val resource = when (id) {
        "look" -> R.drawable.icon_look
        "visible" -> R.drawable.icon_reflector
        "lighting" -> R.drawable.icon_streetlamp
        "car_lights" -> R.drawable.icon_headlights
        "refuge" -> R.drawable.icon_island
        "escort" -> R.drawable.icon_escort
        "signal" -> R.drawable.icon_signal
        "speed_zone" -> R.drawable.icon_bump
        "phone_away" -> R.drawable.icon_phone_away
        "crosswalk" -> R.drawable.icon_crosswalk
        else -> R.drawable.icon_crosswalk
    }
    PixelImage(resource, null, Modifier.size(30.dp))
}
```

Nota: ya no hay caso para `"eye"` — se quita en la Tarea 3. El `else` ya no debería alcanzarse nunca (todo id real está listado explícitamente); se deja como red de seguridad, no como mapeo intencional.

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/CraftArt.kt
git commit -m "fix(ui): cada ficha tiene su propio icono; visible y lighting ya no comparten la estrella de puntaje"
```

---

## Task 3: Jubilar "Confirmar alto" (`eye`)

**Files:**
- Modify: `assets/game/files/campaign.json` (quitar la ficha del catálogo y de cada nivel que la ofrece)
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt` (quitar el bloque `if("eye" in actions)` muerto)
- Modify: `app/src/test/java/com/example/crosswalker/SimulationTest.kt` (los tests que usan `eye` como alternativa a `look`)

**Por qué se puede quitar sin riesgo:** el motor nunca condiciona el frenado de un auto a qué ficha eligió el peatón — los autos frenan por reglas externas (cruce marcado, semáforo). `Mirar` ya exige que el hueco sea seguro para TODO el cruce, lo que incluye el caso "el auto ya se detuvo" (hueco infinito). Por construcción, `Confirmar alto` nunca puede tener éxito donde `Mirar` falle. Quitarlo no cambia qué gana ningún nivel — el reporte de balance ya lo demostró en la ronda 2 de este plan (ver el razonamiento previo a este documento).

- [ ] **Step 1: Quitar la ficha del catálogo**

En `campaign.json`, eliminar el objeto `{"id": "eye", ...}` del array `tools`.

- [ ] **Step 2: Quitar `"eye"` de `tools`/`newTools` en cada nivel**

Para cada nivel 2-12 que la tenga en `tools`, quitarla (no cambia ninguna otra cosa de ese nivel en este paso — eso es la Tarea 8).

- [ ] **Step 3: Quitar el código muerto en el motor**

En `Simulation.kt`, dentro del bloque `if(waiting && focused(p)) { ... }`, eliminar:

```kotlin
if("eye" in actions) {
    val allStopped = lanesAhead.all { lane -> carsFor(lane).all { (car,dir,isOncoming) ->
        !isOncoming && car.speed < .002f && (p.x-car.x)*dir > .115f } }
    move = move && allStopped
    if(!allStopped) feedback = "Confirmar alto: espera a que TODOS los carriles se detengan; una mirada no basta."
}
```

- [ ] **Step 4: Actualizar los tests que usaban `eye` como alternativa a `look`**

En `SimulationTest.kt`: varios tests comparan explícitamente `setOf("look")` vs `setOf("eye")` esperando el mismo resultado (`reflectorAloneNeverWins`, `wetRoadNeedsLongerToBrakeAndReflectorImprovesDetection`, el bucle `for(observation in listOf("look","eye"))` de `campaignHasMultipleAffordableSolutionsAndRealFailure`, y las ramas `eye=` de `budgetCountsPersonalCardsPerPersonAndStreetOnce`/`noLevelOffersAToolThatCannotPossiblyMatterThere` si aplican). Quitar la rama `eye` de cada uno — queda solo `look`. Si un test literalmente solo comparaba look-vs-eye (y no prueba nada más), eliminarlo entero.

- [ ] **Step 5: Correr toda la suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: pueden aparecer fallas de balance en niveles que dependían de la rama `eye` para encontrar una solución — anotar cuáles (se resuelven en la Tarea 8, no aquí).

- [ ] **Step 6: Commit**

```bash
git add assets/game/files/campaign.json app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "refactor(game): jubila Confirmar alto (redundante por diseño con Mirar)"
```

---

## Task 4: Mecánica nueva — "Encender luces" (`car_lights`)

**Files:**
- Modify: `assets/game/files/campaign.json` (nueva ficha de categoría `street`)
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt` (`sight()`)
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt` (brillo de faros en el sprite del auto)
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**La lección real:** de noche, un conductor debe encender las luces para que los peatones lo vean venir — no es lo mismo que el peatón lleve reflector (`visible`, para que el conductor LO vea a él) ni que el cruce esté iluminado (`lighting`, para ver el área en general). Es la tercera pieza del rompecabezas "de noche todos se ayudan a ver", y la pieza que le falta al conductor.

- [ ] **Step 1: Escribir el test que falla**

```kotlin
@Test fun carsWithoutHeadlightsAreHarderToSeeAtNight() {
    val base = levels[2].copy(night = true,
        lanes = listOf(Lane(.7f, .15f, 0f, "car_blue")),
        walkers = listOf(Walker("ana", "Ana", "impulsive", .12f, "walker_coral")),
        tools = listOf("look", "car_lights"))
    val dark = Simulation(base, plan(base, 1, setOf("look")))
    val lit = Simulation(base, plan(base, 1, setOf("look"), setOf("car_lights")))
    assertTrue("Sin luces, de noche, se ve menos lejos",
        dark.sightRange("ana") < lit.sightRange("ana"))
}
```

(`plan(l, crossing, skill, street)` ya acepta `street` como cuarto argumento con default `emptySet()` — se usa exactamente igual que en `wetRoadNeedsLongerToBrakeAndReflectorImprovesDetection`.)

- [ ] **Step 2: Correrlo y confirmar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest.carsWithoutHeadlightsAreHarderToSeeAtNight"`
Expected: FAIL — `sight()` no conoce `car_lights` todavía.

- [ ] **Step 3: Implementar**

En `Simulation.kt`, cambiar `sight()`:

```kotlin
private fun sight(p: PersonState): Float {
    val base = level.sight[crossing(p)]
    // Lighting improves darkness, not a view blocked by a parked vehicle.
    val light = if(level.night && !("lighting" in plan.street && crossing(p) == 1)) .55f else 1f
    // An impulsive walker alone judges gaps with less margin; an escort compensates.
    val impulsive = if(p.walker.kind == "impulsive" && "escort" !in skills(p)) .8f else 1f
    // At night, a car's own headlights are what let you see IT coming from far away —
    // separate from the street being lit, and separate from you wearing a reflector.
    val headlights = if(level.night && "car_lights" !in plan.street) .7f else 1f
    return base * light * impulsive * headlights * if(level.weather == "fog") .7f else 1f
}
```

- [ ] **Step 4: Agregar la ficha al catálogo**

En `campaign.json`, agregar a `tools`:

```json
{
  "id": "car_lights",
  "label": "Encender luces",
  "icon": "●",
  "cost": 2,
  "description": "De noche, los autos encienden las luces para que los veas venir desde más lejos. No reemplaza al reflector ni a la iluminación del cruce — cada uno ayuda a ver algo distinto.",
  "category": "street"
}
```

- [ ] **Step 5: Agregar el brillo visual en `Board.kt`**

Dentro de `level.lanes.forEach { lane -> ... }`, justo antes de dibujar el auto principal, agregar (solo si `"car_lights" in plan.street && level.night`):

```kotlin
if("car_lights" in plan.street && level.night) {
    val beamX = x + (if(lane.speed < 0) -34f else 34f)
    color("#FFE07A"); p.alpha = 70
    c.drawCircle(beamX, carY, 14f, p)
    p.alpha = 255
}
```

(Dibujar esto ANTES del `sprite(lane.car,...)` de esa línea para que el brillo quede detrás del auto, no encima.)

- [ ] **Step 6: Correr el test y la suite completa**

Run: `./gradlew :app:testDebugUnitTest`
Expected: el nuevo test pasa; compila el cambio de `Board.kt`.

- [ ] **Step 7: Commit**

```bash
git add assets/game/files/campaign.json app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/main/java/com/example/crosswalker/Board.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat(game): nueva ficha Encender luces — los autos tambien deben verse de noche"
```

---

## Task 5: Frenado más visible y más natural

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt` (`stepVehicle`)
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt` (luz de freno)
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**El problema real:** el auto espera 0.65s a velocidad completa después de detectar el riesgo antes de empezar a frenar — eso produce un frenazo tardío y abrupto en vez de una desaceleración visible. Reducir la espera y agregar una señal visual (luz de freno) hace que el frenado se LEA como una decisión del auto, no como un bug.

- [ ] **Step 1: Escribir el test de la nueva ventana de reacción**

```kotlin
@Test fun carsBrakeSoonerAndMoreVisibly() {
    val base = levels[2].copy(lanes = listOf(Lane(.7f, .15f, 0f, "car_blue")),
        walkers = listOf(Walker("ana", "Ana", "impulsive", .12f, "walker_coral")),
        tools = listOf("look", "crosswalk"))
    val sim = Simulation(base, plan(base, 1, setOf("look"), setOf("crosswalk")))
    repeat(60) { sim.tick(.02f) } // 1.2s
    assertTrue("A 1.2s ya deberia estar frenando, no esperando",
        sim.isBraking(base.lanes.first()) || sim.carSpeed(base.lanes.first()) < .15f)
}
```

(El valor exacto de `repeat(60)` y el umbral se ajustan empíricamente corriendo el test — el punto es que con la espera actual de 0.65s el auto probablemente SIGUE a velocidad nominal a los 1.2s; con la espera reducida, ya debería estar reaccionando.)

- [ ] **Step 2: Correrlo, confirmar que falla con los valores actuales**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest.carsBrakeSoonerAndMoreVisibly"`

- [ ] **Step 3: Reducir la espera de reacción**

En `stepVehicle`, cambiar:

```kotlin
car.braking = request && car.reaction >= .65f
```

a:

```kotlin
car.braking = request && car.reaction >= .35f
```

- [ ] **Step 4: Correr el test; si sigue fallando, ampliar también el margen de detección**

Si reducir solo la reacción no alcanza, cambiar también:

```kotlin
val request = distance != null && distance <= stopDistance(car.speed) + .18f
```

a un margen mayor, por ejemplo `+ .30f`, y volver a correr. Repetir hasta que el test pase con un valor concreto (anotar cuál quedó).

- [ ] **Step 5: Confirmar que el resto de la suite sigue en verde**

Run: `./gradlew :app:testDebugUnitTest`
Expected: puede requerir retocar presupuestos en niveles donde el frenado más temprano cambia el balance (mismo procedimiento de la Tarea 8).

- [ ] **Step 6: Agregar la luz de freno en `Board.kt`**

Dentro de `level.lanes.forEach`, donde ya existe:

```kotlin
if(!level.tutorial && sim != null) {
    if(sim.carSpeed(lane)<.002f) label("ALTO",x,carY-24f,8f,"#FFE07A")
    else if(sim.isBraking(lane)) label("FRENA",x,carY-24f,8f,"#F27A6B")
}
```

agregar, justo antes del `sprite(lane.car,...)` de esa línea, una señal visual (no solo texto) cuando frena:

```kotlin
if(!level.tutorial && sim?.isBraking(lane) == true) {
    val tailX = x + (if(lane.speed < 0) 20f else -20f)
    color("#F27A6B"); c.drawCircle(tailX, carY, 4f, p)
}
```

- [ ] **Step 7: Compilar y correr toda la suite**

Run: `./gradlew :app:testDebugUnitTest`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/main/java/com/example/crosswalker/Board.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "fix(game): frenado mas temprano y visible, con luz de freno"
```

---

## Task 6: Clima que de verdad se vea

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**No hay test JVM** (render). Verificación: compila; el usuario confirma visualmente.

- [ ] **Step 1: Lluvia más densa y más opaca**

Cambiar:

```kotlin
if(level.weather=="rain") {
    color("#B7CFDD");p.alpha=95
    for(i in 0..35) { val x=(i*47%360).toFloat();val y=(i*71%330).toFloat();c.drawLine(x,y,x-3,y+9,p) };p.alpha=255
}
```

a:

```kotlin
if(level.weather=="rain") {
    color("#1B2440");p.alpha=35;c.drawRect(0f,0f,360f,330f,p);p.alpha=255
    color("#CFE0ED");p.alpha=160
    for(i in 0..70) { val x=(i*37%370-5).toFloat();val y=(i*53%340).toFloat();c.drawLine(x,y,x-5,y+16,p) };p.alpha=255
}
```

(Más líneas, más largas, más opacas, y un cielo gris-azul de fondo — llueve en TODA la pantalla, no solo trazos sueltos.)

- [ ] **Step 2: Niebla realmente densa**

Cambiar:

```kotlin
if(level.weather=="fog") {color("#DDE2E9");p.alpha=40;c.drawRect(0f,56f,360f,330f,p);p.alpha=255}
```

a:

```kotlin
if(level.weather=="fog") {color("#DDE2E9");p.alpha=110;c.drawRect(0f,56f,360f,330f,p);p.alpha=255}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): lluvia y niebla mucho mas visibles"
```

**Nota para el usuario:** playtest los niveles 7 (lluvia), 11 (niebla) y 12 (lluvia+noche) — estos valores de opacidad son mi mejor estimación sin poder verlos renderizados; probablemente necesiten un ajuste fino en el dispositivo.

---

## Task 7: El objeto que bloquea la vista cambia por mundo, y el cono de visión se ve de verdad

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**Por qué:** hoy siempre es el mismo auto azul rotado, sin importar el mundo — reduce variedad y sigue sin ser obvio para un niño pequeño. Las decoraciones por mundo ya existen (`tree`, `decor_bikerack_campus`, `decor_busstop_city`) y cada una tiene una identidad reconocible.

- [ ] **Step 1: Elegir el objeto por mundo**

En el bloque de `Simulation.crossings.forEachIndexed` donde se dibuja el peligro de vista bloqueada (de la ronda 1), cambiar el sprite fijo `"car_blue"` por uno que dependa de `level.world`:

```kotlin
if(level.sight[i]<.5f) {
    val cx=x*360f
    val obstruction=when(level.world) { 1->"decor_bikerack_campus"; 2->"decor_busstop_city"; else->"tree" }
    sprite(obstruction,cx-24,290f,48f,42f)
    rect(cx-36f,286f,72f,11f,"#D9483F")
    color("#FFF8E6");p.style=Paint.Style.STROKE;p.strokeWidth=1.2f
    c.drawCircle(cx-28f,291.5f,3f,p);c.drawLine(cx-31f,294.5f,cx-25f,288.5f,p)
    val caption=when(level.world) { 1->"BICICLETAS"; 2->"PARADA DE BUS"; else->"ÁRBOL" }
    label(caption,cx+6,294f,6f,"#FFF8E6")
}
```

(Se quita la rotación 90° del auto de la ronda 1 — ya no aplica, el objeto nuevo es naturalmente estático y reconocible por sí mismo.)

- [ ] **Step 2: Cono de visión como abanico relleno, no línea punteada**

En el bloque de `level.walkers.forEachIndexed` donde se dibuja la línea punteada de alcance de vista (de la ronda 1), cambiar:

```kotlin
if(planning) {
    val range=preview.sightRange(w.id)*360f
    val crossX=Simulation.crossings[plan.people[w.id]?.crossing ?: 1]*360f
    val laneY=(level.lanes.maxByOrNull { it.y }?.y ?: .5f)*410f
    color(if(preview.sightRange(w.id)<.5f) "#F27A6B" else "#FFE07A")
    p.style=Paint.Style.STROKE;p.strokeWidth=2f
    p.pathEffect=DashPathEffect(floatArrayOf(6f,6f),0f)
    c.drawLine(crossX-range,laneY,crossX+range,laneY,p)
    p.pathEffect=null
}
```

a un abanico relleno semitransparente, como un cono de linterna, en vez de una línea:

```kotlin
if(planning) {
    val range=preview.sightRange(w.id)*360f
    val crossX=Simulation.crossings[plan.people[w.id]?.crossing ?: 1]*360f
    val laneY=(level.lanes.maxByOrNull { it.y }?.y ?: .5f)*410f
    val short=preview.sightRange(w.id)<.5f
    color(if(short) "#F27A6B" else "#FFE07A");p.style=Paint.Style.FILL;p.alpha=55
    c.drawRect(crossX-range,laneY-10f,crossX+range,laneY+10f,p)
    p.alpha=255
    if(short) label("¡OJO!",crossX,laneY-16f,8f,"#F27A6B")
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): el objeto que tapa la vista varia por mundo; el alcance de vista se ve como un cono relleno"
```

**Nota para el usuario:** playtest niveles 4, 7, 9 (si aplica), 10, 11, 12.

---

## Task 8: Re-encuadre narrativo + re-balanceo de necesidad real, nivel por nivel

**Files:**
- Modify: `assets/game/files/campaign.json`
- Modify/Create: tests dedicados en `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

Este es el trabajo más largo del plan: no es solo texto, es verificar con pruebas que cada ficha que un nivel ofrece de verdad hace falta en algún punto — no solo que esté disponible.

- [ ] **Step 1: Nivel 5 — Ana corrió tras su pelota**

Cambiar la lección a algo como: *"Ana corrió tras su pelota sin mirar. Tomás necesita guardar el celular. Un adulto que acompaña a Ana ayuda a esperar el momento seguro — o dale más margen con una zona lenta."* (ajustar tono exacto, mantener corto). No cambiar fichas/carriles aquí — ya se retocan en los pasos siguientes si la quita de `eye` rompió el balance.

- [ ] **Step 2: Nivel 9 — solo introduce `visible`**

Confirmar que `newTools` tenga únicamente `["visible"]` (quitar `"lighting"` de ahí si quedó de la ronda 1 — se mueve al Paso 3).

- [ ] **Step 3: Nivel 10 — la regla del camión detenido + introduce `lighting`**

Cambiar la lección a la regla real y concreta: *"Nunca cruces justo antes o después de un camión o autobús detenido — espera a que se aleje, así el conductor te ve. El Centro además está tapado por la parada."* Mover `"lighting"` a `newTools` de este nivel (ya no se introduce en el 9).

- [ ] **Step 4: Nivel 11 — introduce `car_lights`**

Cambiar `newTools` a `["car_lights"]`. Actualizar la lección para mencionarlo: *"La niebla no desaparece con una lámpara, pero los autos SÍ pueden prender luces para que los veas venir. La isla permite esperar a salvo; reduce la velocidad."*

- [ ] **Step 5: Para cada nivel que introduce algo (3, 4, 5, 6, 8, 9, 10, 11): escribir el par de tests "sin, falla / con, gana"**

Patrón (ejemplo para el nivel 9 con `visible`):

```kotlin
@Test fun level9NeedsVisibleNotJustLook() {
    val l = levels.first { it.id == 9 }
    val without = finish(Simulation(l, plan(l, 1, setOf("look"), setOf("crosswalk"))))
    val with = finish(Simulation(l, plan(l, 1, setOf("look", "visible"), setOf("crosswalk"))))
    assertEquals(Phase.RETRY, without.phase)
    assertEquals(Phase.WON, with.phase)
}
```

Escribir el equivalente para 3→`crosswalk`, 4→`speed_zone`, 5→`escort`, 6→`signal`, 8→`refuge`, 10→`lighting`, 11→`car_lights`. (El de 5 reemplaza/confirma lo que ya existía de la ronda 1; revisar si ya hay un test equivalente antes de duplicar.)

- [ ] **Step 6: Correr cada test, ajustar el nivel que falle**

Para cada uno que falle (lo más probable: el lado "sin, falla" en realidad gana porque la geometría tiene demasiado margen, o el lado "con, gana" falla porque el presupuesto no alcanza):
- Si "sin, falla" en realidad gana: la pista es demasiado fácil — subir la velocidad del carril relevante un 10-15% y volver a correr.
- Si "con, gana" falla: subir `"budget"` del nivel en 1-2 y volver a correr.
Repetir por nivel hasta que ambos lados del par pasen. Anotar qué número cambió en cada nivel.

- [ ] **Step 7: Correr la suite completa**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — todos los pares de necesidad, el balance general, y el guard test de la ronda 1 (`noLevelOffersAToolThatCannotPossiblyMatterThere`) en verde.

- [ ] **Step 8: Barrido final de "nadie gana sin preparar" (igual que las rondas anteriores)**

Crear temporalmente `TempRound2SweepTest.kt` con el mismo patrón de barrido (los 12 niveles × 3 cruces × plan vacío → todos deben RETRY), correrlo, confirmar, borrarlo.

- [ ] **Step 9: Commit**

```bash
git add assets/game/files/campaign.json app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat(campaign): cada ficha introducida es necesaria de verdad; nivel 5 y 10 con reglas reales"
```

---

## Task 9: Regresión final y resumen para el usuario

- [ ] **Step 1: Suite completa**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos los tests.

- [ ] **Step 2: `git status` — confirmar que no quedó ningún archivo temporal**

Run: `git status --short`
Expected: solo los archivos listados en el Global Constraints; ningún `Temp*Test.kt` ni script de Python suelto.

- [ ] **Step 3: Resumen para el usuario**

Reportar: qué niveles cambiaron de presupuesto/velocidad y por qué (Tarea 8, Paso 6); recordar que todos los cambios visuales (Tareas 1, 2, 6, 7, y el brillo de faros/luz de freno de las Tareas 4-5) no se pudieron ver renderizados — se necesita playtest real antes de mergear.
