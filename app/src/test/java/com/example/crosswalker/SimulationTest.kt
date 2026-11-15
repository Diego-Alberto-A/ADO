package com.example.crosswalker.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    private val root = JSONObject(java.io.File("src/main/assets/campaign.json").readText())
    private val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
    private val tools = root.getJSONArray("tools").let { a -> List(a.length()) { parseTool(a.getJSONObject(it)) } }
    private fun finish(s: Simulation): Simulation { repeat(1801) { if(s.phase == Phase.RUNNING) s.tick() }; return s }
    private fun crossingsFor(l: Level, first: Int): List<Int> =
        (listOf(first, 0, 1, 2).distinct().filter { it == first || it != l.blockedCrossing }).take(l.walkers.size)

    private fun plan(l: Level, crossing: Int, skill: Set<String>, street: Set<String> = emptySet()) =
        planFor(l, crossing, l.walkers.associate { it.id to skill }, street)

    private fun planFor(l: Level, crossing: Int, skills: Map<String, Set<String>>, street: Set<String> = emptySet(), crossings: List<Int> = crossingsFor(l, crossing)): Plan {
        return Plan(l.walkers.mapIndexed { index, walker ->
            walker.id to Assignment(crossings[index], skills[walker.id].orEmpty().toMutableSet())
        }.toMap().toMutableMap(), street.toMutableSet())
    }

    @Test fun duplicateCrossingsAreRejectedBeforeAnyoneMoves() {
        val level = levels.first { it.id == 5 }
        val duplicate = Plan(level.walkers.associate { it.id to Assignment(1) }.toMutableMap())
        val sim = Simulation(level, duplicate)
        sim.tick()
        assertEquals(Phase.RETRY, sim.phase)
        assertEquals(0f, sim.time, 0f)
        assertEquals("Asigna un cruce diferente a cada peatón.", sim.reason)
    }

    @Test fun movingToAnOccupiedCrossingSwapsWalkersAndKeepsTheirSkills() {
        val plan = Plan(mutableMapOf("ana" to Assignment(1, mutableSetOf("escort")), "tomas" to Assignment(0)))
        plan.moveWalker("ana", 0)
        assertEquals(0, plan.people.getValue("ana").crossing)
        assertEquals(1, plan.people.getValue("tomas").crossing)
        assertEquals(setOf("escort"), plan.people.getValue("ana").skills)
    }

    @Test fun fasterTrafficKeepsTheTimeBetweenPasses() {
        val level = levels.first { it.id == 6 }
        val lane = level.lanes.first()
        val initial = plan(level, 1, setOf("look"), setOf("crosswalk"))
        val regular = Simulation(level.copy(trafficPace = 1f), initial)
        val faster = Simulation(level, initial)
        assertEquals(regular.carSpeed(lane) * 1.35f, faster.carSpeed(lane), .0001f)

        val lap = 1.4f / regular.carSpeed(lane)
        assertEquals(regular.carX(lane), regular.carX(lane, lap), .0001f)
        assertEquals(faster.carX(lane), faster.carX(lane, lap), .0001f)
    }

    @Test fun level7TrafficCollidesWithoutSlowZoneEvenAfterLooking() {
        val level = levels.first { it.id == 7 }
        val skills = mapOf("luz" to setOf("look"), "ana" to setOf("look", "escort"))
        for (crossings in distinctCrossingOrders(level)) {
            val plan = planFor(level, crossings.first(), skills, crossings = crossings)
            val sim = Simulation(level, plan)
            val brakingLanes = mutableSetOf<Lane>()
            while (sim.phase == Phase.RUNNING && sim.time < 30f) {
                sim.tick()
                level.lanes.forEach { lane ->
                    assertTrue("Car stopped at ${sim.time}s", sim.carSpeed(lane) > 0f)
                    if (sim.isBraking(lane)) brakingLanes.add(lane)
                }
            }
            assertEquals("Crossings $crossings: ${sim.reason}", Phase.RETRY, sim.phase)
            assertNotNull("Expected a collision at crossings $crossings", sim.collapsedWalkerId)
            assertEquals(HitCarRole.MAIN, sim.collapsedCarRole)
            assertTrue(sim.impactSpeed > 0f)
            assertTrue(sim.reason.contains("lluvia"))
            assertTrue(sim.reason.contains("Bajar velocidad"))
            if (crossings == listOf(1, 0)) {
                assertTrue("The approaching car should brake before impact", sim.collapsedLane in brakingLanes)
            }
        }

        val plan = planFor(level, 1, skills, setOf("speed_zone"))
        assertEquals(Phase.WON, finish(Simulation(level, plan)).phase)
    }

    @Test fun plansSuggestedByTheLevelGuidesWinWithinBudget() {
        val allLook = setOf("look")
        val suggested = listOf(
            plan(levels[0], 1, allLook),
            plan(levels[1], 1, allLook + "phone_away"),
            plan(levels[2], 1, allLook, setOf("crosswalk")),
            plan(levels[3], 1, allLook + "headphones_off"),
            planFor(levels[4], 1, mapOf("ana" to allLook + "escort", "tomas" to allLook + "phone_away")),
            plan(levels[5], 1, allLook, setOf("crosswalk")),
            plan(levels[6], 1, allLook, setOf("speed_zone")),
            plan(levels[7], 1, allLook, setOf("crosswalk")),
            plan(levels[8], 1, emptySet(), setOf("car_lights", "speed_zone")),
            planFor(levels[9], 0, mapOf("tomas" to setOf("phone_away")), setOf("car_lights", "speed_zone"), listOf(0, 2)),
            plan(levels[10], 1, emptySet(), setOf("car_lights", "speed_zone")),
            planFor(levels[11], 1, mapOf("tomas" to setOf("phone_away")), setOf("crosswalk", "car_lights", "speed_zone"))
        )
        levels.zip(suggested).forEach { (level, plan) ->
            assertTrue("Nivel ${level.id}: presupuesto", plan.cost(tools) <= level.budget)
            val sim = finish(Simulation(level, plan))
            assertEquals("Nivel ${level.id}: ${sim.reason}", Phase.WON, sim.phase)
        }
    }

    @Test fun childBadgeAppearsWhenEscortIsIntroduced() {
        assertFalse(levels[0].showsChildBadge(levels[0].walkers.single()))
        val escortLevel = levels.first { "escort" in it.newTools }
        assertEquals(listOf("ana"), escortLevel.walkers.filter(escortLevel::showsChildBadge).map { it.id })
    }

    private fun distinctCrossingOrders(level: Level): List<List<Int>> {
        val available = Simulation.crossings.indices.filter { it != level.blockedCrossing }
        fun orders(remaining: List<Int>, count: Int): List<List<Int>> =
            if (count == 0) listOf(emptyList()) else remaining.flatMap { crossing ->
                orders(remaining - crossing, count - 1).map { listOf(crossing) + it }
            }
        return orders(available, level.walkers.size)
    }

    @Test fun campaignHasMultipleAffordableSolutionsAndRealFailure() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        for(l in levels) {
            val solutions = mutableListOf<String>()
            var threeStars=false
            val street = tools.filter { it.category == "street" && it.id in l.tools }.map { it.id }
            search@ for(mask in 0 until (1 shl street.size)) {
                val selected = street.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
                for(crossings in distinctCrossingOrders(l)) for(reflector in listOf(false,true)) for(escort in listOf(false,true)) {
                    if(reflector && "visible" !in l.tools || escort && "escort" !in l.tools) continue
                    val p = planFor(l, crossings.first(), l.walkers.associate { it.id to if ("look" in l.tools) setOf("look") else emptySet() }, selected, crossings)
                    p.people.forEach { (id,a) ->
                        val kind=l.walkers.first { it.id==id }.kind
                        if(kind=="phone" && "phone_away" in l.tools) a.skills.add("phone_away")
                        if(kind=="headphones" && "headphones_off" in l.tools) a.skills.add("headphones_off")
                        if(reflector) a.skills.add("visible")
                        if(escort && kind=="impulsive") a.skills.add("escort")
                    }
                    if(p.cost(tools) > l.budget) continue
                    val sim = finish(Simulation(l,p))
                    if(sim.phase==Phase.WON) {
                        if(sim.stars(p.cost(tools))==3) threeStars=true
                        solutions.add("cross=${p.people.mapValues { it.value.crossing }} people=${p.people.mapValues { it.value.skills }} street=$selected cost=${p.cost(tools)} time=${sim.time}")
                        if(solutions.size>=4 && threeStars) break@search
                    }
                }
            }
            report.append("Level ${l.id}:\n" + solutions.joinToString("\n") + "\n")
            println("Level ${l.id}: ${solutions.size} solutions: ${solutions.firstOrNull()}")
            if(!threeStars) failures.add("No affordable three-star solution in ${l.id}")
            // The first night lesson has one marked crossing and teaches one required pair of cards.
            if (l.id != 9 && solutions.size < 2) failures.add("No alternatives in level ${l.id}")
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
    @Test fun reflectorAloneNeverWins() {
        val l=levels[8].copy(lanes=listOf(Lane(.7f,.15f,.4f,"car_blue")), tools = listOf("visible"))
        val s=finish(Simulation(l,plan(l,1,setOf("visible"))))
        assertEquals(Phase.RETRY,s.phase)
        // No Mirar means nobody ever checked traffic, so the plan never
        // gets an instant text rejection anymore — it runs, and a real car catches them.
        assertNotNull(s.collapsedWalkerId)
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
    @Test fun distractedWalkerIsIdentifiedBeforeTrafficCanHitSomeoneElse() {
        val level = levels.first { it.id == 10 }
        for (crossings in distinctCrossingOrders(level)) {
            val sim = Simulation(level, planFor(level, 0, emptyMap(), setOf("car_lights", "speed_zone"), crossings))
            sim.tick()
            assertEquals(Phase.RETRY, sim.phase)
            assertEquals(0f, sim.time, 0f)
            assertNull(sim.collapsedWalkerId)
            assertEquals("Tomás sigue mirando el celular. Asígnale Guardar celular en Habilidades.", sim.reason)
        }
    }

    @Test fun collisionExplanationNamesThePedestrianWhoWasHit() {
        val level = levels.first { it.id == 10 }
        val skills = mapOf("tomas" to setOf("phone_away"))
        val sim = finish(Simulation(level, planFor(level, 0, skills, setOf("speed_zone"), listOf(0, 2))))
        val hit = level.walkers.single { it.id == sim.collapsedWalkerId }
        assertTrue(sim.reason, sim.reason.contains(hit.name))
        assertTrue(sim.reason, sim.reason.contains("Encender luces"))
        assertFalse(sim.reason, sim.reason.contains("Guardar celular"))
    }

    @Test fun waitingExplanationIdentifiesLeoAndHisHeadphones() {
        val level = levels.first { it.id == 4 }
        val sim = finish(Simulation(level, plan(level, 1, setOf("look"))))
        assertEquals(Phase.RETRY, sim.phase)
        assertNull(sim.collapsedWalkerId)
        assertTrue(sim.reason, sim.reason.startsWith("Leo se quedó esperando"))
        assertTrue(sim.reason, sim.reason.contains("Quitar audífonos"))
    }

    @Test fun phoneAwayAloneDoesNotCheckTraffic() {
        val l=levels[1];assertEquals(Phase.RETRY,finish(Simulation(l,plan(l,1,setOf("phone_away")))).phase)
    }
    @Test fun wetRoadNeedsLongerToBrakeAndHeadlightsReplaceReflectors() {
        val base=levels[8].copy(lanes=listOf(Lane(.7f,.18f,.25f,"car_blue")),tools=listOf("look","visible","crosswalk","car_lights"))
        fun observed(l: Level)=Simulation(l,plan(l,1,setOf("look","visible"),setOf("crosswalk","car_lights"))).apply { repeat(170) {tick(.02f)} }
        val dry=observed(base);val wet=observed(base.copy(weather="rain"))
        assertTrue("Wet road stops later",wet.carSpeed(base.lanes.first())>dry.carSpeed(base.lanes.first()))
        val visible=Simulation(base,plan(base,1,setOf("look","visible"),setOf("crosswalk","car_lights")))
        val noReflector = Simulation(base, plan(base, 1, setOf("look"), setOf("crosswalk", "car_lights")))
        val noHeadlights = Simulation(base, plan(base, 1, setOf("look"), setOf("crosswalk")))
        repeat(100) {
            visible.tick(.02f)
            noReflector.tick(.02f)
            noHeadlights.tick(.02f)
        }
        val lane = base.lanes.first()
        assertEquals(visible.carSpeed(lane), noReflector.carSpeed(lane), .0001f)
        assertEquals(visible.sightRange("ana"), noReflector.sightRange("ana"), .0001f)
        assertTrue(noReflector.carSpeed(lane) < noHeadlights.carSpeed(lane))
    }
    @Test fun blindSpotLaneDetectsPedestriansLaterEvenByDay() {
        val base=levels[2].copy(lanes=listOf(Lane(.7f,.15f,0f,"car_blue")),
            walkers=listOf(Walker("ana","Ana","impulsive",.12f,"walker_coral")))
        val blind=base.copy(lanes=listOf(Lane(.7f,.15f,0f,"car_green",blindSpot=true)))
        val baseSim=Simulation(base,plan(base,1,setOf("look"),setOf("crosswalk")))
        val blindSim=Simulation(blind,plan(blind,1,setOf("look"),setOf("crosswalk")))
        repeat(120) { baseSim.tick(.02f); blindSim.tick(.02f) }
        assertTrue("Blind-spot car should still be moving while the normal one already reacted",
            blindSim.carSpeed(blind.lanes.first())>baseSim.carSpeed(base.lanes.first()))
    }
    @Test fun carsWithoutHeadlightsAreHarderToSeeAtNight() {
        val base=levels[2].copy(night=true,
            lanes=listOf(Lane(.7f,.15f,0f,"car_blue")),
            walkers=listOf(Walker("ana","Ana","impulsive",.12f,"walker_coral")),
            tools=listOf("look","car_lights"))
        val dark=Simulation(base,plan(base,1,setOf("look")))
        val lit=Simulation(base,plan(base,1,setOf("look"),setOf("car_lights")))
        assertTrue("Sin luces, de noche, se ve menos lejos",
            dark.sightRange("ana")<lit.sightRange("ana"))
    }
    @Test fun reflectorAlsoHelpsItsWearerSeeAtNight() {
        val base=levels[2].copy(night=true,
            lanes=listOf(Lane(.7f,.15f,0f,"car_blue")),
            walkers=listOf(Walker("ana","Ana","impulsive",.12f,"walker_coral")),
            tools=listOf("look","visible","escort"))
        val without=Simulation(base,plan(base,1,setOf("look","escort")))
        val with=Simulation(base,plan(base,1,setOf("look","escort","visible")))
        assertTrue("El reflector tambien debe ayudar a ver, no solo a ser visto",
            without.sightRange("ana")<with.sightRange("ana"))
    }
    @Test fun leoCanCrossAtNormalTrafficSpeedAfterRemovingHeadphones() {
        val level = levels.first { it.id == 4 }
        val wearing = finish(Simulation(level, plan(level, 1, setOf("look"))))
        val removed = finish(Simulation(level, plan(level, 1, setOf("look", "headphones_off"))))
        assertEquals(Phase.RETRY, wearing.phase)
        assertEquals(Phase.WON, removed.phase)
    }

    @Test fun headphonesWalkerPerceivesLessUntilRemoved() {
        // The planning-screen visibility preview reads sightRange(): it must reflect
        // that someone wearing headphones can't trust their own "Mirar" check as far,
        // the same penalty tickTraffic() already enforces — otherwise the preview
        // shows full visibility while the simulation silently requires a far smaller
        // gap than what it displays.
        val base=levels[2].copy(lanes=listOf(Lane(.7f,.15f,0f,"car_blue")),
            walkers=listOf(Walker("leo","Leo","headphones",.12f,"walker_rust")),
            tools=listOf("look","headphones_off"))
        val on=Simulation(base,plan(base,1,setOf("look")))
        val off=Simulation(base,plan(base,1,setOf("look","headphones_off")))
        assertTrue("Con audífonos puestos, el rango de confianza de Mirar debe ser menor",
            on.sightRange("leo")<off.sightRange("leo"))
    }
    @Test fun impulsiveWalkerSeesLessUnlessEscorted() {
        // tools overridden explicitly: "escort" doesn't exist in campaign.json's tool list yet
        // at this point (added in a later task) — the test must be self-contained.
        // No crosswalk/signal on purpose: with neither, the car never brakes for anyone,
        // so its motion is exactly the constant-speed, no-braking model "Mirar" itself
        // assumes when predicting a safe gap — no risk of the two disagreeing.
        val base=levels[2].copy(lanes=listOf(Lane(.7f,.37f,0f,"car_blue")),
            walkers=listOf(Walker("ana","Ana","impulsive",.12f,"walker_coral")),
            tools=listOf("look","escort"))
        val alone=finish(Simulation(base,plan(base,1,setOf("look"))))
        val escorted=finish(Simulation(base,plan(base,1,setOf("look","escort"))))
        assertEquals("An impulsive walker alone should misjudge this fast lane and get caught",
            Phase.RETRY,alone.phase)
        assertEquals("Escort compensates the penalty — same lane is safely crossable",
            Phase.WON,escorted.phase)
    }
    @Test fun level6RequiresWaitingForTrafficWithoutStoppingCars() {
        val level = levels.first { it.id == 6 }
        val sim = Simulation(level, plan(level, 1, setOf("look"), setOf("crosswalk")))
        while (sim.phase == Phase.RUNNING) {
            sim.tick()
            level.lanes.forEach { lane ->
                assertFalse("Cars must keep circulating in level 6", sim.isBraking(lane))
                assertTrue(sim.carSpeed(lane) > 0f)
            }
        }
        assertEquals(sim.reason, Phase.WON, sim.phase)
        assertFalse("signal" in level.tools)
    }

    @Test fun carsBrakeSoonerAndMoreVisibly() {
        val base=levels[2].copy(lanes=listOf(Lane(.7f,.15f,0f,"car_blue")),
            walkers=listOf(Walker("ana","Ana","impulsive",.12f,"walker_coral")),
            tools=listOf("look","crosswalk"))
        val sim=Simulation(base,plan(base,1,setOf("look"),setOf("crosswalk")))
        repeat(60) { sim.tick(.02f) } // 1.2s
        assertTrue("A 1.2s ya deberia estar frenando, no esperando",
            sim.isBraking(base.lanes.first()) || sim.carSpeed(base.lanes.first())<.15f)
    }
    @Test fun cardsAndLevelGeometryStayConsistent() {
        val catalog=tools.associateBy {it.id}
        for(l in levels) {
            assertTrue(l.tools.all {it in catalog});assertTrue(l.newTools.all {it in l.tools})
            assertEquals(3,l.sight.size);assertTrue(l.sight.all {it>0f && it<=1f})
            assertTrue(l.accessible.all {it in 0..2})
        }
        assertTrue(tools.none {it.id in setOf("wait","school","bump","officer")})
    }
    @Test fun budgetCountsPersonalCardsPerPersonAndStreetOnce() {
        val l=levels[4];val p=plan(l,1,setOf("look"),setOf("speed_zone"));assertEquals(5,p.cost(tools))
    }
    @Test fun lanesLackingLookOrEyeNeverWinEvenWhenTheWalkPhysicallySurvives() {
        for(l in levels.filter { !it.tutorial }) {
            for(c in 0..2) {
                val p = plan(l,c,emptySet())
                p.people.forEach { (id,a) ->
                val kind=l.walkers.first{it.id==id}.kind
                if(kind=="phone" && "phone_away" in l.tools) a.skills.add("phone_away")
                if(kind=="headphones" && "headphones_off" in l.tools) a.skills.add("headphones_off")
                if(kind=="impulsive" && "escort" in l.tools) a.skills.add("escort")
            }
                if("crosswalk" in l.tools) p.street.add("crosswalk")
                val sim = finish(Simulation(l,p))
                assertNotEquals("level=${l.id} crossing=$c won without look/eye", Phase.WON, sim.phase)
            }
        }
    }
    @Test fun twoWayLanesStillLetALookingWalkerThrough() {
        fun solved(levelId: Int, crossing: Int, street: Set<String> = emptySet()): Boolean {
            val l = levels.first { it.id == levelId }
            val p = plan(l, crossing, setOf("look"), street)
            p.people.forEach { (id,a) ->
                val kind=l.walkers.first{it.id==id}.kind
                if(kind=="phone" && "phone_away" in l.tools) a.skills.add("phone_away")
                if(kind=="headphones" && "headphones_off" in l.tools) a.skills.add("headphones_off")
                if(kind=="impulsive" && "escort" in l.tools) a.skills.add("escort")
            }
            return finish(Simulation(l, p)).phase == Phase.WON
        }
        assertTrue(solved(3, 0, setOf("crosswalk")))
        assertTrue(solved(3, 1, setOf("crosswalk")))
        assertTrue(solved(4, 2))
        assertTrue(solved(5, 0)); assertTrue(solved(5, 1)); assertTrue(solved(5, 2))
    }
    @Test fun noLevelOffersAToolThatCannotPossiblyMatterThere() {
        val failures = mutableListOf<String>()
        for(l in levels) {
            if("lighting" in l.tools && !l.night) failures.add("level=${l.id} offers lighting but night=false")
            if("visible" in l.tools && !l.night) failures.add("level=${l.id} offers visible but night=false")
            if("car_lights" in l.tools && !l.night) failures.add("level=${l.id} offers car_lights but night=false")
            if("escort" in l.tools && l.walkers.none { it.kind == "impulsive" }) failures.add("level=${l.id} offers escort but no impulsive walker")
            if("headphones_off" in l.tools && l.walkers.none { it.kind == "headphones" }) failures.add("level=${l.id} offers headphones_off but no headphones walker")
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }
    // A ficha nunca debe aparecer en la caja de herramientas de un nivel antes de que
    // algún nivel anterior (o el mismo) la presente por primera vez en `newTools` —
    // de lo contrario el jugador la encuentra sin ninguna explicación, la prueba sin
    // saber qué hace, y si ese nivel no depende de ella, parece no servir de nada.
    @Test fun noLevelOffersAToolBeforeItIsIntroduced() {
        val introduced = mutableSetOf("look")
        val failures = mutableListOf<String>()
        for(l in levels.sortedBy { it.id }) {
            val untaught = l.tools.toSet() - introduced - l.newTools.toSet()
            if(untaught.isNotEmpty()) failures.add("level=${l.id} offers $untaught before introducing it")
            introduced += l.newTools
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }
    @Test fun retiredStreetToolsAreGoneFromTheCampaign() {
        val retired = setOf("refuge", "signal")
        assertTrue(tools.none { it.id in retired })
        assertTrue(levels.none { level -> (level.tools + level.newTools + level.guideTools).any { it in retired } })
    }

    @Test fun crossingOutsideTheMarkedPathAtNightCausesACollisionEvenWithStreetUpgrades() {
        val level = levels.first { it.id == 9 }
        val street = setOf("car_lights", "speed_zone")
        val unmarked = finish(Simulation(level, plan(level, 0, emptySet(), street)))
        assertEquals(Phase.RETRY, unmarked.phase)
        assertEquals("ana", unmarked.collapsedWalkerId)
        assertTrue(unmarked.reason, unmarked.reason.contains("fuera del paso peatonal"))
        assertEquals(Phase.WON, finish(Simulation(level, plan(level, 1, emptySet(), street))).phase)
    }

    @Test fun lookingOutsideMarkedCrossingsWorksOnlyByDay() {
        val base = levels.first { it.id == 9 }
        val day = base.copy(night = false, tools = listOf("look"))
        val night = base.copy(tools = base.tools + "look")
        assertEquals(Phase.WON, finish(Simulation(day, plan(day, 0, setOf("look")))).phase)
        val sim = finish(Simulation(night, plan(night, 0, setOf("look"), setOf("car_lights", "speed_zone"))))
        assertEquals("ana", sim.collapsedWalkerId)
        assertTrue(sim.reason.contains("fuera del paso peatonal"))
    }

    @Test fun nightLevelsNeedBothStreetImprovementsWithoutPersonalVisionCards() {
        for (level in levels.filter { it.night }) {
            val crossings = when (level.id) {
                10 -> listOf(0, 2)
                else -> crossingsFor(level, 1)
            }
            val skills = level.walkers.filter { it.kind == "phone" }.associate { it.id to setOf("phone_away") }
            val street = if (level.walkers.any { it.kind == "wheelchair" }) setOf("crosswalk") else emptySet()
            fun run(vararg improvements: String): Simulation =
                finish(Simulation(level, planFor(level, 1, skills, street + improvements, crossings)))

            val both = run("car_lights", "speed_zone")
            assertEquals("Nivel ${level.id}: ${both.reason}", Phase.WON, both.phase)
            assertEquals("Nivel ${level.id}: solo luces", Phase.RETRY, run("car_lights").phase)
            assertEquals("Nivel ${level.id}: solo Bajar velocidad", Phase.RETRY, run("speed_zone").phase)
        }
    }
}
