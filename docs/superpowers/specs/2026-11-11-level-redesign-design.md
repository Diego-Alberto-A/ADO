# Rediseño de los 12 niveles — diseño

## Problema

Los 12 niveles actuales (`app/src/main/assets/campaign.json`) son casi idénticos:
siempre 3 carriles en las mismas posiciones, los mismos 3 autos, y las 9 fichas
(`tools`) están **todas disponibles desde el nivel 1** sin ningún desbloqueo
progresivo. Solo escalan linealmente velocidad de los autos y presupuesto. El
resultado: niveles aburridos, sin curva de aprendizaje, y ninguna explicación
de qué hace cada ficha más allá de un texto que aparece al tocarla.

## Objetivo

Rediseñar los 12 niveles para que:

- El nivel 1 sea un tutorial real, estilo Nintendo, que enseña a jugar.
- El juego sea incremental: cada nivel decide qué fichas tienen sentido en su
  entorno, sin que todas estén disponibles desde el principio.
- Cada nivel presente un mapa distinto (carriles, tráfico, elenco de
  peatones), no solo un reescalado del anterior.
- Al tocar un nivel en el mapa, se muestre la ficha nueva que introduce (si
  la hay) con su ícono y una explicación breve, antes de entrar a jugar.
- Se mantengan los 3 mundos existentes y, explícitamente, **la interfaz visual
  actual no cambia** — ni pantallas nuevas, ni estilos nuevos. Todo el trabajo
  es de contenido y de un modelo de datos más expresivo.

## No-objetivos

- No se rediseña `Board.kt` para soportar número variable de puntos de cruce
  (hoy fijos en Izquierda/Centro/Derecha, `Simulation.crossings`). Cambiar eso
  tocaría la UI de selección de cruce, que se pidió no tocar. Queda anotado
  como posible mejora futura.
- No se genera arte nuevo. Los personajes nuevos reutilizan los 3 sprites de
  peatón existentes (`walker_coral`, `walker_blue`, `walker_green`); se
  distinguen por nombre, diálogo y mecánica, no visualmente.
- No se construye un sistema de desbloqueo global persistente. Qué fichas
  están disponibles lo decide cada nivel (su entorno), no un progreso
  acumulado guardado en `SharedPreferences`.

## Arquitectura

**Enfoque elegido: extensión mínima del modelo de datos existente.**
Se evaluaron y descartaron dos alternativas:

- *Desbloqueo global persistente* (flags "ya aprendiste X para siempre" en
  `GameRepository`): no sirve porque el pedido explícito es que el **nivel**
  decida qué fichas tienen sentido ahí, no un progreso acumulado fijo.
- *Tablero con número variable de cruces/obstáculos*: daría aún más variedad
  de mapa, pero exige tocar `Simulation.crossings` (constante) y los chips
  "Izq./Centro/Der." de la UI de juego — es decir, sí cambiaría la interfaz.

El enfoque elegido no toca `Board.kt` en absoluto: ya es 100% data-driven
sobre `level.lanes` (número de carriles, velocidad, sentido, auto). La
variedad de mapa se logra solo con datos: 1 a 4 carriles, distintas
velocidades/sentidos, día/noche y elenco de peatones.

## Cambios al modelo de datos

`Level` (en `Simulation.kt`) gana 5 campos, todos con default seguro para no
romper compatibilidad:

```kotlin
data class Level(
    val id: Int, val world: Int, val title: String, val lesson: String,
    val budget: Int, val night: Boolean, val lanes: List<Lane>, val walkers: List<Walker>,
    val tools: List<String>,                 // ids de fichas disponibles en este nivel
    val newTools: List<String> = emptyList(), // subconjunto de `tools` que se introduce aquí
    val tutorial: Boolean = false,            // true solo en el nivel 1
    val introLines: List<String> = emptyList(), // cola de diálogo inicial (solo tutorial)
    val readyMessage: String = ""             // mensaje reactivo "ya puedes lanzar el plan" (solo tutorial)
)
```

`campaign.json`: cada nivel gana `"tools"` (array de ids) y, cuando aplique,
`"newTools"`; el nivel 1 además gana `"tutorial": true`, `"introLines"` y
`"readyMessage"`. `GameRepository.kt` parsea los campos nuevos con los
defaults de arriba cuando falten (no debería faltar ninguno, pero los
defaults evitan romper si algún nivel queda incompleto).

`campaign.json` también gana una ficha nueva en `tools` (ver abajo).

### Dónde se usa `tools`/`newTools`

- `Game` (en `MainActivity.kt`): el panel de fichas filtra
  `s.repo.tools.filter { it.category==category && it.id in s.level.tools }`
  en vez de mostrar siempre las 9. Las dos pestañas HABILIDADES/LA CALLE
  siguen siempre visibles (no se oculta ninguna), aunque una quede vacía en
  algún nivel — así la interfaz no cambia de forma, solo de contenido.
- El diálogo de nivel que ya existe al tocar una tarjeta en `WorldMap`
  (`chosen?.let { PaperDialog("NIVEL ${l.id}", ...) }`) gana, después del
  texto de estrellas, una sección condicional:

  ```kotlin
  if (l.newTools.isNotEmpty()) {
      Text("FICHA NUEVA", style = HudStrong, color = Orange.dark)
      l.newTools.forEach { id ->
          val t = s.repo.tools.first { it.id == id }
          Row(verticalAlignment = Alignment.CenterVertically) {
              ToolArtwork(t.id)
              Column { Text(t.label, style = BodyStrong); Text(t.description, style = BodyText) }
          }
      }
  }
  ```

  Reutiliza `ToolArtwork` y `PaperDialog` tal cual existen — ninguna pantalla
  nueva, ningún componente visual nuevo.

## Fichas: auditoría y cambios

Se revisaron las 9 fichas actuales contra "¿le enseña algo distinto a un
niño?":

- **Mirar, Esperar, Contacto, Reflector, Semáforo** — mecánicas sólidas y
  distintas entre sí. Sin cambios.
- **Zona escolar** y **Reductor** son mecánicamente casi idénticas (ambas
  solo multiplican la velocidad del tráfico). No se fusionan: en la vida real
  son señales distintas en contextos distintos. Se usan en **mapas
  distintos**, nunca juntas en el mismo nivel, para que cada una tenga su
  propio momento en vez de sentirse redundante.
- **Paso cebra** era la ficha más débil (solo +20% velocidad). Gana una
  dependencia real: el peatón nuevo en silla de ruedas solo cruza a
  velocidad normal si Paso cebra está activo.

### Ficha nueva: "Guarda el celular" (`phone_away`)

```json
{
  "id": "phone_away",
  "label": "Guarda el celular",
  "icon": "✕",
  "cost": 1,
  "description": "Guarda el teléfono antes de cruzar para prestar atención a la calle.",
  "category": "skill"
}
```

`CraftArt.kt` → `ToolArtwork`: nueva rama `"phone_away" -> R.drawable.icon_wait`
(reutiliza el ícono de "esperar" como marcador visual más cercano; si más
adelante se genera arte nuevo con `tools/package_kenney_assets.py`, se puede
reemplazar sin tocar lógica).

### Peatones nuevos (kinds)

Dos arquetipos nuevos en `Simulation.kt`, 4 líneas:

```kotlin
// en el cálculo de velocity dentro de tick():
if (p.walker.kind == "wheelchair" && "crosswalk" !in plan.street) velocity *= .5f
if (p.walker.kind == "phone" && "phone_away" !in skills) velocity *= .5f
// "mirar" no protege a quien sigue en el celular:
val looking = "look" in skills && (p.walker.kind != "phone" || "phone_away" in skills)
// ... y se usa `looking` en vez de `"look" in skills` en el bloque de detección de huecos
```

- **`wheelchair`** (Mundo 2, "Mateo") — necesita Paso cebra para cruzar a
  velocidad normal. Enseña accesibilidad: el entorno importa tanto como la
  decisión del peatón.
- **`phone`** (Mundo 3, "Tomás") — Mirar no sirve de nada si no se suelta la
  pantalla primero. Idea del usuario; lección muy actual.

No se toca ninguna prueba existente: ningún test usa los kinds nuevos, y los
tres kinds existentes (`impulsive`, `distracted`, `slow`) quedan intactos.

## Nivel 1: tutorial estilo Nintendo

Inspirado explícitamente en el `DialogueBox` ya existente (caja con
typewriter, placa de nombre y voces — "estilo Animal Crossing/Pokémon"). Se
extiende ese mismo componente, no se crea uno nuevo:

`DialogueBox` gana un parámetro opcional `onAdvance: (() -> Unit)? = null`:
cuando el texto ya está totalmente revelado (`done == true`) y se vuelve a
tocar la caja, si `onAdvance` no es null, se llama en vez de no hacer nada.
En todos los demás niveles `onAdvance` queda `null` → comportamiento idéntico
al de hoy.

`Session` gana `introIndex: Int` (reiniciado a `0` en `reset()`).

En `Game`, cuando `s.level.tutorial`:

- Mientras `introIndex < level.introLines.size`: el mensaje mostrado es
  `level.introLines[introIndex]`, con `onAdvance = { s.introIndex++ }` — el
  jugador toca la caja para avanzar, como un diálogo clásico.
- Una vez agotada la cola, el mensaje se vuelve reactivo:
  - si "mirar" aún no está en las habilidades del peatón → `level.lesson`
    ("Ahora toca HABILIDADES y elige la ficha MIRAR...").
  - si ya está, y el plan no se ha lanzado → `level.readyMessage`
    ("¡Muy bien! Toca PONER EL PLAN EN MARCHA...").

`Session.toggle()` deja de sobrescribir `message` con la descripción de la
ficha tocada cuando `level.tutorial` es `true` (para no pisar el mensaje
reactivo); en el resto de los niveles el comportamiento actual no cambia.

### Guion exacto del nivel 1

Cola inicial (`introLines`, se avanza tocando la caja):

1. "¡Hola! Soy Ana."
2. "Hoy aprenderás a cruzar la calle sin peligro. Vamos paso a paso."
3. "Arriba a la izquierda está PAUSA (Ⅱ), por si quieres detenerte un
   momento."
4. "Arriba a la derecha están tus FICHAS ●: cada decisión cuesta algunas,
   ¡hoy tienes de sobra!"
5. "Hay dos pestañas: HABILIDADES son para mí, y LA CALLE protege todo el
   camino. Hoy usamos HABILIDADES."
6. "Elige por dónde cruzo: Izquierda, Centro o Derecha. Te recomiendo el
   Centro."

Reactivos:

7. `lesson`: "Ahora toca HABILIDADES y elige la ficha MIRAR, para que sepa
   si viene un coche."
8. `readyMessage`: "¡Muy bien! Toca PONER EL PLAN EN MARCHA para
   intentarlo."

Mapa del nivel 1: 1 solo carril, un auto lento, un solo peatón (Ana),
presupuesto generoso (el costo de Mirar es irrelevante frente al
presupuesto) para que la única decisión real sea "¿uso Mirar o no?". Única
ficha disponible: Mirar. Pestaña LA CALLE vacía (se nombra en la línea 5
como algo que llegará después, sin dar fichas todavía).

## Los 12 niveles

Principio rector: **concepto nuevo → mapa fácil; mapa difícil → sin concepto
nuevo.** La dificultad del mapa y la novedad de la ficha nunca suben juntas.
No todos los niveles introducen una ficha nueva — varios enseñan aplicando
lo ya visto en un contexto distinto.

### Mundo 1 — Mi pequeño barrio (día)

| # | Título | Mapa | Peatones | Fichas disponibles | Ficha nueva | Enseña |
|---|---|---|---|---|---|---|
| 1 | Mi primer cruce | 1 carril, auto lento | Ana | Mirar | Mirar | Tutorial: un peatón, una ficha, un botón |
| 2 | Para y mira | 2 carriles, mismo sentido | Ana, Leo | Mirar, Esperar | Esperar | La paciencia evita las prisas de Ana |
| 3 | Cada quien su paso | 2 carriles, sentidos opuestos | +Luz | Mirar, Esperar, Contacto | Contacto | Mirar al conductor; Luz necesita más tiempo |
| 4 | Camino a la escuela | 3 carriles, más tráfico | Ana, Leo, Luz | +Zona escolar, Paso cebra | Zona escolar, Paso cebra | El entorno (una escuela) también protege |

### Mundo 2 — Campus de innovación (día)

| # | Título | Mapa | Peatones | Fichas disponibles | Ficha nueva | Enseña |
|---|---|---|---|---|---|---|
| 5 | La gran avenida | 4 carriles (la más difícil hasta ahora) | Ana, Leo, Luz | Mirar, Esperar, Contacto, Paso cebra | — | Una avenida grande exige planear mejor con lo ya sabido |
| 6 | El cruce con semáforo | 2-3 carriles, cruce central señalizado | Ana, Leo | +Semáforo | Semáforo | Solo funciona cruzando por el Centro |
| 7 | El agente de tránsito | 3 carriles, intersección concurrida | Ana, Leo, Luz | +Agente | Agente | Cruzar todos juntos en la ventana que abre el agente |
| 8 | Un compañero especial | 3 carriles, cruce marcado | +Mateo (silla de ruedas) | Mirar, Esperar, Contacto, Paso cebra, Semáforo | — | Inclusión: el paso cebra deja de ser opcional |

### Mundo 3 — La ciudad de noche

| # | Título | Mapa | Peatones | Fichas disponibles | Ficha nueva | Enseña |
|---|---|---|---|---|---|---|
| 9 | Luces al anochecer | 1-2 carriles (espejo del nivel 1, de noche) | Ana | Mirar, Reflector | Reflector | De noche cambia la ficha, no la situación |
| 10 | La parada del camión | 2-3 carriles | +Tomás (celular) | Mirar, Esperar, Reflector, Guarda el celular | Guarda el celular | Mirar no sirve si no sueltas el teléfono |
| 11 | Juntos de noche | 3 carriles | Ana, Leo, Luz | Mirar, Esperar, Reflector, Agente, Paso cebra | — | Cuidar a quien camina más despacio, de noche |
| 12 | Una ciudad segura | 4 carriles (la más difícil del juego) | Todo el elenco | Todas las fichas aprendidas | — | Examen final: aplicar todo |

Presupuestos y valores exactos de carriles (`y`/`speed`/`offset`/`car`) se
calibran en la implementación, siguiendo la progresión de dificultad de
esta tabla; los valores actuales de `campaign.json` sirven de referencia de
escala.

## Impacto conocido en pruebas existentes

- `NavigationTransitionsTest.kt` busca el texto `"Primeros pasos"` (título
  actual del nivel 1). Al renombrarlo a "Mi primer cruce", ese test debe
  actualizarse. Se incluye como tarea del plan de implementación.
- `SimulationTest.campaignArchetypesHaveSafeSolutions` itera los 12 niveles
  asumiendo un patrón de carriles fijo; con mapas variables por nivel, ese
  test necesita generarse a partir de los niveles reales de
  `GameRepository`/`campaign.json` en vez de sintetizarlos. Se incluye como
  tarea del plan.

## Supuestos

- Los nombres de los mundos y su orden no cambian.
- El elenco de peatones nuevos (Mateo, Tomás) reutiliza sprites existentes;
  no se genera arte nuevo en esta fase.
- Los valores numéricos exactos de presupuesto/velocidad de cada nivel se
  afinan durante la implementación y el playtesting, no en este documento.
