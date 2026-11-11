package com.example.crosswalker.game

import kotlin.math.abs

data class Lane(val y: Float, val speed: Float, val offset: Float, val car: String)
data class Walker(val id: String, val name: String, val kind: String, val speed: Float, val sprite: String)
data class Level(val id: Int, val world: Int, val title: String, val lesson: String, val budget: Int, val night: Boolean, val lanes: List<Lane>, val walkers: List<Walker>)
data class Tool(val id: String, val label: String, val icon: String, val cost: Int, val description: String, val category: String)
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
    val people = level.walkers.map { PersonState(it, crossings[plan.people[it.id]?.crossing ?: 1]) }
    fun carX(lane: Lane, ahead: Float = 0f): Float {
        val multiplier = (if("school" in plan.street) .7f else 1f) * (if("bump" in plan.street) .65f else 1f)
        val t = trafficClocks.getValue(lane) + if(time < stopUntil) 0f else ahead
        return (((lane.offset + lane.speed * multiplier * t) % 1.4f + 1.4f) % 1.4f) - .2f
    }
    fun emergency() { if(phase == Phase.RUNNING && !emergencyUsed) { emergencyUsed = true; stopUntil = time + 3f } }
    fun tick(dt: Float = STEP) {
        if(phase != Phase.RUNNING) return
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
            val lane = level.lanes.firstOrNull { p.y > it.y + .065f && p.y - it.y < .15f }
            if(lane != null && "look" in skills) {
                val duration = (p.y - lane.y + .075f) / velocity
                val hazard = (0..30).any { sample -> abs(carX(lane, duration * sample / 30) - p.x) < .135f }
                if(hazard && !officer && !signalled && time >= stopUntil) continue
            }
            p.y -= velocity * dt
            for(l in level.lanes) {
                val near = abs(p.y-l.y)<.066f && abs(p.x-carX(l))<.115f
                val yielding = ("eye" in skills && !level.night) || ("visible" in skills && level.night)
                if(near && !yielding && !officer && !signalled && time >= stopUntil) {
                    phase = Phase.RETRY
                    reason = if(level.night) "De noche, usa el reflector para que puedan verte." else "Un coche estaba muy cerca. Prueba mirar antes de cruzar o cambia el punto de cruce."
                    return
                }
            }
            if(p.y <= .08f) p.done = true
        }
        if(people.all { it.done }) phase = Phase.WON
        if(time > 90f && phase == Phase.RUNNING) { phase=Phase.RETRY; reason="Probemos otro plan para encontrar un momento seguro." }
    }
    fun stars(cost: Int): Int = if(phase != Phase.WON) 0 else if(emergencyUsed) 1 else if(cost <= level.budget * .65f) 3 else 2
    companion object { const val STEP = .05f; val crossings = listOf(.25f,.5f,.75f) }
}
