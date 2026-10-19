#if !SWIFT_PACKAGE
import XCTest
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
        s.simulation=Simulation(s.level,s.plan); s.simulation=nil
        XCTAssertEqual(s.plan.people[s.selected]?.crossing,2)
        s.reset(); XCTAssertEqual(s.plan.people[s.selected]?.crossing,1)
        s.paused=true; s.navigate(.map); XCTAssertEqual(s.world,1); XCTAssertFalse(s.paused); XCTAssertNil(s.simulation)
    }
    func testWinPersistsBestScoreAndUnlocksNextLevel() throws {
        let s=try session(); s.enter(s.repo.levels[0]); s.toggle(try XCTUnwrap(s.repo.tools.first { $0.id == "eye" }))
        s.simulation=Simulation(s.level,s.plan)
        for _ in 0..<1801 { s.tick() }
        XCTAssertEqual(s.simulation?.phase,.won); XCTAssertEqual(s.repo.stars(1),3); XCTAssertTrue(s.repo.unlocked(2))
    }
}
#endif
