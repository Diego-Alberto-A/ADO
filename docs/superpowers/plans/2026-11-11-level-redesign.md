# Rediseño de los 12 niveles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convertir los 12 niveles actuales (casi idénticos, con las 9 fichas disponibles desde el inicio) en un currículo incremental: nivel 1 como tutorial real, fichas que cada nivel habilita según su entorno, mapas distintos por nivel, y una tarjeta de "ficha nueva" al tocar un nivel.

**Architecture:** Extensión mínima del modelo de datos existente (`Level` gana `tools`, `newTools`, `tutorial`, `introLines`, `readyMessage`); `Board.kt` no se toca (ya es 100% data-driven sobre `level.lanes`); el tutorial reutiliza `DialogueBox` con una cola de líneas que avanza al tocar la caja. Dos arquetipos de peatón nuevos (`wheelchair`, `phone`) y una ficha nueva (`phone_away`) se añaden a `Simulation.kt` con 4 líneas de código.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 (JVM unit tests en `app/src/test`), Compose UI testing + Espresso (instrumented tests en `app/src/androidTest`), org.json.

**Spec:** `docs/superpowers/specs/2026-11-11-level-redesign-design.md`

## Global Constraints

- La interfaz visual (pantallas, estilos, componentes) no cambia — solo contenido y modelo de datos. No se crean pantallas nuevas.
- `Board.kt` no se modifica: el tablero ya es data-driven sobre `level.lanes`; la variedad de mapa se logra solo con datos.
- `Simulation.crossings` (3 puntos fijos Izq./Centro/Der.) no cambia.
- No se genera arte nuevo. Los peatones nuevos reutilizan los sprites existentes (`walker_coral`, `walker_blue`, `walker_green`); nunca dos peatones con el mismo sprite en el mismo nivel.
- Los 3 mundos y su orden no cambian.
- Todos los campos nuevos de `Level` tienen un valor por defecto seguro, para no romper los sitios que ya construyen `Level(...)` posicionalmente (p. ej. el helper `level()` en `SimulationTest.kt`).

## File Structure

- `app/build.gradle.kts` — gana `testImplementation("org.json:json:20231013")` (el stub de Android para `org.json` lanza excepciones en pruebas JVM puras; esta dependencia trae la implementación real solo para tests).
- `app/src/main/java/com/example/crosswalker/game/Simulation.kt` — `Level` gana 5 campos nuevos; `tick()` gana las reglas de `wheelchair`/`phone`; dos funciones nuevas `parseTool`/`parseLevel` (puras, sin `Context`).
- `app/src/main/java/com/example/crosswalker/data/GameRepository.kt` — usa `parseTool`/`parseLevel` en vez de parsear inline.
- `app/src/main/assets/campaign.json` — reescritura completa: 10 fichas (9 + `phone_away`), 12 niveles con mapas y fichas distintas.
- `app/src/main/java/com/example/crosswalker/CraftArt.kt` — `ToolArtwork` gana la rama `"phone_away"`.
- `app/src/main/java/com/example/crosswalker/Dialogue.kt` — `DialogueBox` gana `onAdvance: (() -> Unit)? = null`.
- `app/src/main/java/com/example/crosswalker/MainActivity.kt` — `Session` gana `introIndex`; `toggle()` deja de sobrescribir `message` en el tutorial; `Game` calcula el mensaje reactivo del tutorial y filtra el panel de fichas por `level.tools`; `WorldMap` añade la sección "FICHA NUEVA" al diálogo de nivel.
- `app/src/test/java/com/example/crosswalker/game/ParsingTest.kt` — nuevo, prueba `parseTool`/`parseLevel`.
- `app/src/test/java/com/example/crosswalker/SimulationTest.kt` — nuevos tests de `wheelchair`/`phone`; reemplaza `campaignArchetypesHaveSafeSolutions`.
- `app/src/androidTest/java/com/example/crosswalker/NavigationTransitionsTest.kt` — `"Primeros pasos"` → `"Mi primer cruce"`.
- `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt` — nuevo.
- `app/src/androidTest/java/com/example/crosswalker/LevelContentTest.kt` — nuevo.

---

### Task 1: Parsing puro de `Level`/`Tool` + campos nuevos

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt`
- Modify: `app/src/main/java/com/example/crosswalker/data/GameRepository.kt`
- Test: `app/src/test/java/com/example/crosswalker/game/ParsingTest.kt`

**Interfaces:**
- Produces: `data class Level(id: Int, world: Int, title: String, lesson: String, budget: Int, night: Boolean, lanes: List<Lane>, walkers: List<Walker>, tools: List<String> = emptyList(), newTools: List<String> = emptyList(), tutorial: Boolean = false, introLines: List<String> = emptyList(), readyMessage: String = "")`
- Produces: `fun parseTool(json: JSONObject): Tool`
- Produces: `fun parseLevel(json: JSONObject): Level`

- [ ] **Step 1: Añadir la dependencia de test para `org.json`**

En `app/build.gradle.kts`, dentro del bloque `dependencies { ... }`, añade:

```kotlin
    testImplementation("org.json:json:20231013")
```

- [ ] **Step 2: Escribir el test que falla**

Crea `app/src/test/java/com/example/crosswalker/game/ParsingTest.kt`:

```kotlin
package com.example.crosswalker.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParsingTest {
    @Test fun parseToolReadsAllFields() {
        val json = JSONObject(
            """{"id":"look","label":"Mirar","icon":"◉","cost":1,"description":"Mira antes de cruzar.","category":"skill"}"""
        )
        val tool = parseTool(json)
        assertEquals(Tool("look","Mirar","◉",1,"Mira antes de cruzar.","skill"), tool)
    }

    @Test fun parseLevelReadsNewFieldsWithDefaultsWhenMissing() {
        val json = JSONObject(
            """{"id":1,"world":0,"title":"Mi primer cruce","lesson":"Toca MIRAR.","budget":3,"night":false,
                "lanes":[{"y":0.5,"speed":0.11,"offset":0.6,"car":"car_coral"}],
                "walkers":[{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
                "tools":["look"]}"""
        )
        val level = parseLevel(json)
        assertEquals(listOf("look"), level.tools)
        assertEquals(emptyList<String>(), level.newTools)
        assertFalse(level.tutorial)
        assertEquals(emptyList<String>(), level.introLines)
        assertEquals("", level.readyMessage)
    }

    @Test fun parseLevelReadsTutorialFieldsWhenPresent() {
        val json = JSONObject(
            """{"id":1,"world":0,"title":"Mi primer cruce","lesson":"Toca MIRAR.","budget":3,"night":false,
                "lanes":[{"y":0.5,"speed":0.11,"offset":0.6,"car":"car_coral"}],
                "walkers":[{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
                "tools":["look"],"newTools":["look"],"tutorial":true,
                "introLines":["¡Hola! Soy Ana.","Vamos paso a paso."],"readyMessage":"¡Lanza el plan!"}"""
        )
        val level = parseLevel(json)
        assertEquals(listOf("look"), level.newTools)
        assertTrue(level.tutorial)
        assertEquals(listOf("¡Hola! Soy Ana.","Vamos paso a paso."), level.introLines)
        assertEquals("¡Lanza el plan!", level.readyMessage)
    }
}
```

- [ ] **Step 3: Verificar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.ParsingTest"`
Expected: FAIL (no existe `parseTool`/`parseLevel`, ni los campos nuevos en `Level`).

- [ ] **Step 4: Implementar `Level` con los campos nuevos y `parseTool`/`parseLevel`**

En `app/src/main/java/com/example/crosswalker/game/Simulation.kt`, reemplaza:

```kotlin
data class Level(val id: Int, val world: Int, val title: String, val lesson: String, val budget: Int, val night: Boolean, val lanes: List<Lane>, val walkers: List<Walker>)
```

por:

```kotlin
data class Level(
    val id: Int, val world: Int, val title: String, val lesson: String,
    val budget: Int, val night: Boolean, val lanes: List<Lane>, val walkers: List<Walker>,
    val tools: List<String> = emptyList(),
    val newTools: List<String> = emptyList(),
    val tutorial: Boolean = false,
    val introLines: List<String> = emptyList(),
    val readyMessage: String = ""
)
```

Añade al final del archivo (fuera de la clase `Simulation`, al mismo nivel que `data class Level`), junto con el import `import org.json.JSONObject` al principio del archivo:

```kotlin
fun parseTool(json: JSONObject) = Tool(
    json.getString("id"), json.getString("label"), json.getString("icon"),
    json.getInt("cost"), json.getString("description"), json.getString("category")
)

private fun JSONObject.stringList(key: String): List<String> =
    if (has(key)) getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } } else emptyList()

fun parseLevel(json: JSONObject): Level {
    val lanes = json.getJSONArray("lanes").let { a -> List(a.length()) { i -> a.getJSONObject(i).let {
        Lane(it.getDouble("y").toFloat(), it.getDouble("speed").toFloat(), it.getDouble("offset").toFloat(), it.getString("car"))
    } } }
    val walkers = json.getJSONArray("walkers").let { a -> List(a.length()) { i -> a.getJSONObject(i).let {
        Walker(it.getString("id"), it.getString("name"), it.getString("kind"), it.getDouble("speed").toFloat(), it.getString("sprite"))
    } } }
    return Level(
        json.getInt("id"), json.getInt("world"), json.getString("title"), json.getString("lesson"),
        json.getInt("budget"), json.getBoolean("night"), lanes, walkers,
        json.stringList("tools"), json.stringList("newTools"),
        json.optBoolean("tutorial", false), json.stringList("introLines"),
        json.optString("readyMessage", "")
    )
}
```

- [ ] **Step 5: Usar `parseTool`/`parseLevel` en `GameRepository`**

En `app/src/main/java/com/example/crosswalker/data/GameRepository.kt`, reemplaza las líneas que construyen `tools` y `levels` inline por:

```kotlin
    val tools = root.getJSONArray("tools").let { a -> List(a.length()) { parseTool(a.getJSONObject(it)) } }
    val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
```

- [ ] **Step 6: Verificar que el test pasa**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.ParsingTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Verificar que no se rompió nada existente**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (todos los tests existentes siguen verdes; `SimulationTest` construye `Level` sin los campos nuevos y debe seguir compilando gracias a los defaults).

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/main/java/com/example/crosswalker/data/GameRepository.kt app/src/test/java/com/example/crosswalker/game/ParsingTest.kt
git commit -m "feat: parsear fichas y niveles por nivel desde JSON"
```

---

### Task 2: Peatones en silla de ruedas y con celular

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/game/Simulation.kt`
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Consumes: `Level`, `Walker`, `Plan`, `Assignment`, `Simulation` (ya existentes).
- Produces: comportamiento de `kind == "wheelchair"` (necesita `"crosswalk"` en `plan.street` para velocidad normal) y `kind == "phone"` (necesita `"phone_away"` en las habilidades propias para que `"look"` proteja).

- [ ] **Step 1: Escribir los tests que fallan**

Añade al final de la clase `SimulationTest` (mismo archivo, reutiliza los helpers privados `level()`/`finish()` ya existentes):

```kotlin
    @Test fun wheelchairNeedsCrosswalkForFullSpeed() {
        val rider = Walker("mateo","Mateo","wheelchair",.12f,"walker_green")
        val withRamp = finish(Simulation(level().copy(walkers=listOf(rider)),
            Plan(mutableMapOf("mateo" to Assignment(1, mutableSetOf("look"))), mutableSetOf("crosswalk"))))
        val withoutRamp = finish(Simulation(level().copy(walkers=listOf(rider)),
            Plan(mutableMapOf("mateo" to Assignment(1, mutableSetOf("look"))))))
        assertEquals(Phase.WON, withRamp.phase)
        assertEquals(Phase.WON, withoutRamp.phase)
        assertTrue(withoutRamp.time > withRamp.time)
    }

    @Test fun phoneKindGetsNoBenefitFromLookAlone() {
        val teen = Walker("tomas","Tomás","phone",.12f,"walker_blue")
        val withLookOnly = Simulation(level().copy(walkers=listOf(teen)),
            Plan(mutableMapOf("tomas" to Assignment(1, mutableSetOf("look")))))
        val withNoSkills = Simulation(level().copy(walkers=listOf(teen)),
            Plan(mutableMapOf("tomas" to Assignment(1, mutableSetOf()))))
        repeat(1800) { withLookOnly.tick(); withNoSkills.tick() }
        assertEquals(withNoSkills.phase, withLookOnly.phase)
        assertEquals(withNoSkills.people[0].y, withLookOnly.people[0].y, 0f)
    }

    @Test fun phoneAwayRestoresLooksProtection() {
        val teen = Walker("tomas","Tomás","phone",.12f,"walker_blue")
        val protected = finish(Simulation(level().copy(walkers=listOf(teen)),
            Plan(mutableMapOf("tomas" to Assignment(1, mutableSetOf("look","phone_away"))))))
        assertEquals(Phase.WON, protected.phase)
    }
```

- [ ] **Step 2: Verificar que fallan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: FAIL (`wheelchairNeedsCrosswalkForFullSpeed` y `phoneAwayRestoresLooksProtection` probablemente no terminan en `WON` distinto, o fallan por tiempos iguales; `phoneKindGetsNoBenefitFromLookAlone` falla porque hoy `"look"` sí tiene efecto para cualquier `kind`).

- [ ] **Step 3: Implementar las reglas en `tick()`**

En `app/src/main/java/com/example/crosswalker/game/Simulation.kt`, dentro de `tick()`, reemplaza:

```kotlin
            var velocity = p.walker.speed * (if("crosswalk" in plan.street) 1.2f else 1f)
            if(p.walker.kind == "distracted" && "look" !in skills) velocity *= .75f
            val lane = level.lanes.firstOrNull { p.y > it.y + .065f && p.y - it.y < .15f }
            if(lane != null && "look" in skills) {
```

por:

```kotlin
            var velocity = p.walker.speed * (if("crosswalk" in plan.street) 1.2f else 1f)
            if(p.walker.kind == "distracted" && "look" !in skills) velocity *= .75f
            if(p.walker.kind == "wheelchair" && "crosswalk" !in plan.street) velocity *= .5f
            if(p.walker.kind == "phone" && "phone_away" !in skills) velocity *= .5f
            val looking = "look" in skills && (p.walker.kind != "phone" || "phone_away" in skills)
            val lane = level.lanes.firstOrNull { p.y > it.y + .065f && p.y - it.y < .15f }
            if(lane != null && looking) {
```

- [ ] **Step 4: Verificar que pasan**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: PASS (todos, incluidos los 3 nuevos).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/game/Simulation.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat: peatones en silla de ruedas y distraidos con el celular"
```

---

### Task 3: Contenido de los 12 niveles + ficha "Guarda el celular"

**Files:**
- Modify: `app/src/main/assets/campaign.json` (reescritura completa)
- Modify: `app/src/main/java/com/example/crosswalker/CraftArt.kt`
- Modify: `app/src/androidTest/java/com/example/crosswalker/NavigationTransitionsTest.kt`
- Test: `app/src/test/java/com/example/crosswalker/SimulationTest.kt`

**Interfaces:**
- Consumes: `parseTool`, `parseLevel` (Task 1), `Simulation`, `Phase`, `Plan`, `Assignment` (existentes).
- Produces: el contenido real de `campaign.json` que todas las tareas de UI (4 y 5) van a ejercitar.

- [ ] **Step 1: Escribir el test de contenido que falla**

Añade al final de `SimulationTest.kt`, **reemplazando** el test existente `campaignArchetypesHaveSafeSolutions` (que asumía un patrón de 3 carriles fijo que ya no aplica):

```kotlin
    @Test fun allLevelsHaveASafeSolutionWithTheirAvailableTools() {
        val root = org.json.JSONObject(java.io.File("src/main/assets/campaign.json").readText())
        val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
        for (level in levels) {
            val safeSkill = if (level.night) "visible" else "eye"
            val people = level.walkers.associate { w ->
                val extras = mutableSetOf("look", safeSkill)
                if (w.kind == "phone") extras.add("phone_away")
                w.id to Assignment(1, extras)
            }.toMutableMap()
            val street = if (level.walkers.any { it.kind == "wheelchair" }) mutableSetOf("crosswalk") else mutableSetOf()
            val s = Simulation(level, Plan(people, street))
            repeat(1801) { s.tick() }
            assertEquals("Level ${level.id} (${level.title})", Phase.WON, s.phase)
        }
    }
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: FAIL — `campaign.json` todavía no tiene `tools`/el contenido nuevo (o directamente no compila porque `campaignArchetypesHaveSafeSolutions` ya no existe en el código fuente si lo borraste primero; bórralo antes de este paso).

- [ ] **Step 3: Reescribir `campaign.json`**

Reemplaza TODO el contenido de `app/src/main/assets/campaign.json` por:

```json
{
  "worlds": [
    "Mi pequeño barrio",
    "Campus de innovación",
    "La ciudad de noche"
  ],
  "tools": [
    {"id":"look","label":"Mirar","icon":"◉","cost":1,"description":"Espera un hueco seguro antes de entrar a cada carril.","category":"skill"},
    {"id":"wait","label":"Esperar","icon":"Ⅱ","cost":1,"description":"Espera tres segundos en el bordillo antes de salir.","category":"skill"},
    {"id":"eye","label":"Contacto","icon":"☺","cost":2,"description":"El conductor te ve y cede el paso durante el día.","category":"skill"},
    {"id":"signal","label":"Semáforo","icon":"▣","cost":3,"description":"En el cruce central, solicita detener el tráfico.","category":"skill"},
    {"id":"visible","label":"Reflector","icon":"✦","cost":2,"description":"Permite que los conductores te vean y cedan el paso de noche.","category":"skill"},
    {"id":"phone_away","label":"Guarda el celular","icon":"✕","cost":1,"description":"Guarda el teléfono antes de cruzar para prestar atención a la calle.","category":"skill"},
    {"id":"officer","label":"Agente","icon":"✋","cost":4,"description":"Detiene el tráfico al inicio: ¡crucen todos juntos y rápido!","category":"street"},
    {"id":"school","label":"Zona escolar","icon":"⚑","cost":2,"description":"Reduce la velocidad del tráfico en toda la calle.","category":"street"},
    {"id":"bump","label":"Reductor","icon":"≋","cost":2,"description":"Los coches circulan más despacio.","category":"street"},
    {"id":"crosswalk","label":"Paso cebra","icon":"▤","cost":2,"description":"Un paso marcado hace el recorrido más rápido, y es indispensable para quien usa silla de ruedas.","category":"street"}
  ],
  "levels": [
    {
      "id": 1, "world": 0, "title": "Mi primer cruce",
      "lesson": "Ahora toca HABILIDADES y elige la ficha MIRAR, para que sepa si viene un coche.",
      "budget": 3, "night": false,
      "walkers": [{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
      "lanes": [{"y":0.50,"speed":0.11,"offset":0.60,"car":"car_coral"}],
      "tools": ["look"], "newTools": ["look"], "tutorial": true,
      "introLines": [
        "¡Hola! Soy Ana.",
        "Hoy aprenderás a cruzar la calle sin peligro. Vamos paso a paso.",
        "Arriba a la izquierda está PAUSA (Ⅱ), por si quieres detenerte un momento.",
        "Arriba a la derecha están tus FICHAS ●: cada decisión cuesta algunas, ¡hoy tienes de sobra!",
        "Hay dos pestañas: HABILIDADES son para mí, y LA CALLE protege todo el camino. Hoy usamos HABILIDADES.",
        "Elige por dónde cruzo: Izquierda, Centro o Derecha. Te recomiendo el Centro."
      ],
      "readyMessage": "¡Muy bien! Toca PONER EL PLAN EN MARCHA para intentarlo."
    },
    {
      "id": 2, "world": 0, "title": "Para y mira",
      "lesson": "Para y mira antes de cruzar: así evitas las prisas.",
      "budget": 6, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"}
      ],
      "lanes": [
        {"y":0.35,"speed":0.13,"offset":0.40,"car":"car_coral"},
        {"y":0.65,"speed":-0.12,"offset":0.90,"car":"car_blue"}
      ],
      "tools": ["look","wait","bump"], "newTools": ["wait","bump"]
    },
    {
      "id": 3, "world": 0, "title": "Cada quien su paso",
      "lesson": "Mira al conductor a los ojos: así sabes que te vio. Luz necesita más tiempo para cruzar.",
      "budget": 8, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.35,"speed":0.14,"offset":0.45,"car":"car_yellow"},
        {"y":0.65,"speed":-0.13,"offset":1.00,"car":"car_green"}
      ],
      "tools": ["look","wait","eye","bump"], "newTools": ["eye"]
    },
    {
      "id": 4, "world": 0, "title": "Camino a la escuela",
      "lesson": "Cerca de la escuela, el tráfico va más despacio y el paso cebra te hace cruzar más rápido.",
      "budget": 10, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.28,"speed":0.15,"offset":0.35,"car":"car_coral"},
        {"y":0.49,"speed":-0.14,"offset":0.90,"car":"car_blue"},
        {"y":0.70,"speed":0.13,"offset":0.60,"car":"car_yellow"}
      ],
      "tools": ["look","wait","eye","school","crosswalk"], "newTools": ["school","crosswalk"]
    },
    {
      "id": 5, "world": 1, "title": "La gran avenida",
      "lesson": "Una avenida grande tiene más carriles: usa lo que ya sabes y ten paciencia.",
      "budget": 10, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.24,"speed":0.17,"offset":0.30,"car":"car_coral"},
        {"y":0.40,"speed":-0.16,"offset":0.95,"car":"car_blue"},
        {"y":0.56,"speed":0.15,"offset":0.55,"car":"car_yellow"},
        {"y":0.72,"speed":-0.14,"offset":1.10,"car":"car_green"}
      ],
      "tools": ["look","wait","eye","crosswalk"], "newTools": []
    },
    {
      "id": 6, "world": 1, "title": "El cruce con semáforo",
      "lesson": "El semáforo detiene el tráfico, pero solo si cruzas por el Centro.",
      "budget": 8, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"}
      ],
      "lanes": [
        {"y":0.28,"speed":0.14,"offset":0.40,"car":"car_coral"},
        {"y":0.49,"speed":-0.13,"offset":0.95,"car":"car_blue"},
        {"y":0.70,"speed":0.12,"offset":0.65,"car":"car_yellow"}
      ],
      "tools": ["look","wait","eye","crosswalk","signal"], "newTools": ["signal"]
    },
    {
      "id": 7, "world": 1, "title": "El agente de tránsito",
      "lesson": "El agente detiene el tráfico al inicio: ¡crucen todos juntos y rápido!",
      "budget": 11, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.24,"speed":0.16,"offset":0.35,"car":"car_coral"},
        {"y":0.40,"speed":-0.15,"offset":1.00,"car":"car_blue"},
        {"y":0.56,"speed":0.14,"offset":0.60,"car":"car_yellow"},
        {"y":0.72,"speed":-0.13,"offset":1.15,"car":"car_green"}
      ],
      "tools": ["look","wait","eye","crosswalk","signal","officer"], "newTools": ["officer"]
    },
    {
      "id": 8, "world": 1, "title": "Un compañero especial",
      "lesson": "Mateo usa silla de ruedas: necesita el paso cebra para cruzar a tiempo.",
      "budget": 9, "night": false,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"mateo","name":"Mateo","kind":"wheelchair","speed":0.10,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.28,"speed":0.13,"offset":0.40,"car":"car_coral"},
        {"y":0.49,"speed":-0.12,"offset":0.90,"car":"car_blue"},
        {"y":0.70,"speed":0.11,"offset":0.60,"car":"car_yellow"}
      ],
      "tools": ["look","wait","eye","crosswalk","signal"], "newTools": []
    },
    {
      "id": 9, "world": 2, "title": "Luces al anochecer",
      "lesson": "De noche hace falta el reflector para que los conductores te vean.",
      "budget": 5, "night": true,
      "walkers": [{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
      "lanes": [{"y":0.50,"speed":0.12,"offset":0.60,"car":"car_coral"}],
      "tools": ["look","visible"], "newTools": ["visible"]
    },
    {
      "id": 10, "world": 2, "title": "La parada del camión",
      "lesson": "Tomás va viendo su celular: si no lo guarda, Mirar no sirve de nada.",
      "budget": 8, "night": true,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"tomas","name":"Tomás","kind":"phone","speed":0.11,"sprite":"walker_blue"}
      ],
      "lanes": [
        {"y":0.35,"speed":0.14,"offset":0.45,"car":"car_blue"},
        {"y":0.65,"speed":-0.13,"offset":1.00,"car":"car_yellow"}
      ],
      "tools": ["look","wait","visible","phone_away"], "newTools": ["phone_away"]
    },
    {
      "id": 11, "world": 2, "title": "Juntos de noche",
      "lesson": "De noche, cuida especialmente a quien camina más despacio.",
      "budget": 11, "night": true,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.28,"speed":0.16,"offset":0.40,"car":"car_coral"},
        {"y":0.49,"speed":-0.15,"offset":0.95,"car":"car_blue"},
        {"y":0.70,"speed":0.14,"offset":0.65,"car":"car_yellow"}
      ],
      "tools": ["look","wait","visible","officer","bump"], "newTools": []
    },
    {
      "id": 12, "world": 2, "title": "Una ciudad segura",
      "lesson": "Pon en práctica todo lo que aprendiste en tu ciudad.",
      "budget": 11, "night": true,
      "walkers": [
        {"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"},
        {"id":"leo","name":"Leo","kind":"distracted","speed":0.10,"sprite":"walker_blue"},
        {"id":"luz","name":"Luz","kind":"slow","speed":0.075,"sprite":"walker_green"}
      ],
      "lanes": [
        {"y":0.24,"speed":0.19,"offset":0.30,"car":"car_coral"},
        {"y":0.40,"speed":-0.18,"offset":1.00,"car":"car_blue"},
        {"y":0.56,"speed":0.17,"offset":0.60,"car":"car_yellow"},
        {"y":0.72,"speed":-0.16,"offset":1.20,"car":"car_green"}
      ],
      "tools": ["look","wait","visible","signal","officer","school","bump","crosswalk","phone_away"], "newTools": []
    }
  ]
}
```

- [ ] **Step 4: Añadir el ícono de "Guarda el celular"**

En `app/src/main/java/com/example/crosswalker/CraftArt.kt`, dentro de `ToolArtwork`, añade una rama antes del `else`:

```kotlin
        "phone_away" -> R.drawable.icon_wait
```

(Reutiliza el ícono de "esperar" como marcador visual más cercano hasta que se genere arte dedicado.)

- [ ] **Step 5: Actualizar el título de nivel 1 en el test instrumentado existente**

En `app/src/androidTest/java/com/example/crosswalker/NavigationTransitionsTest.kt`, reemplaza las dos ocurrencias de `"Primeros pasos"` por `"Mi primer cruce"`.

- [ ] **Step 6: Verificar que el test de contenido pasa**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.crosswalker.game.SimulationTest"`
Expected: PASS. Si `allLevelsHaveASafeSolutionWithTheirAvailableTools` falla para algún nivel específico, el mensaje indica cuál (`Level N (título)`); ajusta el `offset` de ese nivel en `campaign.json` (súmale o réstale entre 0.1 y 0.3) y vuelve a correr el test hasta que pase — los números de arriba son el punto de partida, no son sagrados.

- [ ] **Step 7: Correr toda la suite JVM**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/assets/campaign.json app/src/main/java/com/example/crosswalker/CraftArt.kt app/src/androidTest/java/com/example/crosswalker/NavigationTransitionsTest.kt app/src/test/java/com/example/crosswalker/SimulationTest.kt
git commit -m "feat: contenido de los 12 niveles con fichas y mapas progresivos"
```

---

### Task 4: Tutorial del nivel 1 (cola de diálogo + mensajes reactivos)

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/Dialogue.kt`
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt`
- Test: `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt`

**Interfaces:**
- Consumes: `Level.tutorial/introLines/lesson/readyMessage` (Task 1/3), `DialogueBox` (existente).
- Produces: `DialogueBox(..., onAdvance: (() -> Unit)? = null)`; `Session.introIndex: Int`.

- [ ] **Step 1: Escribir el test instrumentado que falla**

Crea `app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt`:

```kotlin
package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TutorialTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    @Test fun tutorialQueueAdvancesByTappingTheBoxThenBecomesReactive() {
        ui.runOnIdle { session.navigate("map") }
        ui.waitForIdle()
        ui.onNodeWithText("Mi primer cruce", substring = true).performClick()
        ui.waitForIdle()
        ui.onNodeWithText("PREPARAR EL PLAN", substring = true).performClick()
        ui.waitForIdle()

        val level = session.level
        assertTrue("el nivel 1 debe tener introLines", level.introLines.isNotEmpty())
        ui.onNodeWithContentDescription(level.introLines[0]).assertIsDisplayed()
        level.introLines.forEach { line ->
            ui.onNodeWithContentDescription(line).performClick()
            ui.waitForIdle()
        }
        ui.onNodeWithContentDescription(level.lesson).assertIsDisplayed()
        ui.onNodeWithText("MIRAR", substring = true).performClick()
        ui.waitForIdle()
        ui.onNodeWithContentDescription(level.readyMessage).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.TutorialTest"`
Expected: FAIL (hoy el mensaje mostrado es siempre `level.lesson` desde el inicio; no hay cola de `introLines` ni avance al tocar la caja).

- [ ] **Step 3: `DialogueBox` gana `onAdvance`**

En `app/src/main/java/com/example/crosswalker/Dialogue.kt`, en la firma de `DialogueBox`, añade el parámetro:

```kotlin
@Composable fun DialogueBox(
    text: String,
    speaker: String?,
    speakerColor: Accent,
    lexicon: Lexicon,
    modifier: Modifier = Modifier,
    blip: () -> Unit = {},
    onAdvance: (() -> Unit)? = null
) {
```

Y cambia el `.clickable(...)` que hoy dice:

```kotlin
                .clickable(interactionSource = null, indication = null) { revealed = text.length }
```

por:

```kotlin
                .clickable(interactionSource = null, indication = null) {
                    if (done && onAdvance != null) onAdvance() else revealed = text.length
                }
```

- [ ] **Step 4: `Session` gana `introIndex` y deja de pisar el mensaje en el tutorial**

En `app/src/main/java/com/example/crosswalker/MainActivity.kt`, en la clase `Session`:

Añade junto a las demás propiedades `by mutableStateOf`/`mutableIntStateOf`:

```kotlin
    var introIndex by mutableIntStateOf(0)
```

En `reset()`, añade `introIndex=0` a la lista de asignaciones (junto a `selected=...; simulation=null; ...`).

En `toggle(t: Tool)`, cambia la última línea:

```kotlin
        plan=next; message=t.description
```

por:

```kotlin
        plan=next; if(!level.tutorial) message=t.description
```

- [ ] **Step 5: `Game` calcula el mensaje reactivo y lo pasa a `DialogueBox`**

En `app/src/main/java/com/example/crosswalker/MainActivity.kt`, dentro de `fun Game(s:Session,click:()->Unit)`, justo antes de la línea que hoy invoca `DialogueBox`, añade:

```kotlin
            val tutorialIntroDone = !s.level.tutorial || s.introIndex >= s.level.introLines.size
            val displayMessage = when {
                s.level.tutorial && !tutorialIntroDone -> s.level.introLines[s.introIndex]
                s.level.tutorial && "look" !in s.plan.people.getValue(s.selected).skills -> s.level.lesson
                s.level.tutorial -> s.level.readyMessage
                else -> s.message
            }
            val onAdvance: (() -> Unit)? = if (s.level.tutorial && !tutorialIntroDone) ({ s.introIndex++ }) else null
```

Y cambia la llamada existente:

```kotlin
            if(s.repo.guidance) DialogueBox(s.message,selectedWalker.name,walkerAccent(selectedWalker.sprite),
                remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
                Modifier.heightIn(min=56.dp),blip=blip(selectedWalker.id))
```

por:

```kotlin
            if(s.repo.guidance) DialogueBox(displayMessage,selectedWalker.name,walkerAccent(selectedWalker.sprite),
                remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
                Modifier.heightIn(min=56.dp),blip=blip(selectedWalker.id),onAdvance=onAdvance)
```

- [ ] **Step 6: Verificar que el test pasa**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.TutorialTest"`
Expected: PASS.

- [ ] **Step 7: Correr la suite instrumentada completa**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS (incluye `NavigationTransitionsTest`, ya actualizado en Task 3).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/Dialogue.kt app/src/main/java/com/example/crosswalker/MainActivity.kt app/src/androidTest/java/com/example/crosswalker/TutorialTest.kt
git commit -m "feat: tutorial del nivel 1 con dialogo paso a paso"
```

---

### Task 5: Panel de fichas filtrado por nivel + tarjeta "FICHA NUEVA"

**Files:**
- Modify: `app/src/main/java/com/example/crosswalker/MainActivity.kt`
- Test: `app/src/androidTest/java/com/example/crosswalker/LevelContentTest.kt`

**Interfaces:**
- Consumes: `Level.tools/newTools` (Task 1/3), `ToolArtwork` (existente, Task 3 le añadió `phone_away`).

- [ ] **Step 1: Escribir el test instrumentado que falla**

Crea `app/src/androidTest/java/com/example/crosswalker/LevelContentTest.kt`:

```kotlin
package com.example.crosswalker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class LevelContentTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val session get() = ViewModelProvider(ui.activity)[Session::class.java]

    @Test fun levelDialogShowsNewToolCard() {
        ui.runOnIdle { session.navigate("map") }
        ui.waitForIdle()
        ui.onNodeWithText("Camino a la escuela", substring = true).performClick()
        ui.waitForIdle()
        ui.onNodeWithText("FICHA NUEVA", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Zona escolar", substring = true).assertIsDisplayed()
    }

    @Test fun toolPaletteOnlyShowsFichasAvailableForThisLevel() {
        ui.runOnIdle { session.navigate("map") }
        ui.waitForIdle()
        ui.onNodeWithText("Mi primer cruce", substring = true).performClick()
        ui.waitForIdle()
        ui.onNodeWithText("PREPARAR EL PLAN", substring = true).performClick()
        ui.waitForIdle()
        ui.onNodeWithText("MIRAR", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ESPERAR", substring = true).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.LevelContentTest"`
Expected: FAIL (hoy el diálogo de nivel no muestra "FICHA NUEVA", y el panel de fichas muestra las 10, no solo las del nivel).

- [ ] **Step 3: Filtrar el panel de fichas por `level.tools`**

En `app/src/main/java/com/example/crosswalker/MainActivity.kt`, dentro de `Game`, cambia:

```kotlin
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.tools.filter {it.category==category}.forEach { t ->
```

por:

```kotlin
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.tools.filter {it.category==category && it.id in s.level.tools}.forEach { t ->
```

- [ ] **Step 4: Añadir la tarjeta "FICHA NUEVA" al diálogo de nivel**

En `WorldMap`, dentro de `chosen?.let { l -> PaperDialog(...) { ... } }`, justo después de la línea:

```kotlin
        Text("★ Llega al otro lado\n★★ Resuelve sin ayuda de emergencia\n★★★ Usa pocas fichas",style=BodyText,color=Ink)
```

añade:

```kotlin
        if (l.newTools.isNotEmpty()) {
            Text("FICHA NUEVA", style = HudStrong, color = Orange.dark)
            l.newTools.forEach { id ->
                val t = s.repo.tools.first { it.id == id }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolArtwork(t.id)
                    Column { Text(t.label, style = BodyStrong, color = Ink); Text(t.description, style = BodyText, color = Ink) }
                }
            }
        }
```

- [ ] **Step 5: Verificar que el test pasa**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.crosswalker.LevelContentTest"`
Expected: PASS.

- [ ] **Step 6: Correr la suite instrumentada completa**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS (`NavigationTransitionsTest`, `TutorialTest`, `LevelContentTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/crosswalker/MainActivity.kt app/src/androidTest/java/com/example/crosswalker/LevelContentTest.kt
git commit -m "feat: fichas por nivel y tarjeta de ficha nueva al elegir nivel"
```

---

### Task 6: Verificación manual en el emulador

**Files:** ninguno (solo verificación; no se espera código nuevo salvo arreglos puntuales si algo se ve mal).

- [x] **Step 1: Compilar e instalar**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, instalado en el emulador/dispositivo conectado.

- [x] **Step 2: Lanzar la app y jugar el tutorial completo**

Abrir la app, tocar JUGAR, tocar "Mi primer cruce", tocar "PREPARAR EL PLAN", tocar la caja de diálogo 6 veces (una por línea de `introLines`), confirmar que el mensaje cambia a la línea de `lesson`, tocar MIRAR, confirmar que cambia a `readyMessage`, tocar "PONER EL PLAN EN MARCHA" y confirmar que Ana cruza.

- [x] **Step 3: Revisar 2-3 niveles más**

Entrar al nivel 4 ("Camino a la escuela") desde el mapa: confirmar que el diálogo previo muestra "FICHA NUEVA" con Zona escolar y Paso cebra. Entrar al nivel 8 ("Un compañero especial"): confirmar que Mateo se mueve muy lento sin Paso cebra y a velocidad normal con él. Entrar al nivel 10 ("La parada del camión"): confirmar que Tomás no reacciona a tiempo sin "Guarda el celular".

- [x] **Step 4: Si algo se ve mal (fichas con ícono incorrecto, carriles que se ven encimados, un nivel imposible de resolver a ojo), anotarlo y ajustar el dato puntual en `campaign.json` o `CraftArt.kt`, luego repetir desde el Step 1.**

- [x] **Step 5: Commit final (si hubo ajustes en este paso)**

```bash
git add -A
git commit -m "fix: ajustes de playtest en campaign.json"
```
