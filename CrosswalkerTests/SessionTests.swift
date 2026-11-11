#if !SWIFT_PACKAGE
import XCTest
import SwiftUI
import UIKit
@testable import Crosswalker

@MainActor final class SessionTests: XCTestCase {
    private func session() throws -> Session {
        let defaults=try XCTUnwrap(UserDefaults(suiteName:"CrosswalkerSessionTests"))
        defaults.removePersistentDomain(forName:"CrosswalkerSessionTests")
        let repo=try GameRepository(defaults:defaults)
        return Session(repo:repo)
    }
    func testBudgetRejectsOverspendingButAllowsRemovingTools() throws {
        let s=try session(); s.enter(s.repo.levels[0])
        let officer=try XCTUnwrap(s.repo.tools.first { $0.id == "officer" })
        let eye=try XCTUnwrap(s.repo.tools.first { $0.id == "eye" })
        s.toggle(officer); s.toggle(eye)
        XCTAssertEqual(s.cost,4); XCTAssertTrue(s.message.hasPrefix("No quedan fichas"))
        s.toggle(officer); s.toggle(eye); XCTAssertEqual(s.cost,2)
    }
    func testPauseAndTransitionsFreezeSimulation() throws {
        let s=try session(); s.enter(s.repo.levels[0]); s.simulation=Simulation(s.level,s.plan)
        s.paused=true; s.tick(); XCTAssertEqual(s.simulation?.time,0)
        s.paused=false; s.transitioning=true; s.tick(); XCTAssertEqual(s.simulation?.time,0)
        s.transitioning=false; s.tick(); XCTAssertEqual(s.simulation?.time,Simulation.step)
    }
    func testRetryResetAndExitHaveOriginalSemantics() throws {
        let s=try session(); s.enter(s.repo.levels[4]); s.crossing(2)
        s.simulation=Simulation(s.level,s.plan); s.improvePlan()
        XCTAssertEqual(s.plan.people[s.selected]?.crossing,2)
        XCTAssertEqual(s.screen,.game); XCTAssertEqual(s.level.id,5)
        s.reset(); XCTAssertEqual(s.plan.people[s.selected]?.crossing,1)
        s.paused=true; s.navigate(.map); XCTAssertEqual(s.world,1); XCTAssertFalse(s.paused); XCTAssertNil(s.simulation)
    }

    func testFailedPlanReturnsToSameLevelWithAllAssignmentsIntact() throws {
        let s=try session(); s.sound=false; s.enter(s.repo.levels[0]); s.crossing(2)
        s.toggle(try XCTUnwrap(s.repo.tools.first { $0.id == "wait" }))
        s.simulation=Simulation(s.level,s.plan)
        for _ in 0..<1801 { s.tick() }
        XCTAssertEqual(s.simulation?.phase,.retry)
        XCTAssertTrue(s.hasModal)
        let levelID=s.level.id, selected=s.selected, cost=s.cost
        s.improvePlan()
        XCTAssertEqual(s.screen,.game); XCTAssertEqual(s.level.id,levelID)
        XCTAssertEqual(s.selected,selected); XCTAssertEqual(s.cost,cost)
        XCTAssertEqual(s.plan.people[selected]?.crossing,2)
        XCTAssertEqual(s.plan.people[selected]?.skills,Set(["wait"]))
        XCTAssertEqual(s.message,s.level.lesson)
        XCTAssertNil(s.simulation); XCTAssertFalse(s.hasModal)
    }
    func testWinPersistsBestScoreAndUnlocksNextLevel() throws {
        let s=try session(); s.enter(s.repo.levels[0]); s.toggle(try XCTUnwrap(s.repo.tools.first { $0.id == "eye" }))
        s.simulation=Simulation(s.level,s.plan)
        for _ in 0..<1801 { s.tick() }
        XCTAssertEqual(s.simulation?.phase,.won); XCTAssertEqual(s.repo.stars(1),3); XCTAssertTrue(s.repo.unlocked(2))
    }

    func testGameLoopStartsAfterInactiveLaunchAndResumesAfterBackgrounding() async throws {
        let s=try session(); s.sound=false; s.enter(s.repo.levels[0])
        // Exercise the real SwiftUI task, rather than calling tick() manually.
        let host=UIHostingController(rootView:GameRoot(session:s).environment(\.scenePhase,.inactive))
        let window=UIWindow(frame:UIScreen.main.bounds)
        window.rootViewController=host; window.isHidden=false
        defer { window.isHidden=true; window.rootViewController=nil }
        host.view.layoutIfNeeded()
        try await Task.sleep(nanoseconds:200_000_000)
        let sim=Simulation(s.level,s.plan); s.simulation=sim
        let carBefore=sim.carX(s.level.lanes[0]), walkerBefore=sim.people[0].y
        host.rootView=GameRoot(session:s).environment(\.scenePhase,.active)
        await eventually { sim.time >= 0.15 }
        XCTAssertNotEqual(sim.carX(s.level.lanes[0]),carBefore,"Traffic must move after launch")
        XCTAssertLessThan(sim.people[0].y,walkerBefore,"Pedestrians must move after launch")

        host.rootView=GameRoot(session:s).environment(\.scenePhase,.background)
        await eventually { s.paused }
        let pausedTime=sim.time
        try await Task.sleep(nanoseconds:200_000_000)
        XCTAssertEqual(sim.time,pausedTime)
        host.rootView=GameRoot(session:s).environment(\.scenePhase,.active)
        // Returning to the app preserves pause until the user taps Continue.
        try await Task.sleep(nanoseconds:200_000_000)
        XCTAssertEqual(sim.time,pausedTime)
        s.paused=false
        await eventually { sim.time > pausedTime }
    }

    private func eventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        for _ in 0..<100 {
            if condition() { return }
            do { try await Task.sleep(nanoseconds:20_000_000) } catch { break }
        }
        XCTFail("Game loop did not reach the expected state",file:file,line:line)
    }
}
#endif
