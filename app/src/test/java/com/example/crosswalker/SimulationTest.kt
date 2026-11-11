package com.example.crosswalker.game

import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    private val walker=Walker("ana","Ana","impulsive",.12f,"walker_coral")
    private fun level(night:Boolean=false)=Level(1,0,"Test","",8,night,listOf(Lane(.28f,.15f,.35f,"car_coral"),Lane(.49f,-.13f,.85f,"car_blue"),Lane(.70f,.12f,.55f,"car_yellow")),listOf(walker))
    private fun plan(vararg skills:String)=Plan(mutableMapOf("ana" to Assignment(1,skills.toMutableSet())))
    private fun finish(s:Simulation):Simulation { repeat(1801) {s.tick()};return s }
    @Test fun samePlanProducesSameTrajectory() {
        val a=Simulation(level(),plan("look"));val b=Simulation(level(),plan("look"))
        repeat(1800) {a.tick();b.tick();assertEquals(a.people.first().y,b.people.first().y,0f);assertEquals(a.phase,b.phase);assertEquals(a.carX(a.level.lanes[0]),b.carX(b.level.lanes[0]),0f)}
    }
    @Test fun lookingFindsASafeGap() {assertEquals(Phase.WON,finish(Simulation(level(),plan("look"))).phase)}
    @Test fun eyeContactProtectsInDaylight() {assertEquals(Phase.WON,finish(Simulation(level(),plan("eye"))).phase)}
    @Test fun reflectorProtectsAtNight() {assertEquals(Phase.WON,finish(Simulation(level(true),plan("visible"))).phase)}
    @Test fun emergencyIsSingleUseAndCapsStars() {
        val s=Simulation(level(),plan("eye"));s.emergency();repeat(80){s.tick()};assertEquals(0f,s.emergencyRemaining,0f);s.emergency();s.tick();assertEquals(0f,s.emergencyRemaining,0f);finish(s);assertEquals(1,s.stars(1))
    }
    @Test fun waitHoldsAtCurb() {val s=Simulation(level(),plan("wait"));repeat(50){s.tick()};assertEquals(.91f,s.people[0].y,0f)}
    @Test fun simulationOwnsSnapshotOfPlan() {val p=plan("look");val s=Simulation(level(),p);p.people.getValue("ana").skills.clear();assertTrue("look" in s.plan.people.getValue("ana").skills)}
    @Test fun scoresRewardEfficiency() {val s=finish(Simulation(level(),plan("eye")));assertEquals(3,s.stars(2));assertEquals(2,s.stars(8))}
    @Test fun budgetCountsPerPersonAndStreetOnce() {val p=plan("look");p.people["leo"]=Assignment(0,mutableSetOf("look"));p.street.add("school");assertEquals(4,p.cost(listOf(Tool("look","","",1,"","skill"),Tool("school","","",2,"","street"))))}
    @Test fun allLevelsHaveASafeSolutionWithTheirAvailableTools() {
        val root=org.json.JSONObject(java.io.File("src/main/assets/campaign.json").readText())
        val levels=root.getJSONArray("levels").let {a->List(a.length()) {parseLevel(a.getJSONObject(it))}}
        for(level in levels) {
            // The obvious safe plan, restricted to what the level actually offers.
            val wanted=setOf("look",if(level.night) "visible" else "eye","phone_away")
            val people=level.walkers.associate {w->w.id to Assignment(1,wanted.filter {it in level.tools}.toMutableSet())}.toMutableMap()
            val street=setOf("crosswalk").filter {it in level.tools}.toMutableSet()
            val s=Simulation(level,Plan(people,street));repeat(1801) {s.tick()}
            assertEquals("Level ${level.id} (${level.title}): ${s.reason}",Phase.WON,s.phase)
        }
    }
    @Test fun unprotectedTrafficCanTriggerFriendlyRetry() {assertEquals(Phase.RETRY,finish(Simulation(level().copy(lanes=listOf(Lane(.7f,0f,.7f,"car_coral"))),plan())).phase)}
    @Test fun centreSignalStopsTraffic() {val s=Simulation(level(),plan("signal"));val x=s.carX(s.level.lanes[0]);repeat(30){s.tick()};assertEquals(x,s.carX(s.level.lanes[0]),0f);assertEquals(Phase.WON,finish(s).phase)}
    @Test fun wheelchairNeedsCrosswalkForFullSpeed() {
        val rider=Walker("mateo","Mateo","wheelchair",.12f,"walker_chair")
        val withRamp=finish(Simulation(level().copy(walkers=listOf(rider)),Plan(mutableMapOf("mateo" to Assignment(1,mutableSetOf("eye"))),mutableSetOf("crosswalk"))))
        val withoutRamp=finish(Simulation(level().copy(walkers=listOf(rider)),Plan(mutableMapOf("mateo" to Assignment(1,mutableSetOf("eye"))))))
        assertEquals(Phase.WON,withRamp.phase);assertEquals(Phase.WON,withoutRamp.phase)
        assertTrue(withoutRamp.time>withRamp.time)
    }
    @Test fun phoneKindGetsNoBenefitFromLookAlone() {
        val teen=Walker("tomas","Tomás","phone",.12f,"walker_slate")
        val withLookOnly=Simulation(level().copy(walkers=listOf(teen)),Plan(mutableMapOf("tomas" to Assignment(1,mutableSetOf("look")))))
        val withNoSkills=Simulation(level().copy(walkers=listOf(teen)),Plan(mutableMapOf("tomas" to Assignment(1,mutableSetOf()))))
        repeat(1800) {withLookOnly.tick();withNoSkills.tick()}
        assertEquals(withNoSkills.phase,withLookOnly.phase);assertEquals(withNoSkills.people[0].y,withLookOnly.people[0].y,0f)
    }
    @Test fun phoneAwayRestoresLooksProtection() {
        val teen=Walker("tomas","Tomás","phone",.12f,"walker_slate")
        val protected=finish(Simulation(level().copy(walkers=listOf(teen)),Plan(mutableMapOf("tomas" to Assignment(1,mutableSetOf("look","phone_away"))))))
        assertEquals(Phase.WON,protected.phase)
    }
}
