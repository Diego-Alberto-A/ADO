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
    @Test fun campaignArchetypesHaveSafeSolutions() {
        for(i in 0..11) {
            val walkers=listOf(walker,Walker("leo","Leo","distracted",.1f,"walker_blue"),Walker("luz","Luz","slow",.075f,"walker_green")).take(minOf(3,1+i%4))
            val l=level(i>=8).copy(id=i+1,walkers=walkers,lanes=level().lanes.map {it.copy(speed=it.speed*(1+i*.035f),offset=it.offset+i*.07f)})
            val p=Plan(walkers.associate {it.id to Assignment(1,mutableSetOf(if(l.night)"visible" else "eye"))}.toMutableMap())
            assertEquals("Level ${i+1}",Phase.WON,finish(Simulation(l,p)).phase)
        }
    }
    @Test fun unprotectedTrafficCanTriggerFriendlyRetry() {assertEquals(Phase.RETRY,finish(Simulation(level().copy(lanes=listOf(Lane(.7f,0f,.7f,"car_coral"))),plan())).phase)}
    @Test fun centreSignalStopsTraffic() {val s=Simulation(level(),plan("signal"));val x=s.carX(s.level.lanes[0]);repeat(30){s.tick()};assertEquals(x,s.carX(s.level.lanes[0]),0f);assertEquals(Phase.WON,finish(s).phase)}
}
