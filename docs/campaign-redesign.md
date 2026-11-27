# Auditoría y rediseño de campaña

Cambios aplicados en `new version/`. El nivel 1 conserva datos, diálogo, fichas disponibles y simulación anteriores. Los niveles 2–12 tenían el mismo patrón de tráfico periódico con más carriles/peatones; se rediseñan los once.

## Hallazgos

- Contacto y Reflector ignoraban las colisiones por una bandera, incluso cuando físicamente un coche llegaba al peatón. Se elimina toda inmunidad.
- Semáforo congelaba toda la calle indefinidamente por la mera presencia de un peatón en el Centro. Ahora es infraestructura compartida con fases y frenado.
- Zona escolar y Reductor multiplicaban reducciones equivalentes. Se fusionan en Zona lenta, sin acumulación.
- Esperar tres segundos premiaba memorizar una fase del tráfico, no mirar: se elimina.
- Agente detenía instantáneamente seis segundos y promovía correr. Se elimina; la ayuda de emergencia tampoco detiene instantáneamente ni vuelve invulnerable.
- Paso cebra aceleraba incluso a quienes no necesitaban adaptación. Se reemplaza por Paso accesible: rampas y señalización, sin acelerar personas.

## Fichas finales

Las habilidades se pagan por peatón; las mejoras de calle una vez por plan. Los costos del tutorial no cambian.

| Ficha | Tipo / costo | Efecto | Mejor situación | Limitación o mala elección | Lección |
|---|---|---|---|---|---|
| Mirar | Personaje / 1 | Comprueba todos los carriles y espera un hueco para completar el tramo. No sirve mientras miras el celular ni si la vista está bloqueada. | Tráfico con huecos y vista despejada. | Con celular activo o vista insuficiente no permite un cruce seguro. | Comprobar todos los carriles y disponer de tiempo para terminar el tramo. |
| Confirmar alto | Personaje / 1 | Espera a que TODOS los coches estén detenidos antes del cruce. Útil con un paso marcado o semáforo; no pide paso ni obliga a frenar. Sin ellos puede dejarte esperando. | Paso marcado o fase peatonal donde todos los coches pueden detenerse. | Sin un motivo para que los conductores cedan, espera; no los obliga a parar. | Confirmar un alto real; ver al conductor no garantiza que pueda frenar. |
| Reflector | Personaje / 1 | De noche, permite que los conductores te detecten antes. No ilumina tu vista ni obliga a frenar. De día no aporta ventaja. | Noche, con paso marcado o semáforo y comprobación del alto. | De día consume presupuesto sin cambiar la detección. No ilumina tu vista. | Ser visto y ver son problemas diferentes. |
| Guardar celular | Personaje / 1 | Quita la distracción de Tomás y recupera su paso normal. No comprueba el tráfico. No cambia nada en quien no lleva celular. | Tomás lleva el celular. | En otro peatón no cambia nada; por sí sola no comprueba coches. | Retirar la distracción antes de mirar. |
| Zona lenta | Calle / 3 | Reduce la velocidad de circulación al 55 %. Da más tiempo y reduce la distancia de frenado; no detiene los coches ni elimina los choques. | Lluvia, vehículos rápidos o peatones lentos. | Puede consumir el presupuesto necesario para una rampa o atención; no detiene coches. | Menos velocidad reduce distancia de frenado y aumenta el margen. |
| Paso accesible | Calle / 2 | Paso marcado con rampas en los cruces indicados. Permite cruzar a Mateo y avisa a conductores que puedan verte. No acelera peatones ni garantiza que frenen. | Mateo necesita una rampa; permite ceder en cruces marcados. | Fuera de los cruces accesibles indicados no resuelve el bordillo. No otorga prioridad física absoluta. | Accesibilidad real y comprobación del tráfico, no solo pintura. |
| Fase peatonal | Calle / 3 | En el Centro: rojo vehicular desde el inicio, paso peatonal del segundo 3 al 16 de cada ciclo de 22 s. Espera luz y tiempo suficiente; confirma el alto. No actúa en cruces laterales. | Centro con varios carriles y suficiente tiempo de fase peatonal. | No controla los laterales; puede obligar a esperar otro ciclo si el tiempo no alcanza. | Esperar la fase adecuada y comprobar el alto antes de entrar. |
| Iluminar cruce | Calle / 2 | Ilumina el Centro de noche para ver y ser visto antes. No elimina niebla ni obstáculos. De día o en cruces laterales no ayuda. | Centro de noche. | No despeja coches estacionados, no elimina niebla y de día no ayuda. | Elegir un lugar iluminado y con buena visibilidad. |
| Isla de refugio | Calle / 3 | Solo en niveles con espacio para isla y solo en el Centro: divide el recorrido en dos tramos y permite esperar protegido a mitad. No reduce velocidad ni sustituye Mirar o Confirmar alto. | Avenida con espacio marcado para isla, especialmente con Luz. | No existe fuera del Centro ni en niveles sin espacio. Sin observación no garantiza nada. | Dividir un recorrido largo solo donde existe un lugar protegido para esperar. |

## Campaña y disponibilidad exacta

Los distractores son opciones de utilidad contextual, nunca consejos falsos de seguridad. La pantalla explica sus limitaciones. Las mejoras no disponibles no aparecen en la paleta.

| Nivel | Problema y motivo del cambio | Presupuesto | Fichas disponibles |
|---|---|---|---|
| 2. Un mensaje puede esperar | Tomás necesita guardar el celular y comprobar el tráfico. Un reflector no ayuda de día. | 3 | Mirar, Guardar celular, Reflector, Confirmar alto |
| 3. Un cruce para Mateo | El paso accesible tiene rampas a la Izquierda y en el Centro. Una rampa no sustituye mirar. | 4 | Mirar, Confirmar alto, Paso accesible, Reflector |
| 4. Ver entre los coches | La Izquierda tiene la vista bloqueada. Luz necesita más tiempo: un cruce despejado y menos velocidad ayudan. | 4 | Mirar, Confirmar alto, Zona lenta, Iluminar cruce |
| 5. Dos sentidos, dos comprobaciones | Un coche que cede en un carril no detiene al otro. Prepara a Tomás y a Ana por separado. | 6 | Mirar, Confirmar alto, Guardar celular, Zona lenta, Paso accesible |
| 6. El verde también termina | El semáforo solo protege el Centro y tiene un tiempo limitado. Mira o confirma que los vehículos pararon. | 8 | Mirar, Confirmar alto, Fase peatonal, Paso accesible, Reflector |
| 7. La lluvia alarga el frenado | Piso mojado: los coches tardan más en detenerse. Luz camina despacio; combina visibilidad del cruce y velocidad segura. | 7 | Mirar, Confirmar alto, Zona lenta, Paso accesible, Iluminar cruce |
| 8. Una pausa protegida | La isla del Centro divide la avenida en dos tramos. Mateo necesita además una rampa; esperar en un carril no es un refugio. | 11 | Mirar, Confirmar alto, Paso accesible, Isla de refugio, Zona lenta, Fase peatonal |
| 9. Ver y ser visto | De noche, reflector e iluminación hacen cosas distintas. Busca un hueco o confirma el alto; no confíes solo en que te vean. | 5 | Mirar, Confirmar alto, Reflector, Iluminar cruce, Zona lenta, Paso accesible |
| 10. La parada oculta el tráfico | El Centro está tapado por la parada. Busca los laterales despejados, guarda el celular y prepara la visibilidad nocturna. | 8 | Mirar, Confirmar alto, Guardar celular, Reflector, Iluminar cruce, Zona lenta, Paso accesible |
| 11. Niebla y pasos cortos | La niebla no desaparece con una lámpara. La isla permite esperar a salvo; reduce la velocidad y prepara a Luz. | 10 | Mirar, Confirmar alto, Reflector, Iluminar cruce, Zona lenta, Isla de refugio, Fase peatonal |
| 12. La ciudad de todos | Lluvia y noche: planifica la rampa de Mateo, la atención de Tomás y el tiempo de Luz. No hay una ficha que lo resuelva todo. | 13 | Mirar, Confirmar alto, Guardar celular, Reflector, Iluminar cruce, Zona lenta, Paso accesible, Isla de refugio, Fase peatonal |

## Mecánicas y resultados comprensibles

- El cruce lateral/central afecta la visibilidad, la disponibilidad de rampas y el funcionamiento de lámpara, señal o refugio. Se indican los cruces bloqueados antes de empezar y en el tablero.
- La lluvia reduce el paso al 85 % y la desaceleración de coches de 0,12 a 0,055 unidades de tablero/s². La noche reduce el alcance visual; la niebla lo reduce adicionalmente. Son parámetros educativos, no medidas calibradas de una calle real.
- Mirar calcula un hueco para **todo el tramo**, sin dejar al peatón parado en un carril. Solo una isla habilitada permite dividir la comprobación en dos.
- Los coches reaccionan durante 0,65 s antes de frenar. La distancia necesaria aumenta con la velocidad mediante `v * reacción + v² / (2 * desaceleración)`. Detectar tarde no garantiza detenerse. No se usa prioridad, reflector o contacto como inmunidad.
- El peatón espera el alto **de todos los carriles**, delante de su punto de paso. La fase peatonal es de los segundos 3–16 en un ciclo de 22 s; el rojo comienza antes para permitir frenar. Cada nuevo tramo necesita tiempo suficiente para terminar.
- Movimiento subdividido en pasos de hasta 20 ms y colisiones por intervalos de rectángulos barridos. Un coche que reaparece por el borde no atraviesa artificialmente todo el tablero.
- Todo contacto real provoca reintento. El mensaje distingue contacto a baja velocidad y coche aún rápido, explica la causa y propone una corrección. No se simulan lesiones ni se enseña que un impacto lento sea inocuo.
- **Objetivo explícito de seguridad:** llegar por suerte sin comprobar el tráfico o mirando el teléfono no cuenta como plan seguro. Puede haber contacto antes, o una evaluación sin choque al terminar. Se explica antes de iniciar; no se inventa un atropello para penalizarlo.
- Si no existen condiciones seguras, el personaje espera y el reintento explica por qué. No se fuerza a salir a una calle insegura por el temporizador.
- Cada nivel tiene una meta explícita de costo para tres estrellas, con al menos una solución segura verificada que la cumple. Se conservan estrellas y desbloqueos previos. No se borra el progreso del usuario al cambiar la campaña.

## Verificación

Las pruebas JVM buscan planes dentro del presupuesto, con solo fichas disponibles, y verifican al menos dos soluciones por nivel. Auditan todos los tipos de ficha individuales en los tres cruces de cada nivel 2–12: ninguno gana solo. Comprueban además planes vacíos fallidos, determinismo, instantánea del plan, teléfono, rampas, lluvia, detección nocturna, frenado gradual, emergencia de un uso, pasos de tiempo grandes y preservación del tutorial.

Las alternativas pueden diferir en cruce, tipo de observación o infraestructura; no se afirma que toda ficha de toda solución sea indispensable. El informe de soluciones se genera en `app/build/reports/campaign-balance.txt`.

## Referencias de la lección

- NHTSA: https://www.nhtsa.gov/road-safety/pedestrian-safety
- Utah Highway Safety Office (mitos sobre visibilidad y frenado): https://highwaysafety.utah.gov/pedestrian-myths/

Los consejos del juego acompañan la responsabilidad de los conductores y el diseño seguro de la vía. No se presenta a un niño como responsable de la conducta peligrosa de un conductor.

Verificación ejecutada: compilación Debug y pruebas JVM correctas; tres pruebas instrumentadas de disponibilidad de fichas y avance/música tras victoria correctas en Pixel 10 Pro API 37. No se afirma calibración física contra datos de vehículos reales.

Se revisó además una captura del nivel 12 y pasó una prueba instrumentada adicional que recorre los niveles 2–12 y abre la ayuda de las fichas. Total: 24 pruebas JVM y cuatro pruebas instrumentadas distintas correctas. Ejemplos de soluciones verificadas: campaign-solutions.txt.
