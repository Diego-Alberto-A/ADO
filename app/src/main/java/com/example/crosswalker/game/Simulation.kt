package com.example.crosswalker.game

import kotlin.math.abs
import org.json.JSONObject

data class Lane(val y: Float, val speed: Float, val offset: Float, val car: String)
data class Walker(val id: String, val name: String, val kind: String, val speed: Float, val sprite: String)
data class Level(
    val id: Int, val world: Int, val title: String, val lesson: String,
    val budget: Int, val night: Boolean, val lanes: List<Lane>, val walkers: List<Walker>,
    val tools: List<String> = emptyList(),      // fichas que este nivel pone a disposición
    val newTools: List<String> = emptyList(),   // las de `tools` que se presentan por primera vez aquí
    val tutorial: Boolean = false,              // el nivel 1 guía paso a paso
    val introLines: List<String> = emptyList(), // cola de diálogo inicial del tutorial
    val readyMessage: String = "",              // tutorial: "ya puedes lanzar el plan"
    val weather: String = "dry",
    val sight: List<Float> = listOf(.95f, .95f, .95f),
    val accessible: List<Int> = listOf(1),
    val island: Boolean = false,
    val parCost: Int = 0
)
data class Tool(val id: String, val label: String, val icon: String, val cost: Int, val description: String, val category: String)
fun Tool.explanation(level: Level): String = if(id == "look" && !level.tutorial)
    "Comprueba todos los carriles y espera un hueco para completar el tramo. No sirve mientras miras el celular ni si la vista está bloqueada." else description

data class Assignment(var crossing: Int = 1, val skills: MutableSet<String> = mutableSetOf())
data class Plan(val people: MutableMap<String, Assignment> = mutableMapOf(), val street: MutableSet<String> = mutableSetOf()) {
    fun cost(tools: List<Tool>) = tools.sumOf { t -> t.cost * (if (t.category == "street") (if(t.id in street) 1 else 0) else people.values.count { t.id in it.skills }) }
    fun copyPlan() = Plan(people.mapValues { Assignment(it.value.crossing, it.value.skills.toMutableSet()) }.toMutableMap(), street.toMutableSet())
}
enum class Phase { PLANNING, RUNNING, WON, RETRY }
data class PersonState(val walker: Walker, val x: Float, var y: Float = .91f, var done: Boolean = false)
class Simulation(val level: Level, source: Plan) {
    val plan = source.copyPlan()
    var phase = Phase.RUNNING; private set
    var time = 0f; private set
    var emergencyUsed = false; private set
    private var stopUntil = 0f
    val emergencyRemaining: Float get() = maxOf(0f, stopUntil - time)
    private val trafficClocks = level.lanes.associateWith { 0f }.toMutableMap()
    var reason = ""; private set
    var collapsedWalkerId: String? = null; private set
    var collapsedLane: Lane? = null; private set
    val people = level.walkers.map { PersonState(it, crossings[plan.people[it.id]?.crossing ?: 1]) }
    data class Vehicle(var x: Float, var speed: Float, var reaction: Float = 0f, var braking: Boolean = false)
    private val vehicles = level.lanes.associateWith { Vehicle(it.offset - .2f, abs(it.speed) * if("speed_zone" in plan.street && !level.tutorial) .55f else 1f) }
    var impactSpeed = 0f; private set
    var feedback = ""; private set
    fun carSpeed(lane: Lane): Float = if(level.tutorial) abs(lane.speed) else vehicles.getValue(lane).speed
    fun isBraking(lane: Lane): Boolean = !level.tutorial && vehicles.getValue(lane).braking
    fun carX(lane: Lane, ahead: Float = 0f): Float {
        if(!level.tutorial) {
            val car = vehicles.getValue(lane)
            return wrap(car.x + direction(lane) * car.speed * ahead)
        }
        val multiplier = (if("school" in plan.street) .7f else 1f) * (if("bump" in plan.street) .65f else 1f)
        val t = trafficClocks.getValue(lane) + if(time < stopUntil) 0f else ahead
        return (((lane.offset + lane.speed * multiplier * t) % 1.4f + 1.4f) % 1.4f) - .2f
    }
    fun emergency() { if(phase == Phase.RUNNING && !emergencyUsed) { emergencyUsed = true; stopUntil = time + 3f } }
    fun tick(dt: Float = STEP) {
        if(phase != Phase.RUNNING) return
        require(dt > 0f && dt.isFinite())
        if(!level.tutorial) {
            var remaining = dt
            while(remaining > .000001f && phase == Phase.RUNNING) {
                val step = minOf(remaining, .02f)
                tickTraffic(step)
                remaining -= step
            }
            return
        }
        time += dt
        val signalled = people.any { p -> "signal" in (plan.people[p.walker.id]?.skills ?: emptySet()) && abs(p.x-.5f)<.05f && !p.done }
        val officer = "officer" in plan.street && time < 6f
        if(time >= stopUntil && !officer && !signalled) {
            level.lanes.forEach { lane ->
                val yield = people.any { p ->
                    val actions = plan.people[p.walker.id]?.skills ?: emptySet()
                    val visible = if(level.night) "visible" in actions else "eye" in actions
                    val distance = (p.x - carX(lane)) * if(lane.speed > 0) 1f else -1f
                    visible && !p.done && p.y > lane.y - .09f && p.y < lane.y + .22f && distance in .10f.. .25f
                }
                if(!yield) trafficClocks[lane] = trafficClocks.getValue(lane) + dt
            }
        }
        for(p in people) {
            if(p.done) continue
            val skills = plan.people[p.walker.id]?.skills ?: emptySet()
            if("wait" in skills && time < 3f) continue
            var velocity = p.walker.speed * (if("crosswalk" in plan.street) 1.2f else 1f)
            if(p.walker.kind == "distracted" && "look" !in skills) velocity *= .75f
            if(p.walker.kind == "wheelchair" && "crosswalk" !in plan.street) velocity *= .5f
            if(p.walker.kind == "phone" && "phone_away" !in skills) velocity *= .5f
            // Looking only helps once the phone is put away, and is moot when
            // drivers already yield: a car frozen by eye contact would otherwise
            // read as a hazard for ever.
            val yielding = ("eye" in skills && !level.night) || ("visible" in skills && level.night)
            val looking = "look" in skills && !yielding && (p.walker.kind != "phone" || "phone_away" in skills)
            val lane = level.lanes.firstOrNull { p.y > it.y + .065f && p.y - it.y < .15f }
            if(lane != null && looking) {
                val duration = (p.y - lane.y + .075f) / velocity
                val hazard = (0..30).any { sample -> abs(carX(lane, duration * sample / 30) - p.x) < .135f }
                if(hazard && !officer && !signalled && time >= stopUntil) continue
            }
            p.y -= velocity * dt
            for(l in level.lanes) {
                val near = abs(p.y-l.y)<.066f && abs(p.x-carX(l))<.115f
                if(near && !yielding && !officer && !signalled && time >= stopUntil) {
                    phase = Phase.RETRY
                    collapsedWalkerId = p.walker.id
                    collapsedLane = l
                    reason = if(level.night) "De noche, un auto no pudo verte a tiempo. Usa el reflector para que te vean." else "El auto no alcanzó a frenar a tiempo. Mira antes de cruzar o cambia el punto de cruce."
                    return
                }
            }
            if(p.y <= .08f) p.done = true
        }
        if(people.all { it.done }) phase = Phase.WON
        if(time > 90f && phase == Phase.RUNNING) { phase=Phase.RETRY; reason="Probemos otro plan para encontrar un momento seguro." }
    }
    private fun direction(lane: Lane) = if(lane.speed >= 0f) 1f else -1f
    private fun wrap(x: Float) = ((x + .2f) % 1.4f + 1.4f) % 1.4f - .2f
    private fun crossing(p: PersonState) = plan.people[p.walker.id]?.crossing ?: 1
    private fun skills(p: PersonState) = plan.people[p.walker.id]?.skills ?: emptySet()
    private fun focused(p: PersonState) = p.walker.kind != "phone" || "phone_away" in skills(p)
    private fun refuge(p: PersonState) = level.island && "refuge" in plan.street && crossing(p) == 1
    private fun sight(p: PersonState): Float {
        val base = level.sight[crossing(p)]
        // Lighting improves darkness, not a view blocked by a parked vehicle.
        val light = if(level.night && !("lighting" in plan.street && crossing(p) == 1)) .55f else 1f
        return base * light * if(level.weather == "fog") .7f else 1f
    }
    private fun pace(p: PersonState): Float = p.walker.speed *
        (if(level.weather == "rain") .85f else 1f) *
        (if(!focused(p)) .65f else 1f)
    private fun stopDistance(v: Float): Float = v * .65f + v * v / (2 * deceleration())
    private fun deceleration() = if(level.weather == "rain") .055f else .12f
    private fun reject(message: String) { phase = Phase.RETRY; reason = message }

    // All units are board fractions and seconds: an educational model, not road engineering data.
    private fun tickTraffic(dt: Float) {
        if(time == 0f) {
            val available = level.tools.toSet()
            val used = plan.street + plan.people.values.flatMap { it.skills }
            if(used.any { it !in available }) { reject("Esa ficha no está disponible en este camino."); return }
            if(plan.people.values.any { it.crossing !in 0..2 }) { reject("Elige uno de los tres cruces."); return }
            val rider = people.firstOrNull { it.walker.kind == "wheelchair" &&
                !("crosswalk" in plan.street && crossing(it) in level.accessible) }
            if(rider != null) { reject("${rider.walker.name} necesita una rampa accesible. El paso accesible está en el cruce indicado; pintar rayas no elimina un bordillo."); return }
        }
        time += dt
        val previous = vehicles.mapValues { it.value.x }
        val green = "signal" in plan.street && time % 22f in 3f..16f
        vehicles.forEach { (lane, car) ->
            val dir = direction(lane)
            val nominal = abs(lane.speed) * if("speed_zone" in plan.street) .55f else 1f
            // Red starts before the pedestrian phase, allowing reaction and actual braking.
            val red = "signal" in plan.street && time % 22f < 16f
            val targets = people.filter { p ->
                val distance = (p.x - car.x) * dir
                val detection = sight(p) * if(level.night && "visible" !in skills(p)) .45f else 1f
                val atRoad = p.y > lane.y - .09f && p.y <= .94f
                val marked = "crosswalk" in plan.street && crossing(p) in level.accessible
                !p.done && atRoad && distance > .10f && distance < detection &&
                    (marked || (red && crossing(p) == 1) || time < stopUntil)
            }
            val target = targets.minByOrNull { (it.x - car.x) * dir }
            val distance = target?.let { (it.x - car.x) * dir - .12f }
            val request = distance != null && distance <= stopDistance(car.speed) + .18f
            car.reaction = if(request) car.reaction + dt else 0f
            car.braking = request && car.reaction >= .65f
            if(car.braking) {
                // Brake even if too late; no visibility or right-of-way grants immunity.
                car.speed = maxOf(0f, car.speed - deceleration() * dt)
            } else car.speed = minOf(nominal, car.speed + .08f * dt)
            car.x = wrap(car.x + dir * car.speed * dt)
        }
        for(p in people) {
            if(p.done) continue
            val actions = skills(p)
            val curb = level.lanes.maxOf { it.y } + .12f
            val onIsland = refuge(p) && p.y <= .515f && p.y >= .49f
            val waiting = p.y >= curb || onIsland
            val lanesAhead = level.lanes.filter { it.y < p.y - .05f && (!refuge(p) || onIsland || it.y > .5f) }
            var move = true
            if(waiting && focused(p)) {
                if("look" in actions) {
                    move = lanesAhead.all { lane ->
                        val car = vehicles.getValue(lane)
                        val duration = (p.y - lane.y + .07f) / pace(p)
                        val arrival = maxOf(0f, (p.y - lane.y - .07f) / pace(p))
                        // A stopped car is only safe when it has stopped BEFORE this crossing.
                        val stopped = car.speed < .002f && (p.x - car.x) * direction(lane) > .115f
                        val horizon = abs(lane.speed) * (if("speed_zone" in plan.street) .55f else 1f) * duration
                        if(!stopped && horizon > sight(p)) false else
                            (0..40).none { sample ->
                                val t = arrival + (duration - arrival) * sample / 40
                                // Predict free-flow speed conservatively; never rely on future braking.
                                val x = wrap(car.x + direction(lane) * maxOf(car.speed, abs(lane.speed) *
                                    if("speed_zone" in plan.street) .55f else 1f) * t)
                                !stopped && abs(x - p.x) < .12f
                            }
                    }
                    if(!move) feedback = if(lanesAhead.any { abs(it.speed) * (if("speed_zone" in plan.street) .55f else 1f) * (p.y-it.y+.07f)/pace(p) > sight(p) })
                        "No hay visibilidad suficiente: busca un cruce despejado, luz, refugio o menos velocidad."
                    else "Esperando un hueco suficiente para completar el tramo sin detenerse en un carril."
                }
                if("eye" in actions) {
                    val allStopped = lanesAhead.all { lane -> val car=vehicles.getValue(lane)
                        car.speed < .002f && (p.x-car.x)*direction(lane) > .115f }
                    move = move && allStopped
                    if(!allStopped) feedback = "Confirmar alto: espera a que TODOS los carriles se detengan; una mirada no basta."
                }
                if("signal" in plan.street && crossing(p) == 1) {
                    val remainingGreen = 16f - time % 22f
                    val needed = (p.y - (lanesAhead.minOfOrNull { it.y } ?: p.y) + .08f) / pace(p)
                    move = move && green && remainingGreen > needed
                    if(!move) feedback = "Semáforo: espera la fase peatonal y comprueba que alcance para todo el tramo."
                }
            }
            val oldY = p.y
            if(move) {
                p.y -= pace(p) * dt
                if(refuge(p) && oldY > .515f && p.y < .515f) p.y = .515f
            }
            for(lane in level.lanes) {
                val oldX = previous.getValue(lane)
                val car = vehicles.getValue(lane)
                // Swept rectangles: solve the overlapping time intervals, not just frame endpoints.
                // A wrap is a new car entering offscreen, never a sweep across the whole road.
                if(abs(car.x-oldX) < .7f && sweptHit(oldX, car.x, oldY, p.y, p.x, lane.y)) {
                    impactSpeed = car.speed
                    collapsedWalkerId = p.walker.id; collapsedLane = lane
                    val cause = when {
                        !focused(p) -> "El celular distrajo a ${p.walker.name}. Guardarlo permite prestar atención, pero también hace falta comprobar el tráfico."
                        level.weather == "rain" -> "Con lluvia el coche necesita más distancia para frenar. Reduce la velocidad y espera un hueco o un alto completo."
                        level.night -> "Con poca luz te detectaron tarde. Ser visible ayuda, pero no sustituye comprobar cada carril."
                        else -> "El coche no podía detenerse dentro de la distancia disponible. Comprueba todos los carriles antes de salir."
                    }
                    reject((if(impactSpeed < .04f) "Contacto a baja velocidad: también puede hacer daño. " else "Choque: el coche seguía en movimiento. ") + cause)
                    return
                }
            }
            if(p.y <= .08f) p.done = true
        }
        if(people.all { it.done }) {
            val unsafe = people.firstOrNull { !focused(it) || skills(it).none { id -> id == "look" || id == "eye" } }
            if(unsafe != null) reject("${unsafe.walker.name} llegó por suerte, sin comprobar el tráfico con atención. El objetivo es un cruce seguro: guarda el celular si lo lleva y elige Mirar o Confirmar alto.")
            else { phase = Phase.WON; feedback = "¡Todos llegaron con un plan seguro!" }
        }
        if(time >= 90f && phase == Phase.RUNNING) reject("Nadie debe cruzar sin condiciones seguras. $feedback Cambia el cruce o combina otras fichas.")
    }
    private fun sweptHit(x0: Float, x1: Float, y0: Float, y1: Float, px: Float, ly: Float): Boolean {
        fun interval(a: Float, b: Float, center: Float, radius: Float): Pair<Float,Float> {
            if(abs(b-a) < .000001f) return if(abs(a-center) < radius) 0f to 1f else 2f to -1f
            val t0=(center-radius-a)/(b-a); val t1=(center+radius-a)/(b-a)
            return minOf(t0,t1) to maxOf(t0,t1)
        }
        val x=interval(x0,x1,px,.115f); val y=interval(y0,y1,ly,.065f)
        return maxOf(0f,x.first,y.first) < minOf(1f,x.second,y.second)
    }
    fun stars(cost: Int): Int = if(phase != Phase.WON) 0 else if(emergencyUsed) 1 else if(cost <= if(level.parCost>0 && !level.tutorial) level.parCost.toFloat() else level.budget * .65f) 3 else 2
    companion object { const val STEP = .05f; val crossings = listOf(.25f,.5f,.75f) }
}

// --- JSON -----------------------------------------------------------------
// Pure parsing, so the campaign file can be checked in plain JVM tests.
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
        json.optString("readyMessage", ""), json.optString("weather", "dry"),
        if(json.has("sight")) json.getJSONArray("sight").let { a -> List(a.length()) { a.getDouble(it).toFloat() } } else listOf(.95f,.95f,.95f),
        if(json.has("accessible")) json.getJSONArray("accessible").let { a -> List(a.length()) { a.getInt(it) } } else listOf(1),
        json.optBoolean("island", false), json.optInt("parCost", 0)
    )
}
