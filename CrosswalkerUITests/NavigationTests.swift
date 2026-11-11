import XCTest

final class NavigationTests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws { continueAfterFailure=false; app=XCUIApplication(); app.launch() }
    override func tearDownWithError() throws {
        if testRun?.hasSucceeded == false {
            print("INTERACTION FAILURE HIERARCHY:\n" + app.debugDescription)
            let screenshot=XCTAttachment(screenshot:app.screenshot())
            screenshot.lifetime = .keepAlways; add(screenshot)
        }
    }
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
        XCTAssertTrue(app.buttons.matching(NSPredicate(format:"label CONTAINS %@","JUGAR")).firstMatch.waitForExistence(timeout:5))
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

    func testLevelCardRespondsAcrossItsVisibleWidth() {
        tap("JUGAR")
        let card=app.buttons["level-1"]
        XCTAssertTrue(card.waitForExistence(timeout:5))
        for x in [CGFloat(0.08),0.5,0.92] {
            card.coordinate(withNormalizedOffset:CGVector(dx:x,dy:0.5)).tap()
            XCTAssertTrue(app.staticTexts["NIVEL 1"].waitForExistence(timeout:5))
            // The backdrop dismisses only the briefing; it must not activate
            // the map underneath (including the return-home button).
            app.coordinate(withNormalizedOffset:CGVector(dx:0.98,dy:0.5)).tap()
            XCTAssertTrue(card.waitForExistence(timeout:5))
        }
    }

    func testImproveFailedPlanKeepsLevelAndCrossingThenAllowsEditing() {
        tap("JUGAR"); tap("Primeros pasos"); tap("PREPARAR EL PLAN"); tap("Der.")
        XCTAssertTrue(app.buttons["Der."].isSelected)
        tap("PONER EL PLAN EN MARCHA")
        XCTAssertTrue(app.staticTexts["OTRO PLAN, OTRA OPORTUNIDAD"].waitForExistence(timeout:30))
        let improve=app.buttons.matching(NSPredicate(format:"label CONTAINS %@","MEJORAR MI PLAN")).firstMatch
        XCTAssertTrue(improve.waitForExistence(timeout:5))
        // Use the padded part of the button, away from its text and the map
        // action underneath it. This previously activated the wrong action.
        improve.coordinate(withNormalizedOffset:CGVector(dx:0.12,dy:0.5)).tap()
        let start=app.buttons.matching(NSPredicate(format:"label CONTAINS %@","PONER EL PLAN EN MARCHA")).firstMatch
        XCTAssertTrue(start.waitForExistence(timeout:5))
        XCTAssertTrue(app.staticTexts["Primeros pasos"].exists)
        XCTAssertTrue(app.buttons["Der."].isSelected)
        tap("Centro"); XCTAssertTrue(app.buttons["Centro"].isSelected)
        tap("Contacto")
        let contact=app.buttons.matching(NSPredicate(format:"label CONTAINS %@","Contacto")).firstMatch
        XCTAssertEqual(contact.value as? String,"Seleccionado")
        tap("Pausa"); tap("CONTINUAR")
        XCTAssertTrue(start.waitForExistence(timeout:5))
    }

    func testPauseAndEmergencyButtonsRespondDuringSimulation() {
        tap("JUGAR"); tap("Primeros pasos"); tap("PREPARAR EL PLAN")
        tap("Mirar"); tap("Esperar"); tap("PONER EL PLAN EN MARCHA")
        tap("Pausa")
        XCTAssertTrue(app.staticTexts["PAUSA"].waitForExistence(timeout:5))
        tap("CONTINUAR"); tap("PARAR TRÁFICO")
        let used=app.buttons["AYUDA UTILIZADA"]
        XCTAssertTrue(used.waitForExistence(timeout:5))
        XCTAssertFalse(used.isEnabled)
        tap("Pausa"); tap("REINTENTAR")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format:"label CONTAINS %@","PONER EL PLAN EN MARCHA")).firstMatch.waitForExistence(timeout:5))
        XCTAssertTrue(app.buttons["Centro"].isSelected)
    }
}
