package com.example.crosswalker.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    private val root = JSONObject(java.io.File("src/main/assets/campaign.json").readText())
    private val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
    private val tools = root.getJSONArray("tools").let { a -> List(a.length()) { parseTool(a.getJSONObject(it)) } }
    private fun finish(s: Simulation): Simulation { repeat(1801) { if(s.phase == Phase.RUNNING) s.tick() }; return s }
    private fun plan(l: Level, crossing: Int, skill: Set<String>, street: Set<String> = emptySet()) =
        Plan(l.walkers.associate { it.id to Assignment(crossing, skill.toMutableSet()) }.toMutableMap(), street.toMutableSet())

    @Test fun campaignHasMultipleAffordableSolutionsAndRealFailure() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        for(l in levels) {
            val solutions = mutableListOf<String>()
            var threeStars=false
            val street = tools.filter { it.category == "street" && it.id in l.tools }.map { it.id }
            search@ for(mask in 0 until (1 shl street.size)) {
                val selected = street.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
                for(c in 0..2) for(observation in listOf("look", "eye")) for(reflector in listOf(false,true)) {
                    if(observation !in l.tools || reflector && "visible" !in l.tools) continue
                    val p = plan(l,c,setOf(observation),selected)
                    p.people.forEach { (id,a) ->
                        if(l.walkers.first { it.id==id }.kind=="phone" && "phone_away" in l.tools) a.skills.add("phone_away")
                        if(reflector) a.skills.add("visible")
                    }
                    if(p.cost(tools) > l.budget) continue
                    val sim = finish(Simulation(l,p))
                    if(sim.phase==Phase.WON) {
                        if(sim.stars(p.cost(tools))==3) threeStars=true
                        solutions.add("cross=$c people=${p.people.mapValues { it.value.skills }} street=$selected cost=${p.cost(tools)} time=${sim.time}")
                        if(solutions.size>=4 && threeStars) break@search
                    }
                }
            }
            report.append("Level ${l.id}:\n" + solutions.joinToString("\n") + "\n")
            println("Level ${l.id}: ${solutions.size} solutions: ${solutions.firstOrNull()}")
            if(!threeStars) failures.add("No affordable three-star solution in ${l.id}")
            if(solutions.size<2) failures.add("No alternatives in level ${l.id}")
            if(!l.tutorial && finish(Simulation(l,plan(l,1,emptySet()))).phase != Phase.RETRY) failures.add("Empty wins level ${l.id}")
        }
        java.io.File("build/reports/campaign-balance.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        assertTrue(failures.toString(), failures.isEmpty())
    }
    @Test fun noSingleCardTypeSolvesAnyNonTutorialLevel() {
        val wins=mutableListOf<String>()
        for(l in levels.filter { !it.tutorial }) for(t in tools.filter { it.id in l.tools }) for(c in 0..2) {
            val p=if(t.category=="street") plan(l,c,emptySet(),setOf(t.id)) else plan(l,c,setOf(t.id))
            if(p.cost(tools)<=l.budget && finish(Simulation(l,p)).phase==Phase.WON) wins.add("level=${l.id} card=${t.id} crossing=$c")
        }
        assertTrue("Single-card wins: $wins", wins.isEmpty())
    }
    @Test fun tutorialRemainsSolvableWithOnlyLook() {
        assertEquals(Phase.WON,finish(Simulation(levels.first(),plan(levels.first(),1,setOf("look")))).phase)
    }
    @Test fun tutorialDataAndTimingAreFrozen() {
        val baseline=JSONObject(java.io.File("src/test/resources/tutorial-baseline.json").readText())
        assertEquals(parseLevel(baseline),parseLevel(root.getJSONArray("levels").getJSONObject(0)))
        val l=levels.first();val sim=finish(Simulation(l,plan(l,1,setOf("look"))))
        assertEquals(6.95f,sim.time,.0001f)
    }
    @Test fun snapshotsAndDeterminism() {
        val l=levels[1]; val p=plan(l,1,setOf("look","phone_away"))
        val a=Simulation(l,p); val b=Simulation(l,p);p.people.values.first().skills.clear()
        repeat(300) { a.tick();b.tick();assertEquals(a.people.first().y,b.people.first().y,0f); assertEquals(a.carX(l.lanes.first()),b.carX(l.lanes.first()),0f) }
    }
    @Test fun reflectorNeverGrantsCollisionImmunity() {
        val l=levels[8].copy(lanes=listOf(Lane(.7f,.15f,.4f,"car_blue")))
        val s=finish(Simulation(l,plan(l,1,setOf("visible"))))
        assertEquals(Phase.RETRY,s.phase);assertNotNull(s.collapsedWalkerId); assertTrue(s.impactSpeed>0f)
    }
    @Test fun inaccessibleKerbFailsWithAnExplanation() {
        val l=levels[2];val s=finish(Simulation(l,plan(l,2,setOf("look"),setOf("crosswalk"))))
        assertEquals(Phase.RETRY,s.phase);assertTrue(s.reason.contains("rampa"));assertNull(s.collapsedWalkerId)
    }
    @Test fun largeTicksDoNotTunnelThroughCars() {
        val l=levels[1]; val a=Simulation(l,plan(l,1,emptySet())); val b=Simulation(l,plan(l,1,emptySet()))
        repeat(90) {a.tick(1f)};repeat(1800) {b.tick(.05f)}
        assertEquals(Phase.RETRY,a.phase);assertEquals(b.phase,a.phase);assertEquals(b.collapsedWalkerId,a.collapsedWalkerId)
    }
    @Test fun emergencyIsSingleUseAndNotAnInstantStop() {
        val l=levels[1]; val s=Simulation(l,plan(l,1,emptySet()));val lane=l.lanes.first()
        s.emergency();s.tick();assertTrue(s.carSpeed(lane)>0f)
        repeat(80){s.tick()};s.emergency();assertEquals(0f,s.emergencyRemaining,0f)
    }
    @Test fun phoneAwayAloneDoesNotCheckTraffic() {
        val l=levels[1];assertEquals(Phase.RETRY,finish(Simulation(l,plan(l,1,setOf("phone_away")))).phase)
    }
    @Test fun wetRoadNeedsLongerToBrakeAndReflectorImprovesDetection() {
        val base=levels[8].copy(lanes=listOf(Lane(.7f,.18f,.25f,"car_blue")),tools=listOf("eye","visible","crosswalk"))
        fun observed(l: Level)=Simulation(l,plan(l,1,setOf("eye","visible"),setOf("crosswalk"))).apply { repeat(170) {tick(.02f)} }
        val dry=observed(base);val wet=observed(base.copy(weather="rain"))
        assertTrue("Wet road stops later",wet.carSpeed(base.lanes.first())>dry.carSpeed(base.lanes.first()))
        val visible=Simulation(base,plan(base,1,setOf("eye","visible"),setOf("crosswalk")))
        val dark=Simulation(base,plan(base,1,setOf("eye"),setOf("crosswalk")))
        repeat(100){visible.tick(.02f);dark.tick(.02f)}
        assertTrue("Earlier detection permits earlier braking",visible.carSpeed(base.lanes.first())<dark.carSpeed(base.lanes.first()))
    }
    @Test fun signalHasFinitePhasesAndNeverTeleportsVehicles() {
        val l=levels[5];val s=Simulation(l,plan(l,1,setOf("eye"),setOf("signal","crosswalk")))
        val lane=l.lanes.first();val before=s.carX(lane);s.tick(.02f)
        assertTrue("Still moving during reaction",s.carX(lane)!=before)
        assertTrue("No instant freeze",s.carSpeed(lane)>0f)
    }
    @Test fun cardsAndLevelGeometryStayConsistent() {
        val catalog=tools.associateBy {it.id}
        for(l in levels) {
            assertTrue(l.tools.all {it in catalog});assertTrue(l.newTools.all {it in l.tools})
            assertEquals(3,l.sight.size);assertTrue(l.sight.all {it>0f && it<=1f})
            assertTrue(l.accessible.all {it in 0..2})
            if(l.island) assertTrue("Island must be outside all car lanes",l.lanes.none {kotlin.math.abs(it.y-.5f)<.1f})
        }
        assertTrue(tools.none {it.id in setOf("wait","school","bump","officer")})
    }
    @Test fun budgetCountsPersonalCardsPerPersonAndStreetOnce() {
        val l=levels[4];val p=plan(l,1,setOf("look"),setOf("speed_zone"));assertEquals(5,p.cost(tools))
    }
}
