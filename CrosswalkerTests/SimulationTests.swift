import XCTest
#if SWIFT_PACKAGE
@testable import CrosswalkerCore
#else
@testable import Crosswalker
#endif

final class SimulationTests: XCTestCase {
    private var bundle: Bundle {
        #if SWIFT_PACKAGE
        return .module
        #else
        return Bundle(for: Self.self)
        #endif
    }
    private let walker = Walker(id:"ana",name:"Ana",kind:"impulsive",speed:0.12,sprite:"walker_coral")
    private func level(night: Bool = false, lanes: [Lane]? = nil) -> Level {
        Level(id:1,world:0,title:"Test",lesson:"",budget:8,night:night,lanes:lanes ?? [Lane(y:0.28,speed:0.15,offset:0.35,car:"car_coral"),Lane(y:0.49,speed:-0.13,offset:0.85,car:"car_blue"),Lane(y:0.7,speed:0.12,offset:0.55,car:"car_yellow")],walkers:[walker])
    }
    private func plan(_ skills: String...) -> Plan { Plan(people:["ana":Assignment(crossing:1,skills:Set(skills))]) }
    @discardableResult private func finish(_ s: Simulation) -> Simulation { for _ in 0..<1801 { s.tick() }; return s }
    func testSamePlanProducesSameTrajectory() {
        let a=Simulation(level(),plan("look")), b=Simulation(level(),plan("look"))
        for _ in 0..<1800 { a.tick(); b.tick(); XCTAssertEqual(a.people[0].y,b.people[0].y); XCTAssertEqual(a.phase,b.phase); XCTAssertEqual(a.carX(a.level.lanes[0]),b.carX(b.level.lanes[0])) }
    }
    func testLookingFindsSafeGap() { XCTAssertEqual(finish(Simulation(level(),plan("look"))).phase,.won) }
    func testEyeContactProtectsInDaylight() { XCTAssertEqual(finish(Simulation(level(),plan("eye"))).phase,.won) }
    func testReflectorProtectsAtNight() { XCTAssertEqual(finish(Simulation(level(night:true),plan("visible"))).phase,.won) }
    func testEmergencyIsSingleUseAndCapsStars() {
        let s=Simulation(level(),plan("eye")); s.emergency()
        let x=s.carX(s.level.lanes[0]); s.tick(); XCTAssertEqual(s.carX(s.level.lanes[0]),x)
        for _ in 0..<80 { s.tick() }; XCTAssertEqual(s.emergencyRemaining,0)
        s.emergency(); s.tick(); XCTAssertEqual(s.emergencyRemaining,0); finish(s); XCTAssertEqual(s.stars(1),1)
    }
    func testWaitHoldsAtCurb() { let s=Simulation(level(),plan("wait")); for _ in 0..<50 { s.tick() }; XCTAssertEqual(s.people[0].y,0.91) }
    func testSimulationOwnsSnapshot() { var p=plan("look"); let s=Simulation(level(),p); p.people["ana"]?.skills=[]; XCTAssertTrue(s.plan.people["ana"]!.skills.contains("look")) }
    func testScoresRewardEfficiency() { let s=finish(Simulation(level(),plan("eye"))); XCTAssertEqual(s.stars(2),3); XCTAssertEqual(s.stars(8),2) }
    func testBudgetCountsPerPersonAndStreetOnce() {
        var p=plan("look"); p.people["leo"]=Assignment(crossing:0,skills:["look"]); p.street.insert("school")
        XCTAssertEqual(p.cost([Tool(id:"look",label:"",icon:"",cost:1,description:"",category:"skill"),Tool(id:"school",label:"",icon:"",cost:2,description:"",category:"street")]),4)
    }
    func testUnprotectedTrafficRetries() { XCTAssertEqual(finish(Simulation(level(lanes:[Lane(y:0.7,speed:0,offset:0.7,car:"car_coral")]),plan())).phase,.retry) }
    func testCentreSignalStopsTraffic() {
        let s=Simulation(level(),plan("signal")), x=safeCarPosition()
        for _ in 0..<30 { s.tick() }; XCTAssertEqual(s.carX(s.level.lanes[0]),x); XCTAssertEqual(finish(s).phase,.won)
    }
    private func safeCarPosition() -> Float { Simulation(level(),plan("signal")).carX(level().lanes[0]) }
    func testCampaignHasAffordableSafeSolutions() throws {
        let campaign=try loadCampaign()
        XCTAssertEqual(campaign.levels.count,12); XCTAssertEqual(campaign.worlds.count,3); XCTAssertEqual(campaign.tools.count,9)
        for l in campaign.levels {
            let p=Plan(people:Dictionary(uniqueKeysWithValues:l.walkers.map { ($0.id,Assignment(crossing:1,skills:[l.night ? "visible" : "eye"])) }))
            XCTAssertLessThanOrEqual(p.cost(campaign.tools),l.budget)
            XCTAssertEqual(finish(Simulation(l,p)).phase,.won,"Level \(l.id)")
        }
    }
    private func loadCampaign() throws -> Campaign {
        let url=try XCTUnwrap(bundle.url(forResource:"campaign",withExtension:"json",subdirectory:"Fixtures"))
        return try JSONDecoder().decode(Campaign.self,from:Data(contentsOf:url))
    }
    // These traces are produced by compiling and running the untouched Kotlin Simulation.kt.
    func testKotlinReferenceTrajectories() throws {
        let campaign=try loadCampaign()
        let url=try XCTUnwrap(bundle.url(forResource:"trajectories",withExtension:"json",subdirectory:"Fixtures"))
        let traces=try JSONDecoder().decode([Trace].self,from:Data(contentsOf:url))
        XCTAssertEqual(traces.count,360)
        for trace in traces {
            let l=campaign.levels[trace.level-1]
            let p=Plan(people:Dictionary(uniqueKeysWithValues:l.walkers.map { ($0.id,Assignment(crossing:trace.crossing,skills:Set(trace.skills))) }),street:Set(trace.street))
            let s=Simulation(l,p)
            if trace.emergency { s.emergency() }
            for sample in trace.samples {
                while s.time < sample.time - 0.001 && s.phase == .running { s.tick() }
                XCTAssertEqual(String(describing:s.phase),sample.phase.lowercased(),"Level \(l.id), \(trace.skills), t=\(sample.time)")
                XCTAssertEqual(s.time,sample.time,accuracy:0.00001)
                for (index,person) in s.people.enumerated() { XCTAssertEqual(person.y,sample.y[index],accuracy:0.00001); XCTAssertEqual(person.done,sample.done[index]) }
                for (index,lane) in l.lanes.enumerated() { XCTAssertEqual(s.carX(lane),sample.cars[index],accuracy:0.00001) }
            }
            XCTAssertEqual(s.stars(p.cost(campaign.tools)),trace.stars)
        }
    }
    private struct Trace: Decodable {
        let level:Int; let crossing:Int; let skills:[String]; let street:[String]; let emergency:Bool; let stars:Int; let samples:[Sample]
    }
    private struct Sample: Decodable { let time:Float; let phase:String; let y:[Float]; let done:[Bool]; let cars:[Float] }
}
