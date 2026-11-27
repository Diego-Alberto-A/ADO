# Rediseño de fichas, mecánicas e intuitividad (niveles 4-12) Implementation Plan

**Goal:** Fix the measured stagnation (World 2 introduces zero new tools across 4 levels) and the "ficha sin sentido" problem (two walker kinds and two tools that are provably inert in the levels that offer them), and make the "blocked sight" hazard legible instead of mysterious.

**Architecture:** Two small, deterministic engine additions in `Simulation.kt` (a real `impulsive` penalty countered by a new `escort` skill tool; a `Lane.blindSpot` flag that only affects driver detection distance), wired into ~6 level JSON entries in `campaign.json`, plus three presentation-only changes in `Board.kt` that make the existing "blocked sight" mechanic read as a real hazard instead of looking like a chosen ficha's feedback label.

**Tech Stack:** Kotlin, JVM unit tests (JUnit, `org.json`), Jetpack Compose Canvas (`Board.kt`, not unit-testable — no emulator in this environment, flagged per task).

**Spec:** `docs/design/specs/2026-11-12-level-and-card-redesign-design.md`

## Global Constraints

- Niveles 1-3: concepto intacto; se permite tocar solo detalles provablemente rotos (ver Tarea 5).
- Nivel 4 en adelante: terreno libre, pero no se cambian IDs, orden, ni cantidad de niveles (3 mundos × 4 niveles se queda igual).
- Sin arte nueva más allá de reusar sprites ya existentes y sin usar (`car_green`).
- Todo cambio de dato (JSON) debe dejar la suite `SimulationTest` en verde: 2+ soluciones por nivel, 3 estrellas alcanzable, ninguna ficha sola gana un nivel, un plan vacío siempre falla (`campaignHasMultipleAffordableSolutionsAndRealFailure`, `noSingleCardTypeSolvesAnyNonTutorialLevel`).
- Valores numéricos iniciales propuestos (a recalibrar en la Tarea 9 si algún nivel falla): multiplicador de `sight` para `impulsive` = 0.8; costo de `escort` = 2; factor de detección reducida para `blindSpot` = 0.5.
- Nunca commitear sin que el usuario lo pida explícitamente — cada "Commit" de este plan es una sugerencia de mensaje, ejecútalo solo si el flujo de trabajo del usuario lo permite.

---

## Task 1: `Lane.blindSpot` field + parsing

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt:6-7` (Lane data class)
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt:406-410` (`parseLevel` lane parsing)
- Test: `app/src/test/java/com/example/crosswalker/game/ParsingTest.kt`

**Interfaces:**
- Produces: `Lane.blindSpot: Boolean` (default `false`), read by Task 2.

- [x] **Step 1: Write the failing test**

Add to `ParsingTest.kt` (new test, after `parseLevelReadsTutorialFieldsWhenPresent`):

```kotlin
@Test fun parseLevelReadsBlindSpotLaneFlag() {
    val json = JSONObject(
        """{"id":1,"world":0,"title":"t","lesson":"l","budget":3,"night":false,
            "lanes":[{"y":0.5,"speed":0.11,"offset":0.6,"car":"car_coral","blindSpot":true}],
            "walkers":[{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
            "tools":["look"]}"""
    )
    val level = parseLevel(json)
    assertTrue(level.lanes.single().blindSpot)
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.ParsingTest"`
Expected: compile error — `Lane` has no member `blindSpot`.

- [x] **Step 3: Add the field and parse it**

In `Simulation.kt`, change the `Lane` data class:

```kotlin
data class Lane(val y: Float, val speed: Float, val offset: Float, val car: String,
    val twoWay: Boolean = false, val oncomingOffset: Float = .7f, val blindSpot: Boolean = false)
```

In `parseLevel`'s lane-parsing lambda:

```kotlin
val lanes = json.getJSONArray("lanes").let { a -> List(a.length()) { i -> a.getJSONObject(i).let {
    Lane(it.getDouble("y").toFloat(), it.getDouble("speed").toFloat(), it.getDouble("offset").toFloat(), it.getString("car"),
        it.optBoolean("twoWay", false), it.optDouble("oncomingOffset", .7).toFloat(), it.optBoolean("blindSpot", false))
} } }
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.ParsingTest"`
Expected: PASS, all `ParsingTest` cases green.

- [x] **Step 5: Run full suite to confirm nothing else broke**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (the new field defaults to `false`, so no existing level/test is affected).

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/game/ParsingTest.kt
git commit -m "feat(game): add blindSpot flag to Lane"
```

---

## Task 2: Blind-spot lanes reduce driver detection distance

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt` (`stepVehicle`'s `targets` filter)
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Consumes: `Lane.blindSpot` (Task 1).
- Produces: a car on a `blindSpot` lane detects pedestrians at half the normal distance, day or night.

- [x] **Step 1: Write the failing test**

Add to `SimulationTest.kt`:

```kotlin
@Test fun blindSpotLaneDetectsPedestriansLaterEvenByDay() {
    val base = levels[2].copy(lanes = listOf(Lane(.7f, .15f, 0f, "car_blue")),
        walkers = listOf(Walker("ana", "Ana", "impulsive", .12f, "walker_coral")))
    val blind = base.copy(lanes = listOf(Lane(.7f, .15f, 0f, "car_green", blindSpot = true)))
    val baseSim = Simulation(base, plan(base, 1, setOf("look"), setOf("crosswalk")))
    val blindSim = Simulation(blind, plan(blind, 1, setOf("look"), setOf("crosswalk")))
    repeat(120) { baseSim.tick(.02f); blindSim.tick(.02f) }
    assertTrue("Blind-spot car should still be moving while the normal one already reacted",
        blindSim.carSpeed(blind.lanes.first()) > baseSim.carSpeed(base.lanes.first()))
}
```

(Use `levels[2]` — a day level that already has `"crosswalk"` in its tools — not `levels[8]`: that one is `night:true`, and the detection formula's night penalty stacks with `blindSpot`'s, swamping the signal you're trying to isolate. `walkers` is overridden to avoid inheriting `levels[2]`'s wheelchair walker and its accessibility gate. Skills must include `"look"`, not `"eye"` — with no safe gap ever appearing and no signal/marked-stop condition the car actually reaches, `"eye"`'s "wait until every lane has fully stopped" gate can deadlock the walker forever; `"look"` doesn't have that failure mode. Skills must NOT be empty — an empty skill set makes this walker "unprepared," which triggers the ambush-car-reuse mechanic from earlier this session and overrides the car's speed entirely, defeating the point of this test. The offset (`0f`, not `.4f`) and tick count (`120`, not `150`) matter too: they were found by tracing `carSpeed` at 20-tick intervals until a tick count where the blind-spot car is clearly still faster — see the "Note on empirical tuning" at the end of this task if the numbers ever need re-deriving.)

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: FAIL — both cars currently brake identically regardless of `blindSpot`.

- [x] **Step 3: Implement the detection reduction**

In `stepVehicle`, change:

```kotlin
val detection = sight(p) * if(level.night && "visible" !in skills(p)) .45f else 1f
```

to:

```kotlin
val detection = sight(p) * (if(level.night && "visible" !in skills(p)) .45f else 1f) * (if(lane.blindSpot) .5f else 1f)
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: PASS.

- [x] **Step 5: Run full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no existing lane sets `blindSpot`, so every current level is unaffected).

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat(game): blind-spot lanes detect pedestrians later"
```

---

## Task 3: Real `impulsive` penalty, cancelled by new `escort` skill

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt` (`sight()`, plus a new public wrapper)
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Consumes: `Walker.kind`, `skills(p)` (existing).
- Produces: `sight(p)` now returns 20% less for `impulsive` walkers unless `"escort"` is in their skills; a new public `fun sightRange(walkerId: String): Float` wrapper for Task 13 (Board.kt) to call without exposing the private `sight(PersonState)` signature.

- [x] **Step 1: Write the failing test**

Add to `SimulationTest.kt`:

```kotlin
@Test fun impulsiveWalkerSeesLessUnlessEscorted() {
    // `tools` is overridden explicitly (not inherited from levels[8]) because at this
    // point in the plan campaign.json doesn't list "escort" anywhere yet — Task 4/7
    // add that later. The test must be self-contained regardless of task order.
    val base = levels[8].copy(
        lanes = listOf(Lane(.7f, .4f, .4f, "car_blue")),
        walkers = listOf(Walker("ana", "Ana", "impulsive", .12f, "walker_coral")),
        tools = listOf("look", "escort")
    )
    val alone = finish(Simulation(base, plan(base, 1, setOf("look"))))
    val escorted = finish(Simulation(base, plan(base, 1, setOf("look", "escort"))))
    assertEquals("An impulsive walker alone should misjudge this fast lane and get caught",
        Phase.RETRY, alone.phase)
    assertEquals("Escort compensates the penalty — same lane is safely crossable",
        Phase.WON, escorted.phase)
}
```

(Pick the lane speed `.4f` deliberately high — adjust it during Step 2 if it doesn't actually separate the two outcomes; the point of the test is to find a speed where the 20% sight penalty is the deciding factor, not to hit an exact number.)

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: FAIL — `escort` doesn't exist yet as a concept, both runs currently behave identically.

- [x] **Step 3: Implement the penalty and the public wrapper**

In `Simulation.kt`, change `sight()`:

```kotlin
private fun sight(p: PersonState): Float {
    val base = level.sight[crossing(p)]
    // Lighting improves darkness, not a view blocked by a parked vehicle.
    val light = if(level.night && !("lighting" in plan.street && crossing(p) == 1)) .55f else 1f
    // An impulsive walker alone judges gaps with less margin; an escort compensates.
    val impulsive = if(p.walker.kind == "impulsive" && "escort" !in skills(p)) .8f else 1f
    return base * light * impulsive * if(level.weather == "fog") .7f else 1f
}
fun sightRange(walkerId: String): Float = people.find { it.walker.id == walkerId }?.let { sight(it) } ?: 1f
```

Place `sightRange` right after `sight()`'s closing brace (both are small, keep them adjacent).

- [x] **Step 4: Run test to verify it passes, adjusting the lane speed if needed**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
If it still fails, try a different `speed` value in the test's lane (e.g. `.3f` or `.5f`) until `alone` is RETRY and `escorted` is WON — this is tuning the *test fixture*, not the production constant.
Expected: PASS.

- [x] **Step 5: Run full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: likely some `campaignHasMultipleAffordableSolutionsAndRealFailure` failures for levels where `ana`/`impulsive` appears (5, 6, 7, 9, 10, 11) — this is expected and is what Task 9 (rebalance) resolves. Confirm the *only* new failures are for those level IDs; anything else is a real regression to investigate before continuing.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat(game): impulsive walkers need an escort or misjudge gaps"
```

---

## Task 4: Add the `escort` tool definition

**Files:**
- Modify: `assets/game/files/campaign.json` (`tools` array)

**Interfaces:**
- Consumes: nothing new — `parseTool` already reads arbitrary `id`/`label`/`icon`/`cost`/`description`/`category`.
- Produces: a `"skill"`-category tool with `id: "escort"`, cost 2, available for levels to list.

- [x] **Step 1: Add the tool**

In `campaign.json`, after the `"phone_away"` tool object in the `tools` array, insert:

```json
{
  "id": "escort",
  "label": "Acompañar",
  "icon": "●",
  "cost": 2,
  "description": "Un adulto acompaña y ayuda a esperar el momento seguro. Compensa que un peatón impulsivo juzgue mal los huecos si va solo. No sustituye Mirar ni Confirmar alto.",
  "category": "skill"
}
```

- [x] **Step 2: Verify the JSON still parses**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.ParsingTest"`
Expected: PASS (this test doesn't enumerate the full tool list, it's just a parser smoke test — this step just confirms the JSON file itself is still valid and loadable).

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: same failures as the end of Task 3 (levels 5,6,7,9,10,11) — the tool exists now but isn't wired into any level's `tools` list yet, so nobody can use it to compensate.

- [x] **Step 3: Commit**

```bash
git add assets/game/files/campaign.json
git commit -m "feat(campaign): add Acompañar (escort) tool"
```

---

## Task 5: Fix the two already-inert tools in World 0 (`visible` in levels 2 and 3)

**Files:**
- Modify: `assets/game/files/campaign.json` (levels 2, 3, 9)

**Why this belongs here:** `visible`'s own description says "De día no aporta ventaja", and the engine only ever checks it when `level.night` (`Simulation.kt`'s `stepVehicle` detection formula). Levels 2 and 3 are both `night:false` but offer `visible` anyway — level 2 even introduces it there as a `newTool`, so the player's first exposure to the reflector card teaches them nothing, since it does nothing in that level. This is the same bug class as the `lighting`-in-level-7 bug already found, just undetected until now.

**Interfaces:**
- Produces: `visible` is only ever offered where `level.night == true`; its real introduction moves to level 9 (`"Ver y ser visto"` — the first night level, already thematically about this exact card).

- [x] **Step 1: Remove `visible` from level 2**

In `campaign.json`, level `id: 2`: remove `"visible"` from both `"tools"` and `"newTools"`.
`"tools"` becomes `["look", "phone_away", "eye"]`.
`"newTools"` becomes `["phone_away", "eye"]`.

- [x] **Step 2: Remove `visible` from level 3's tools**

Level `id: 3`: remove `"visible"` from `"tools"` (it's not in `newTools` there, nothing else to change).
`"tools"` becomes `["look", "eye", "crosswalk"]`.

- [x] **Step 3: Introduce `visible` properly at level 9**

Level `id: 9` (`"Ver y ser visto"`, already `night:true` and already has `"visible"` in `"tools"`): set `"newTools": ["visible"]`.

- [x] **Step 4: Run the balance suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: levels 2 and 3 still pass (removing an inert option can only shrink the search space of *equivalent* solutions, never remove the ones that didn't use it — confirm no NEW failures appear for levels 2 or 3 specifically; failures for 5,6,7,9,10,11 from Task 3 are still expected and unrelated).

- [x] **Step 5: Commit**

```bash
git add assets/game/files/campaign.json
git commit -m "fix(campaign): reflector only offered where it can matter (night)"
```

---

## Task 6: Wire `escort` into level 5 (its real introduction)

**Files:**
- Modify: `assets/game/files/campaign.json` (level 5)

- [x] **Step 1: Add the tool and update the lesson**

Level `id: 5`: change

```json
"lesson": "Un coche que cede en un carril no detiene al otro. Prepara a Tomás y a Ana por separado.",
...
"tools": ["look", "eye", "phone_away", "speed_zone", "crosswalk"],
"newTools": [],
```

to

```json
"lesson": "Un coche que cede en un carril no detiene al otro. Tomás necesita guardar el celular. Ana es impulsiva: juzga mal los huecos si va sola — acompáñala, o dale más margen con una zona lenta.",
...
"tools": ["look", "eye", "phone_away", "speed_zone", "crosswalk", "escort"],
"newTools": ["escort"],
```

- [x] **Step 2: Run the balance suite and read the report**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Then read: `cat app/build/reports/campaign-balance.txt | grep -A6 "^Level 5:"`
If level 5 still fails (no 3-star solution, or fewer than 2 solutions), increase `"budget"` by 1 and re-run. Repeat until level 5 passes.
Expected: level 5 passes; levels 6,7,9,10,11 still fail (not yet wired) — that's expected.

- [x] **Step 3: Commit**

```bash
git add assets/game/files/campaign.json
git commit -m "feat(campaign): introduce Acompañar in level 5"
```

---

## Task 7: Wire `escort` into levels 6, 7, 9, 11 (carry-over) and fix the `lighting` bug in level 7

**Files:**
- Modify: `assets/game/files/campaign.json` (levels 6, 7, 9, 11)

- [x] **Step 1: Level 6** — add `"escort"` to `"tools"` (not `newTools`, already introduced): `["look", "eye", "signal", "crosswalk", "visible", "escort"]`.

- [x] **Step 2: Level 7** — add `"escort"` to `"tools"` AND remove `"lighting"` (bug: level 7 is `night:false`, `lighting` only matters at night): `"tools"` becomes `["look", "eye", "speed_zone", "crosswalk", "escort"]`.

- [x] **Step 3: Level 9** — add `"escort"` to `"tools"`: `["look", "eye", "visible", "lighting", "speed_zone", "crosswalk", "escort"]`.

- [x] **Step 4: Level 11** — add `"escort"` to `"tools"`: `["look", "eye", "visible", "lighting", "speed_zone", "refuge", "signal", "escort"]`.

- [x] **Step 5: Run the balance suite and read the report**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Then: `cat app/build/reports/campaign-balance.txt | grep -A8 -E "^Level (6|7|9|11):"`
For any of these four that still fails, increase that level's `"budget"` by 1 and re-run. Repeat per-level until all four pass.
Expected: levels 5,6,7,9,11 pass; level 10 still fails (not touched yet — Task 8).

- [x] **Step 6: Commit**

```bash
git add assets/game/files/campaign.json
git commit -m "feat(campaign): carry Acompañar into levels 6, 7, 9, 11; fix lighting-by-day bug in level 7"
```

---

## Task 8: Blind-spot vehicle in level 10

**Files:**
- Modify: `assets/game/files/campaign.json` (level 10)

**Why level 10:** it's already about something hiding the pedestrian's view (a bus stop); adding a vehicle whose *driver* is also hindered gives it a genuinely new lesson instead of being "level 4 again, at night" — and makes `visible`/`crosswalk`/`lighting` matter again instead of feeling pre-learned.

- [x] **Step 1: Mark one lane as blind-spot and update the lesson**

Level `id: 10`, second lane (`y: 0.68`): change

```json
{
  "y": 0.68,
  "speed": -0.12,
  "offset": 0.97,
  "car": "car_blue",
  "twoWay": true,
  "oncomingOffset": 0.0
}
```

to

```json
{
  "y": 0.68,
  "speed": -0.12,
  "offset": 0.97,
  "car": "car_green",
  "twoWay": true,
  "oncomingOffset": 0.0,
  "blindSpot": true
}
```

And change the lesson from

```json
"lesson": "El Centro está tapado por la parada. Busca los laterales despejados, guarda el celular y prepara la visibilidad nocturna.",
```

to

```json
"lesson": "El Centro está tapado por la parada, y uno de los carriles lo ocupa un vehículo grande: su conductor te detecta mucho más tarde. Busca los laterales despejados, sé visible, y guarda el celular.",
```

- [x] **Step 2: Add `escort` (carry-over, Ana appears here)**

Add `"escort"` to `"tools"`: `["look", "eye", "phone_away", "visible", "lighting", "speed_zone", "crosswalk", "escort"]`.

- [x] **Step 3: Run the balance suite and read the report**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Then: `cat app/build/reports/campaign-balance.txt | grep -A8 "^Level 10:"`
If it fails, increase `"budget"` by 1 and re-run. Repeat until it passes.
Expected: level 10 passes.

- [x] **Step 4: Commit**

```bash
git add assets/game/files/campaign.json
git commit -m "feat(campaign): blind-spot vehicle in level 10"
```

---

## Task 9: Full rebalance confirmation

**Files:** none (verification-only task; fixes land in whichever level's JSON still fails)

- [x] **Step 1: Run the complete suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. If any level still fails, read `app/build/reports/campaign-balance.txt` for that level's ID, adjust its `"budget"` (or, if a specific tool is clearly the blocker, that tool's `"cost"` in the `tools` array) by small increments, and re-run until green. Do this for every remaining failure before moving on.

- [x] **Step 2: Confirm the "unprepared walker is always caught" guarantee still holds for every touched level**

This mirrors the ad-hoc sweep used earlier this session for the ambush-car work — write it as a temporary test, run it, then delete it (don't leave it in the repo):

```kotlin
package com.example.crosswalker.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TempRedesignSweepTest {
    private val root = JSONObject(java.io.File("src/main/assets/campaign.json").readText())
    private val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
    private fun finish(s: Simulation): Simulation { repeat(1801) { if(s.phase == Phase.RUNNING) s.tick() }; return s }
    private fun plan(l: Level, crossing: Int, skill: Set<String>) =
        Plan(l.walkers.associate { it.id to Assignment(crossing, skill.toMutableSet()) }.toMutableMap())

    @Test fun unpreparedAlwaysCaughtOnEveryCrossing() {
        val failures = mutableListOf<String>()
        for(l in levels.filter { !it.tutorial }) for(c in 0..2) {
            val s = finish(Simulation(l, plan(l, c, emptySet())))
            if(s.phase != Phase.RETRY) failures.add("level=${l.id} crossing=$c phase=${s.phase}")
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }
}
```

Save it as `app/src/test/java/com/example/crosswalker/game/TempRedesignSweepTest.kt`, run:
`./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.TempRedesignSweepTest"`
Expected: PASS. Then delete the file (`rm app/src/test/java/com/example/crosswalker/game/TempRedesignSweepTest.kt`) and run the full suite once more to confirm it's clean.

- [x] **Step 3: Commit whatever budget/cost adjustments were needed**

```bash
git add assets/game/files/campaign.json
git commit -m "fix(campaign): rebalance budgets after Acompañar and blind-spot changes"
```

(Skip this commit if Step 1 required no adjustments at all.)

---

## Task 10: Permanent guard test — no ficha sin sentido

**Files:**
- Modify: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Consumes: `Level.tools`, `Level.night`, `Level.island`, `Walker.kind` (existing).
- Produces: a standing regression guard so a future level can't silently offer `lighting`/`visible` by day, `refuge` without an island, or `escort` with no `impulsive` walker — the exact bug class fixed in Tasks 5 and 7.

- [x] **Step 1: Write the test**

Add to `SimulationTest.kt`:

```kotlin
@Test fun noLevelOffersAToolThatCannotPossiblyMatterThere() {
    val failures = mutableListOf<String>()
    for(l in levels) {
        if("lighting" in l.tools && !l.night) failures.add("level=${l.id} offers lighting but night=false")
        if("visible" in l.tools && !l.night) failures.add("level=${l.id} offers visible but night=false")
        if("refuge" in l.tools && !l.island) failures.add("level=${l.id} offers refuge but island=false")
        if("escort" in l.tools && l.walkers.none { it.kind == "impulsive" }) failures.add("level=${l.id} offers escort but no impulsive walker")
    }
    assertTrue(failures.toString(), failures.isEmpty())
}
```

- [x] **Step 2: Run it**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: PASS — Tasks 4-8 already fixed every case this test checks. If it fails, that means a level from Tasks 5-8 was missed; go fix that level's `tools` list before continuing.

- [x] **Step 3: Commit**

```bash
git add app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "test(game): guard against ficha offered where it cannot matter"
```

---

## Task 11: Parked car reads as parked, not as traffic (Board.kt, visual A+B)

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**No JVM test** (Compose Canvas rendering — not unit-testable in this environment, no Android emulator available). Verification step is "compiles" plus a note for the user to playtest visually before merging.

- [x] **Step 1: Replace the "VISTA TAPADA" block**

In `Board.kt`, inside the `Simulation.crossings.forEachIndexed { i,x -> ... }` loop (the one that currently draws `crosswalk`/ramp markers too), change:

```kotlin
if(level.sight[i]<.5f) {
    sprite("car_blue",x*360-24,322f,48f,28f)
    label("VISTA TAPADA",x*360,319f,7f,"#FFE07A")
}
```

to:

```kotlin
if(level.sight[i]<.5f) {
    val cx=x*360f
    // Parked, not driving: rotated 90° around its own centre so it never
    // reads as moving traffic, unlike every lane car on the board.
    c.save();c.rotate(90f,cx,311f);sprite("car_blue",cx-24,297f,48f,28f);c.restore()
    // A fixed hazard, not a ficha the player chose: a warm/red chip (every
    // chosen-ficha label on this board — ZONA LENTA, RAMPA, LUZ — is yellow),
    // plus a small eye-slash icon.
    rect(cx-36f,286f,72f,11f,"#D9483F")
    color("#FFF8E6");p.style=Paint.Style.STROKE;p.strokeWidth=1.2f
    c.drawCircle(cx-28f,291.5f,3f,p);c.drawLine(cx-31f,294.5f,cx-25f,288.5f,p)
    label("AUTO ESTACIONADO",cx+6,294f,6f,"#FFF8E6")
}
```

- [x] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): parked car reads as a fixed hazard, not traffic or a chosen ficha"
```

**Note for the user:** no emulator is available in this environment — please run the app and check levels 4, 7, 10, 11, 12 (every level with a blocked-sight crossing) before merging, specifically that the rotated sprite and the warning chip don't collide with the ramp marker when both appear at the same crossing (they currently never do in any of these 5 levels, but confirm visually).

---

## Task 12: Blind-spot vehicle renders bigger (Board.kt)

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**No JVM test** (rendering). Verification: compiles; visual check deferred to the user.

- [x] **Step 1: Make the car sprite size depend on `lane.blindSpot`**

In `Board.kt`, inside `level.lanes.forEach { lane -> ... }`, right before the line `val x=preview.carX(lane)*360f; ...`, add:

```kotlin
val carW=if(lane.blindSpot) 64f else 52f
val carH=if(lane.blindSpot) 44f else 38f
```

Then replace the three hardcoded `52f,38f` pairs in that same lane block (main car, oncoming car, ambush car — the three `sprite(lane.car, ..., 52f, 38f)` calls) with `carW,carH`.

- [x] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): blind-spot vehicles render larger"
```

**Note for the user:** playtest level 10 specifically — confirm the larger `car_green` doesn't visually overlap the lane markings or the other lane's cars.

---

## Task 13: Sight-distance guide line during planning (Board.kt + Simulation.kt, visual C)

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Board.kt`

**Interfaces:**
- Consumes: `Simulation.sightRange(walkerId: String): Float` (added in Task 3).

**No JVM test** (rendering). Verification: compiles; visual check deferred to the user.

- [x] **Step 1: Draw the guide line**

In `Board.kt`, inside the `level.walkers.forEachIndexed { i,w -> ... }` loop, right after the line that computes `val y=(person?.y ?: .91f)*410f`, add:

```kotlin
if(planning) {
    val range = preview.sightRange(w.id) * 360f
    val crossX = Simulation.crossings[plan.people[w.id]?.crossing ?: 1] * 360f
    val laneY = (level.lanes.maxByOrNull { it.y }?.y ?: .5f) * 410f
    color(if(preview.sightRange(w.id) < .5f) "#F27A6B" else "#FFE07A")
    p.style = Paint.Style.STROKE; p.strokeWidth = 2f
    p.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    c.drawLine(crossX - range, laneY, crossX + range, laneY, p)
    p.pathEffect = null
}
```

- [x] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Board.kt
git commit -m "feat(ui): show each walker's real sight range while planning"
```

**Note for the user:** this is the highest-impact visual change in the whole plan — please playtest levels 1 (full sight, baseline), 4, 7, 10, 11, 12 (reduced sight) and confirm the line reads clearly against the road art and doesn't overlap the walker-selection oval or other UI.

---

## Task 14: Final full regression

**Files:** none (verification only)

- [x] **Step 1: Run the entire JVM suite one more time**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, every test green (`SimulationTest`, `ParsingTest`, `TutorialStepTest`, `AudioMappingTest`, `ExampleUnitTest`).

- [x] **Step 2: Confirm `git status` shows only the expected files**

Run: `git status --short`
Expected: changes only in `assets/game/files/campaign.json`, `app/src/main/java/com/example/crosswalker/game/Simulation.kt`, `app/src/main/java/com/example/crosswalker/Board.kt`, `app/src/test/java/com/example/crosswalker/SimulationTest.kt`, `app/src/test/java/com/example/crosswalker/game/ParsingTest.kt`, and the two docs files under `docs/design/`. No leftover temp test files from Task 9.

- [x] **Step 3: Summarize for the user**

Report: which levels had their budget adjusted and by how much (from Tasks 6-9), and the explicit reminder that `Board.kt` changes (Tasks 11-13) have not been visually verified — no Android emulator in this environment — and need a real playtest before merging, same as every other visual change made earlier this session.
