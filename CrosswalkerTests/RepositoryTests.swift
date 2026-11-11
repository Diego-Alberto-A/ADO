import XCTest
#if SWIFT_PACKAGE
@testable import CrosswalkerCore
#else
@testable import Crosswalker
#endif

final class RepositoryTests: XCTestCase {
    func testProgressSettingsAndUnlocksPersistWithoutLosingBestScore() throws {
        let suite="CrosswalkerTests.\(UUID().uuidString)"
        let defaults=try XCTUnwrap(UserDefaults(suiteName:suite))
        defer { defaults.removePersistentDomain(forName:suite) }
        #if SWIFT_PACKAGE
        let root=Bundle.module
        #else
        let root=Bundle(for:Self.self)
        #endif
        let url=try XCTUnwrap(root.url(forResource:"campaign",withExtension:"json",subdirectory:"Fixtures"))
        let campaign=try JSONDecoder().decode(Campaign.self,from:Data(contentsOf:url))
        let first=GameRepository(campaign:campaign,defaults:defaults)
        XCTAssertTrue(first.sound); XCTAssertTrue(first.guidance); XCTAssertTrue(first.unlocked(1)); XCTAssertFalse(first.unlocked(2))
        first.save(1,stars:3); first.save(1,stars:1); first.sound=false; first.guidance=false
        let second=GameRepository(campaign:campaign,defaults:defaults)
        XCTAssertEqual(second.stars(1),3); XCTAssertTrue(second.unlocked(2)); XCTAssertFalse(second.unlocked(3)); XCTAssertFalse(second.sound); XCTAssertFalse(second.guidance)
    }
}
