package com.example.crosswalker.game

import kotlin.math.abs
import org.json.JSONObject

data class Lane(val y: Float, val speed: Float, val offset: Float, val car: String,
    val twoWay: Boolean = false, val oncomingOffset: Float = .7f, val blindSpot: Boolean = false)
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
    val parCost: Int = 0,
    val blockedCrossing: Int? = null, // obra/obstáculo fijo: ese cruce no es una opción válida
    val guideTools: List<String> = emptyList(),
    val trafficYields: Boolean = true,
    val trafficPace: Float = 1f,
    val pedestrianCrossings: List<Int> = emptyList()
)
fun Level.hasMarkedCrossing(crossing: Int): Boolean = crossing in pedestrianCrossings

fun Level.showsChildBadge(walker: Walker): Boolean =
    "escort" in tools && walker.kind == "impulsive"

data class Tool(val id: String, val label: String, val icon: String, val cost: Int, val description: String, val category: String)
fun Tool.explanation(level: Level): String = if(id == "look" && !level.tutorial)
    "Comprueba todos los carriles y espera un hueco para completar el tramo. No sirve mientras miras el celular ni si la vista está bloqueada." else description

data class Assignment(var crossing: Int = 1, val skills: MutableSet<String> = mutableSetOf())
data class Plan(val people: MutableMap<String, Assignment> = mutableMapOf(), val street: MutableSet<String> = mutableSetOf()) {
    fun moveWalker(walkerId: String, crossing: Int) {
        val walker = people.getValue(walkerId)
        people.entries.firstOrNull { it.key != walkerId && it.value.crossing == crossing }
            ?.value?.crossing = walker.crossing
        walker.crossing = crossing
    }

    fun cost(tools: List<Tool>) = tools.sumOf { t -> t.cost * (if (t.category == "street") (if(t.id in street) 1 else 0) else people.values.count { t.id in it.skills }) }
    fun copyPlan() = Plan(people.mapValues { Assignment(it.value.crossing, it.value.skills.toMutableSet()) }.toMutableMap(), street.toMutableSet())
}
enum class Phase { PLANNING, RUNNING, WON, RETRY }
// Which car actually caused a collision: the lane's own traffic, its hidden oncoming
// car, or a one-shot ambush car — so the result screen can spotlight that exact
// vehicle instead of darkening the whole lane it drove in.
enum class HitCarRole { MAIN, ONCOMING, AMBUSH }
data class PersonState(val walker: Walker, val x: Float, var y: Float = .91f, var done: Boolean = false)
class Simulation(val level: Level, source: Plan, private val recklessSpeed: Float = RECKLESS_SPEED) {
    val plan = source.copyPlan()
    var phase = Phase.RUNNING; private set
    var time = 0f; private set
    private val trafficClocks = level.lanes.associateWith { 0f }.toMutableMap()
    var reason = ""; private set
    var collapsedWalkerId: String? = null; private set
    var collapsedLane: Lane? = null; private set
    var collapsedCarRole: HitCarRole? = null; private set
    var collapsedCarX: Float? = null; private set
    val people = level.walkers.map { PersonState(it, crossings.getOrElse(plan.people[it.id]?.crossing ?: 1) { crossings[1] }) }
    data class Vehicle(var x: Float, var speed: Float, var reaction: Float = 0f, var braking: Boolean = false,
        var overrideSpeed: Float? = null, var overrideUntil: Float = 0f)
    private val trafficLoopLength = 1.4f * level.trafficPace
    private val trafficLoopStart = (1f - trafficLoopLength) / 2f
    private fun baseSpeed(lane: Lane) =
        abs(lane.speed) * level.trafficPace * if ("speed_zone" in plan.street && !level.tutorial) .55f else 1f
    private val vehicles = level.lanes.associateWith { Vehicle(it.offset - .2f, baseSpeed(it)) }
    // A twoWay lane also carries a hidden car coming from the other direction: the
    // same strip looks one-way, but isn't — "Mirar" must clear both directions.
    // It never yields and runs faster than normal traffic: not every driver follows
    // the rules, which is exactly why looking both ways always matters.
    private val oncoming = level.lanes.filter { it.twoWay }.associateWith { Vehicle(it.oncomingOffset - .2f, baseSpeed(it) * recklessSpeed) }
    private fun carsFor(lane: Lane): List<Triple<Vehicle, Float, Boolean>> = buildList {
        add(Triple(vehicles.getValue(lane), direction(lane), false))
        if(lane.twoWay) add(Triple(oncoming.getValue(lane), -direction(lane), true))
    }
    // True when this lane's own traffic (plus its hidden oncoming car, if any) is
    // already on a collision course with `p` as they cross. In that case a surprise
    // car racing in would just be a second car converging on someone already caught —
    // the whole point of the surprise car is to catch a walker who looks safe but
    // isn't, not to pile on once a real car is already going to hit them.
    private fun laneAlreadyThreatens(lane: Lane, p: PersonState): Boolean {
        val duration = (p.y - lane.y + .07f) / pace(p)
        val arrival = maxOf(0f, (p.y - lane.y - .07f) / pace(p))
        return carsFor(lane).any { (car, dir, _) ->
            (0..40).any { sample ->
                val t = arrival + (duration - arrival) * sample / 40
                abs(wrap(car.x + dir * car.speed * t) - p.x) < .12f
            }
        }
    }
    // The demonstration car only collides with the walker who triggered it.
    // In single-walker levels an existing car can play that role.
    private class AmbushCar(val lane: Lane, var x: Float, var prevX: Float, val dir: Float, val speed: Float,
        val until: Float, val walkerId: String)
    private val recklessTriggered = mutableSetOf<String>()
    private val ambushCars = mutableMapOf<String, AmbushCar>()
    var impactSpeed = 0f; private set
    var feedback = ""; private set
    fun carSpeed(lane: Lane): Float = if(level.tutorial) abs(lane.speed) else vehicles.getValue(lane).speed
    fun isBraking(lane: Lane): Boolean = !level.tutorial && vehicles.getValue(lane).braking
    fun oncomingCarX(lane: Lane): Float = oncoming.getValue(lane).x
    // (x, direction) of every ambush car currently racing through this lane — almost
    // always none; only present for the brief dash after someone crosses unprepared.
    fun ambushCarsFor(lane: Lane): List<Pair<Float, Float>> = ambushCars.values.filter { it.lane == lane }.map { it.x to it.dir }
    fun carX(lane: Lane, ahead: Float = 0f): Float {
        if(!level.tutorial) {
            val car = vehicles.getValue(lane)
            return wrap(car.x + direction(lane) * car.speed * ahead)
        }
        val multiplier = (if("school" in plan.street) .7f else 1f) * (if("bump" in plan.street) .65f else 1f)
        val t = trafficClocks.getValue(lane) + ahead
        return (((lane.offset + lane.speed * multiplier * t) % 1.4f + 1.4f) % 1.4f) - .2f
    }
    fun tick(dt: Float = STEP) {
        if(phase != Phase.RUNNING) return
        require(dt > 0f && dt.isFinite())
        if (time == 0f && people.map { crossing(it) }.distinct().size != people.size) {
            reject("Asigna un cruce diferente a cada peatón.")
            return
        }
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
        val officer = "officer" in plan.street && time < 6f
        if(!officer) {
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
                if(hazard && !officer) continue
            }
            p.y -= velocity * dt
            for(l in level.lanes) {
                val near = abs(p.y-l.y)<.066f && abs(p.x-carX(l))<.115f
                if(near && !yielding && !officer) {
                    phase = Phase.RETRY
                    collapsedWalkerId = p.walker.id
                    collapsedLane = l
                    collapsedCarRole = HitCarRole.MAIN
                    collapsedCarX = carX(l)
                    reason = collisionReason(p, l, HitCarRole.MAIN, staged = false)
                    return
                }
            }
            if(p.y <= .08f) p.done = true
        }
        if(people.all { it.done }) phase = Phase.WON
        if(time > 90f && phase == Phase.RUNNING) { phase=Phase.RETRY; reason="Probemos otro plan para encontrar un momento seguro." }
    }
    private fun direction(lane: Lane) = if(lane.speed >= 0f) 1f else -1f
    private fun wrap(x: Float): Float =
        ((x - trafficLoopStart) % trafficLoopLength + trafficLoopLength) % trafficLoopLength + trafficLoopStart
    private fun crossing(p: PersonState) = plan.people[p.walker.id]?.crossing ?: 1
    private fun skills(p: PersonState) = plan.people[p.walker.id]?.skills ?: emptySet()
    private fun focused(p: PersonState) = p.walker.kind != "phone" || "phone_away" in skills(p)
    private val headlightsOn = level.night && "car_lights" in plan.street
    private val carefulNightTraffic = headlightsOn && "speed_zone" in plan.street

    private fun outsideNightCrossing(p: PersonState): Boolean =
        level.night && level.pedestrianCrossings.isNotEmpty() && !level.hasMarkedCrossing(crossing(p))

    private fun checksTraffic(p: PersonState): Boolean =
        focused(p) && !outsideNightCrossing(p) && ("look" in skills(p) || carefulNightTraffic)
    private fun sight(p: PersonState): Float {
        val base = level.sight[crossing(p)]
        // Lighting improves darkness, not a view blocked by a parked vehicle. A reflector
        // catches and scatters nearby light, so it softens the dark a little for its own
        // wearer too — not as much as a real streetlamp, but a real, visible improvement.
        val light = when {
            !level.night -> 1f
            "lighting" in plan.street && crossing(p) == 1 -> 1f
            headlightsOn -> .9f
            "visible" in skills(p) -> .75f
            else -> .55f
        }
        // An impulsive walker alone judges gaps with less margin; an escort compensates.
        val impulsive = if(p.walker.kind == "impulsive" && "escort" !in skills(p)) .8f else 1f
        // At night, a car's own headlights are what let you see IT coming from far away —
        // separate from the street being lit, and separate from you wearing a reflector.
        val headlights = if (level.night && !headlightsOn) .7f else 1f
        return base * light * impulsive * headlights * if(level.weather == "fog") .7f else 1f
    }
    // Headphones are a hearing problem, not a seeing one: a driver can still spot this
    // walker from just as far away (unlike night, fog or standing behind an obstruction,
    // which really do shrink what both sides can see of each other) — so this only
    // narrows how far THIS walker can trust their own "Mirar" check, never `sight(p)`
    // itself, which stepVehicle() also reads for how far a driver notices a pedestrian.
    private fun perceivedRange(p: PersonState): Float =
        sight(p) * (if(p.walker.kind == "headphones" && "headphones_off" !in skills(p)) .5f else 1f)
    fun sightRange(walkerId: String): Float = people.find { it.walker.id == walkerId }?.let { perceivedRange(it) } ?: 1f
    private fun pace(p: PersonState): Float = p.walker.speed *
        (if(level.weather == "rain") .85f else 1f) *
        (if(!focused(p)) .65f else 1f)
    private fun stopDistance(v: Float): Float = v * .65f + v * v / (2 * deceleration())
    private fun deceleration() = if(level.weather == "rain") .055f else .12f
    private fun reject(message: String) { phase = Phase.RETRY; reason = message }

    // One car's physics for one tick: shared by the lane's own car and, on a
    // twoWay lane, the hidden car coming from the other direction (dir flipped).
    // `reckless` cars never yield to a crossing pedestrian — they just speed through.
    private fun stepVehicle(lane: Lane, car: Vehicle, dir: Float, dt: Float, reckless: Boolean = false) {
        // A walker who stepped out unprepared gets this exact car racing to meet them,
        // at a fixed speed timed for the encounter — same contract as an AmbushCar,
        // just reusing the lane's own vehicle instead of spawning a second one.
        car.overrideSpeed?.let { speed ->
            car.braking = false; car.reaction = 0f
            car.speed = speed
            car.x = wrap(car.x + dir * speed * dt)
            return
        }
        val nominal = baseSpeed(lane) * if (reckless) recklessSpeed else 1f
        if (reckless || !level.trafficYields) {
            car.braking = false; car.reaction = 0f
            car.speed = minOf(nominal, car.speed + .08f * dt)
            car.x = wrap(car.x + dir * car.speed * dt)
            return
        }
        val targets = people.filter { p ->
            val distance = (p.x - car.x) * dir
            val detection = sight(p) * (if (level.night && !headlightsOn && "visible" !in skills(p)) .45f else 1f) * (if(lane.blindSpot) .5f else 1f)
            val atRoad = p.y > lane.y - .09f && p.y <= .94f
            val marked = if (level.night && level.pedestrianCrossings.isNotEmpty()) {
                level.hasMarkedCrossing(crossing(p))
            } else {
                ("crosswalk" in plan.street && crossing(p) in level.accessible) || headlightsOn
            }
            !p.done && atRoad && distance > .10f && distance < detection &&
                marked
        }
        val target = targets.minByOrNull { (it.x - car.x) * dir }
        val distance = target?.let { (it.x - car.x) * dir - .12f }
        val request = distance != null && distance <= stopDistance(car.speed) + .35f
        car.reaction = if(request) car.reaction + dt else 0f
        car.braking = request && car.reaction >= .35f
        if(car.braking) {
            // Brake even if too late; no visibility or right-of-way grants immunity.
            car.speed = maxOf(0f, car.speed - deceleration() * dt)
        } else car.speed = minOf(nominal, car.speed + .08f * dt)
        car.x = wrap(car.x + dir * car.speed * dt)
    }

    // All units are board fractions and seconds: an educational model, not road engineering data.
    private fun collisionReason(p: PersonState, lane: Lane, role: HitCarRole, staged: Boolean): String {
        val vehicle = when {
            role == HitCarRole.ONCOMING -> "un coche que venía en sentido contrario"
            lane.blindSpot -> "un vehículo grande"
            else -> "un coche"
        }
        if (outsideNightCrossing(p)) {
            return "${p.walker.name} cruzó fuera del paso peatonal y chocó con $vehicle. " +
                "De noche, usa un cruce con franjas blancas. Ahí los conductores esperan peatones."
        }
        val action = when {
            !focused(p) && "phone_away" in level.tools -> "Asígnale Guardar celular en Habilidades."
            level.night && !headlightsOn && "car_lights" in level.tools -> "Activa Encender luces para mejorar la visibilidad."
            level.night && "speed_zone" in level.tools && "speed_zone" !in plan.street ->
                "Activa Zona lenta para dar a los conductores más tiempo de reacción."
            !checksTraffic(p) && "look" in level.tools -> "Asígnale Mirar para que espere un hueco."
            level.weather == "rain" -> "Con lluvia aumenta la distancia de frenado. Prueba otro cruce o deja más margen."
            else -> "Prueba otro cruce para que tenga más margen."
        }
        val event = if (staged && !checksTraffic(p)) {
            "${p.walker.name} entró al carril sin comprobar el tráfico y chocó con $vehicle."
        } else {
            "${p.walker.name} chocó con $vehicle mientras cruzaba."
        }
        return "$event $action"
    }

    private val waitingReasons = mutableMapOf<String, String>()

    private fun tickTraffic(dt: Float) {
        if(time == 0f) {
            val available = level.tools.toSet()
            val used = plan.street + plan.people.values.flatMap { it.skills }
            if(used.any { it !in available }) { reject("Esa ficha no está disponible en este camino."); return }
            if(plan.people.values.any { it.crossing !in 0..2 }) { reject("Elige uno de los tres cruces."); return }
            if(level.blockedCrossing != null && plan.people.values.any { it.crossing == level.blockedCrossing }) {
                reject("Ese cruce está bloqueado por una obra. Elige otro camino."); return
            }
            val rider = people.firstOrNull { it.walker.kind == "wheelchair" &&
                !("crosswalk" in plan.street && crossing(it) in level.accessible) }
            if(rider != null) { reject("${rider.walker.name} necesita una rampa. Usa la ficha Rampa y elige uno de los cruces indicados."); return }
            val distracted = people.filterNot(::focused)
            if (distracted.isNotEmpty() && "phone_away" in available) {
                reject(distracted.joinToString(" ") {
                    "${it.walker.name} sigue mirando el celular. Asígnale Guardar celular en Habilidades."
                })
                return
            }
        }
        time += dt
        val previous = vehicles.mapValues { it.value.x }
        val previousOncoming = oncoming.mapValues { it.value.x }
        vehicles.forEach { (lane, car) -> stepVehicle(lane, car, direction(lane), dt) }
        oncoming.forEach { (lane, car) -> stepVehicle(lane, car, -direction(lane), dt, reckless = true) }
        // A dash, not a simulated car: constant speed, no braking, swept away once it expires.
        for(a in ambushCars.values) { a.prevX = a.x; a.x = wrap(a.x + a.dir * a.speed * dt) }
        for(p in people) {
            if(p.done) continue
            waitingReasons.remove(p.walker.id)
            val curb = level.lanes.maxOf { it.y } + .12f
            val lanesAhead = level.lanes.filter { it.y < p.y - .05f }
            val checking = checksTraffic(p)
            var move = true
            if (p.y >= curb && checking) {
                var limitedVisibility = false
                move = lanesAhead.all { lane ->
                    val duration = (p.y - lane.y + .07f) / pace(p)
                    val arrival = maxOf(0f, (p.y - lane.y - .07f) / pace(p))
                    carsFor(lane).all { (car, dir, isOncoming) ->
                        val nominal = baseSpeed(lane) * if (isOncoming) recklessSpeed else 1f
                        // A stopped car is only safe when it has stopped BEFORE this crossing.
                        // The reckless oncoming car never stops, so it never counts as safe that way.
                        val stopped = !isOncoming && car.speed < .002f && (p.x - car.x) * dir > .115f
                        // Compare with the level's sight range in its original units.
                        // The position prediction below uses the faster traffic and longer loop.
                        val horizon = nominal * duration / level.trafficPace
                        if (!stopped && horizon > perceivedRange(p)) {
                            limitedVisibility = true
                            false
                        } else
                            (0..40).none { sample ->
                                val t = arrival + (duration - arrival) * sample / 40
                                // Predict free-flow speed conservatively; never rely on future braking.
                                val x = wrap(car.x + dir * maxOf(car.speed, nominal) * t)
                                !stopped && abs(x - p.x) < .12f
                            }
                    }
                }
                if (!move) {
                    val help = when {
                        !limitedVisibility -> "no encontró un hueco suficiente. Prueba otro cruce."
                        p.walker.kind == "headphones" && "headphones_off" !in skills(p) && "headphones_off" in level.tools ->
                            "no tenía margen para comprobar el tráfico con los audífonos puestos. Asígnale Quitar audífonos."
                        level.night && !headlightsOn && "car_lights" in level.tools ->
                            "no alcanzó a ver el tráfico desde su cruce. Activa Encender luces."
                        "speed_zone" in level.tools && "speed_zone" !in plan.street ->
                            "necesita más margen para cruzar. Prueba Zona lenta u otro cruce."
                        else -> "no tuvo visibilidad suficiente para completar el cruce. Revisa su cruce y las mejoras disponibles."
                    }
                    val explanation = "${p.walker.name} se quedó esperando: $help"
                    waitingReasons[p.walker.id] = explanation
                }
            }
            val oldY = p.y
            if (move) p.y -= pace(p) * dt
            // Stepping into a lane without having checked it (no Mirar, or still on the
            // phone): the danger is made real, not a coin flip. Whichever car is relevant
            // — the reckless two-way one, or the lane's own car otherwise — races in from
            // the entry edge it would naturally come from, so it visibly catches whoever
            // just walked in unprepared, instead of popping up out of nowhere or a text
            // error nobody got to see coming.
            if (!checking) {
                for(lane in level.lanes) {
                    val key = "${p.walker.id}|${lane.y}"
                    if(key in recklessTriggered) continue
                    if(p.y in (lane.y - .02f)..(lane.y + .14f)) {
                        recklessTriggered += key
                        if(laneAlreadyThreatens(lane, p)) continue
                        // The surprise car always comes from the corner nearest to where the
                        // walker is: crossing on the left, it races in left-to-right (and the
                        // mirror for the right). Centred, there's no near corner to favour, so
                        // it falls back to whatever's already logical for this lane (the
                        // opposite of this lane's own traffic when it's two-way, same direction
                        // otherwise).
                        val dir = when(crossing(p)) {
                            0 -> 1f
                            2 -> -1f
                            else -> if(lane.twoWay) -direction(lane) else direction(lane)
                        }
                        val edge = if(dir > 0f) -.2f else 1.2f
                        // Aim for when the walker is centred in the lane — not instantly, but
                        // fast enough that a normal acceleration curve could never arrive in
                        // time from off-screen.
                        val timeToCenter = maxOf(.25f, (p.y - lane.y) / pace(p))
                        val speed = abs(p.x - edge) / timeToCenter
                        val until = time + timeToCenter + .4f
                        val reuse = when {
                            dir == direction(lane) -> vehicles.getValue(lane)
                            lane.twoWay && dir == -direction(lane) -> oncoming.getValue(lane)
                            else -> null
                        }
                        // Repositioning live traffic would invalidate other walkers' gap checks.
                        if (reuse != null && people.size == 1) {
                            reuse.x = edge
                            reuse.overrideSpeed = speed
                            reuse.overrideUntil = until
                        } else {
                            ambushCars[key] = AmbushCar(lane, edge, edge, dir, speed, until, p.walker.id)
                        }
                    }
                }
            }
            for(lane in level.lanes) {
                val hits = buildList {
                    add(Triple(vehicles.getValue(lane), previous.getValue(lane), false))
                    if(lane.twoWay) add(Triple(oncoming.getValue(lane), previousOncoming.getValue(lane), true))
                }
                // Swept rectangles: solve the overlapping time intervals, not just frame endpoints.
                // A wrap is a new car entering offscreen, never a sweep across the whole road.
                for((car, oldX, isOncoming) in hits) {
                    if(abs(car.x-oldX) < .7f && sweptHit(oldX, car.x, oldY, p.y, p.x, lane.y)) {
                        impactSpeed = car.speed
                        collapsedWalkerId = p.walker.id; collapsedLane = lane
                        val role = if (isOncoming) HitCarRole.ONCOMING else HitCarRole.MAIN
                        collapsedCarRole = role
                        collapsedCarX = car.x
                        reject(collisionReason(p, lane, role, staged = car.overrideSpeed != null))
                        return
                    }
                }
                for(a in ambushCars.values.filter { it.lane == lane }) {
                    if(a.walkerId == p.walker.id && abs(a.x - a.prevX) < .7f && sweptHit(a.prevX, a.x, oldY, p.y, p.x, lane.y)) {
                        impactSpeed = a.speed
                        collapsedWalkerId = p.walker.id; collapsedLane = lane
                        collapsedCarRole = HitCarRole.AMBUSH
                        collapsedCarX = a.x
                        reject(collisionReason(p, lane, HitCarRole.AMBUSH, staged = true))
                        return
                    }
                }
            }
            if(p.y <= .08f) p.done = true
        }
        ambushCars.entries.removeIf { time > it.value.until }
        (vehicles.values + oncoming.values).forEach {
            if (it.overrideSpeed != null && time > it.overrideUntil) {
                it.overrideSpeed = null
            }
        }
        feedback = waitingReasons.values.firstOrNull().orEmpty()
        if(people.all { it.done }) { phase = Phase.WON; feedback = "¡Todos llegaron con un plan seguro!" }
        if (time >= 90f && phase == Phase.RUNNING) {
            val pending = people.filterNot { it.done }
            reject(pending.joinToString(" ") { person ->
                waitingReasons[person.walker.id]
                    ?: "${person.walker.name} no terminó de cruzar antes de que se acabara el tiempo."
            })
        }
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
    fun stars(cost: Int): Int = if(phase != Phase.WON) 0 else if(cost <= if(level.parCost>0 && !level.tutorial) level.parCost.toFloat() else level.budget * .65f) 3 else 2
    companion object { const val STEP = .05f; val crossings = listOf(.25f,.5f,.75f); const val RECKLESS_SPEED = 1.08f }
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
        Lane(it.getDouble("y").toFloat(), it.getDouble("speed").toFloat(), it.getDouble("offset").toFloat(), it.getString("car"),
            it.optBoolean("twoWay", false), it.optDouble("oncomingOffset", .7).toFloat(), it.optBoolean("blindSpot", false))
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
        json.optInt("parCost", 0),
        if(json.has("blockedCrossing")) json.getInt("blockedCrossing") else null,
        json.stringList("guideTools"),
        json.optBoolean("trafficYields", true),
        json.optDouble("trafficPace", 1.0).toFloat(),
        json.optJSONArray("pedestrianCrossings")?.let { crossings ->
            List(crossings.length()) { crossings.getInt(it) }
        }.orEmpty()
    )
}
