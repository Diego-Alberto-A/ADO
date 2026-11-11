import XCTest

final class NavigationTests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws { continueAfterFailure=false; app=XCUIApplication(); app.launch() }
    private func tap(_ label: String) {
        let button=app.buttons.matching(NSPredicate(format:"label CONTAINS %@",label)).firstMatch
        // Long level briefings can put the action below the modal viewport.
        // Scroll the frontmost scroll view before asserting the button exists.
        for _ in 0..<3 {
            if button.waitForExistence(timeout:2) && button.isHittable { break }
            guard let scroll=app.scrollViews.allElementsBoundByIndex.last else { break }
            scroll.swipeUp()
        }
        XCTAssertTrue(button.waitForExistence(timeout:5),label)
        // Wait for the screen/modal animation to release input, rather than tapping through it.
        let ready=expectation(for:NSPredicate(format:"isHittable == true AND isEnabled == true"),evaluatedWith:button)
        wait(for:[ready],timeout:5); button.tap()
    }
    func testWorldsGameAndNestedSettings() {
        tap("AJUSTES"); XCTAssertTrue(app.staticTexts["A TU MANERA"].waitForExistence(timeout:5)); tap("LISTO")
        tap("JUGAR"); tap("MUNDO 2"); XCTAssertTrue(app.staticTexts["Campus de innovación"].waitForExistence(timeout:5))
        tap("MUNDO 1"); tap("Primeros pasos"); tap("PREPARAR EL PLAN")
        tap("LA CALLE"); tap("HABILIDADES"); tap("Pausa"); tap("AJUSTES"); tap("LISTO")
        XCTAssertTrue(app.staticTexts["PAUSA"].waitForExistence(timeout:5)); tap("CONTINUAR")
        tap("Pausa"); tap("VOLVER AL MAPA"); tap("INICIO")
        XCTAssertTrue(app.buttons["JUGAR"].waitForExistence(timeout:5))
    }
    func testWinRetryAndNextLevel() {
        tap("JUGAR"); tap("Primeros pasos"); tap("PREPARAR EL PLAN"); tap("Contacto"); tap("PONER EL PLAN EN MARCHA")
        XCTAssertTrue(app.staticTexts["¡BUEN CAMINO!"].waitForExistence(timeout:20))
        tap("VOLVER A INTENTAR")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format:"label CONTAINS %@","PONER EL PLAN EN MARCHA")).firstMatch.waitForExistence(timeout:5))
        tap("PONER EL PLAN EN MARCHA")
        XCTAssertTrue(app.staticTexts["¡BUEN CAMINO!"].waitForExistence(timeout:20))
        tap("SIGUIENTE CAMINO")
        XCTAssertTrue(app.staticTexts["Mira a ambos lados"].waitForExistence(timeout:5))
    }
}
