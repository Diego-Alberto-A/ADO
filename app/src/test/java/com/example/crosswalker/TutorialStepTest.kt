package com.example.crosswalker

import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialStepTest {
    // El nivel 1 real tiene 6 introLines (índices 0-5); los tests usan ese número explícito
    // para no acoplarse a un detalle de parseo de campaign.json en esta prueba pura.
    private val count = 6

    @Test fun firstFourLinesPointAtTheDialogueItself() {
        for (i in 0..1) assertEquals(TutorialStep("dialogue"), tutorialStepFor(i, count, lookChosen = false))
    }
    @Test fun pauseAndTokensLinesSpotlightThoseWidgetsButTapAdvancesDialogue() {
        assertEquals(TutorialStep("pause", "dialogue"), tutorialStepFor(2, count, lookChosen = false))
        assertEquals(TutorialStep("tokens", "dialogue"), tutorialStepFor(3, count, lookChosen = false))
    }
    @Test fun tabsLineSpotlightsTheSkillTab() {
        assertEquals(TutorialStep("tab_skill", "dialogue"), tutorialStepFor(4, count, lookChosen = false))
    }
    @Test fun lastLineIsTheCrossingActionItself() {
        assertEquals(TutorialStep("crossing"), tutorialStepFor(5, count, lookChosen = false))
    }
    @Test fun afterIntroLinesStepIsToolLookUntilChosen() {
        assertEquals(TutorialStep("tool_look"), tutorialStepFor(count, count, lookChosen = false))
    }
    @Test fun onceLookIsChosenStepIsLaunch() {
        assertEquals(TutorialStep("launch"), tutorialStepFor(count, count, lookChosen = true))
    }
}
