# Rediseño de fichas, mecánicas e intuitividad (niveles 4-12)

## Problema

El campaign tiene 3 mundos × 4 niveles (ya coincide con lo pedido: no hay que recortar
niveles). El problema es de contenido:

- De las 9 fichas totales, 8 ya se introdujeron para el nivel 4. Del nivel 5 al 12 solo se
  introducen 2 fichas nuevas (`signal` en el 6, `refuge` en el 8). **El Mundo 2 completo
  (niveles 9-12) no introduce nada nuevo** — solo repite las mismas 9 fichas con variaciones
  de clima/noche.
- De los 4 "tipos" de peatón (`impulsive`, `phone`, `wheelchair`, `slow`), solo 2 (`phone`,
  `wheelchair`) tienen una mecánica real en el motor (`Simulation.kt`). `impulsive` y `slow`
  son solo una etiqueta + un número de velocidad; no hay código que los distinga de "un
  peatón normal con esa velocidad".
- El nivel 7 ofrece la ficha `lighting` en un nivel de día, donde no sirve para nada (su
  propia descripción dice "de día no aporta ventaja") — una ficha "sin sentido" filtrada en
  el diseño actual.
- El mecanismo de "vista bloqueada" (niveles 4, 10, 11, 12: un auto estático + etiqueta
  "VISTA TAPADA") se ve visualmente igual a las etiquetas de retroalimentación de fichas que
  el jugador SÍ eligió ("ZONA LENTA", "RAMPA", "LUZ"), sin ninguna señal de que es un peligro
  fijo del escenario, no una ficha. Esto lo hace poco intuitivo desde el nivel 4.

## Alcance aprobado

Libertad total en el motor (`Simulation.kt`, `Board.kt`), usando los assets existentes;
nueva arte solo si es mínima (ya hay sprites sin usar que cubren lo necesario: `walker_rust`,
`car_green`). Niveles 1-3: concepto intacto, detalle negociable. Nivel 4 en adelante: terreno
libre. No se cambia la cantidad/orden de niveles ni los IDs.

## Piezas nuevas

1. **`impulsive` deja de ser cosmético.** `sight()` aplica un multiplicador (valor inicial
   propuesto: 0.8, a calibrar con datos) a los peatones de kind `impulsive`, salvo que la
   ficha `escort` esté activa para ese peatón. Representa que un niño impulsivo juzga los
   huecos de tráfico con menos margen.
2. **Ficha nueva `escort` ("Acompañar")**, categoría `skill`, costo inicial propuesto 2
   (a calibrar). Cancela la penalización de `impulsive`. Se introduce como `newTool` en el
   nivel 5 (primera aparición de Ana fuera del tutorial/mundo 0) y queda disponible (no
   "nueva") en 6, 7, 9, 10, 11 — cualquier nivel donde Ana ya aparezca.
3. **Mecánica nueva sin ficha nueva: "punto ciego" en un carril.** Nuevo campo
   `Lane.blindSpot: Boolean = false`. En `stepVehicle`, cuando `lane.blindSpot`, la distancia
   de detección del conductor se reduce con un factor fijo (propuesto 0.5, a calibrar),
   independiente de día/noche — representa un vehículo grande cuyo conductor detecta tarde.
   Se dibuja con el sprite `car_green` (existente, sin usar en ningún nivel), escalado más
   grande que un auto normal. Se usa en el nivel 10, dándole una razón de ser distinta a "el
   nivel 4 otra vez, de noche", y hace que `visible`/`crosswalk`/`lighting` — fichas que ya
   se sienten "aprendidas y sin importancia" desde el nivel 9 — vuelvan a importar.
4. **Bug fix:** se quita `lighting` de las fichas disponibles del nivel 7 (es de día).

`slow` no se toca: su velocidad reducida ya es una mecánica real (exige huecos más grandes),
solo parecía "sin sentido" por estar junto a `impulsive`, que sí era falso.

## Rediseño de intuitividad: "vista bloqueada" (niveles 4, 10, 11, 12)

Cambio compartido en `Board.kt`, sin tocar ningún número de dificultad — es presentación,
no balance:

- **A. Orientación.** El auto estacionado se dibuja perpendicular a la calle (en batería),
  no acostado como los autos en movimiento — señal visual inmediata de "esto no es tráfico".
- **B. Etiqueta de advertencia.** Se reemplaza "VISTA TAPADA" (mismo estilo visual que las
  etiquetas de fichas elegidas por el jugador) por una tarjeta con color distinto
  (rojo/naranja, no amarillo) y texto concreto ("AUTO ESTACIONADO" + ícono de ojo tachado).
  Mismo lenguaje visual para cualquier peligro fijo del escenario (incluye el nuevo "punto
  ciego" del nivel 10).
- **C. Guía de alcance de vista en planeación.** Mientras el jugador planea (antes de darle
  play), se dibuja una línea punteada desde cada peatón hacia el carril más cercano, con el
  largo real de lo que alcanza a ver en el cruce elegido (usando `level.sight[crossing]` y
  los mismos multiplicadores de `sight()`, ya públicos/derivables desde `Board.kt`). Es la
  mejora de mayor impacto en intuitividad: el jugador VE el riesgo antes de chocar, en los 4
  niveles que usan esta mecánica.

## Cambios nivel por nivel

**Mundo 0 "Mi pequeño barrio" (1-4):** sin cambios de contenido. El nivel 4 recibe A+B+C de
arriba (presentación compartida), sin tocar su mecánica, fichas, carriles ni presupuesto.

**Mundo 1 "Campus de innovación" (5-8):**

| Nivel | Cambio |
|---|---|
| 5 | `newTools` += `escort`. Lección actualizada explicando la nueva necesidad de Ana. |
| 6 | `tools` += `escort` (ya no es "nueva"). |
| 7 | `tools` += `escort`; `tools` -= `lighting` (bug fix). |
| 8 | Sin cambios (Ana no aparece en este nivel). |

**Mundo 2 "La ciudad de noche" (9-12):**

| Nivel | Cambio |
|---|---|
| 9 | Sin cambios estructurales — solo reverificar balance (Ana presente, ahora con penalización). |
| 10 | Un carril recibe `blindSpot: true` + sprite `car_green` escalado. Lección actualizada. `tools` += `escort`. |
| 11 | Sin cambios estructurales — reverificar balance (Ana presente). `tools` += `escort`. |
| 12 | Sin cambios (Ana no aparece en este nivel). |

Todos los valores numéricos exactos (multiplicador de `sight` para `impulsive`, costo de
`escort`, factor de detección de `blindSpot`, ajustes de presupuesto por nivel) se calibran
durante la implementación corriendo la suite de balance repetidamente — no se fijan de
antemano en este documento.

## Orden de implementación

1. Visual compartido (A+B+C) — sin riesgo de balance, solo render.
2. Ficha `escort` + penalización de `impulsive` en el motor, cableado en 5, 6, 7, 9, 10, 11.
3. Carril `blindSpot` + nivel 10.
4. Bug fix de `lighting` en el nivel 7.
5. Recalibración de presupuestos/costos hasta cumplir las invariantes existentes: 2+
   soluciones por nivel, 3 estrellas alcanzable, ninguna ficha sola gana un nivel, un plan
   vacío siempre falla.

## Verificación

- Reutilizar la suite existente (`SimulationTest`) después de cada cambio de datos.
- Barridos ad-hoc temporales (como los de esta sesión) para verificar invariantes antes de
  comitear, borrándolos después de confirmar.
- **Guardia nueva permanente**: un test que detecte automáticamente una ficha en la lista de
  un nivel que no puede servir para nada ahí (p. ej. `lighting` sin `night`, `refuge` sin
  `island`) — para que este tipo de error no se filtre de nuevo.
- Sin emulador de Android disponible en este entorno: los cambios de `Board.kt` (A, B, C)
  requieren playtest visual del usuario antes de mergear.
