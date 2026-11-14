package com.example.crosswalker

import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialStepTest {
    private val count = 11

    @Test fun narrationHighlightsTheElementBeingExplained() {
        val targets = listOf("dialogue", "dialogue", "dialogue", "dialogue", "pause", "tokens",
            "walkers", "tab_skill", "tab_street", "crossing")
        targets.forEachIndexed { index, target ->
            assertEquals(TutorialStep(target, "dialogue"), tutorialStepFor(index, count, false))
        }
    }
    @Test fun closedPanelPromptsOpeningAtAnyTutorialStep() {
        for (index in listOf(0, count - 1, count)) {
            assertEquals(TutorialStep("planning_panel"), tutorialStepFor(index, count, false, panelOpen = false))
        }
    }

    @Test fun mapLessonHighlightsTheBoardAndPointsToThePanelTab() {
        assertEquals(
            TutorialStep("board", "planning_panel"),
            tutorialStepFor(MAP_INTRO_INDEX, count, false, panelOpen = false)
        )
    }

    @Test fun finalIntroRequiresChoosingACrossing() {
        assertEquals(TutorialStep("crossing"), tutorialStepFor(count - 1, count, false))
    }
    @Test fun lookingIsRequiredBeforeLaunching() {
        assertEquals(TutorialStep("tool_look"), tutorialStepFor(count, count, false))
        assertEquals(TutorialStep("launch"), tutorialStepFor(count, count, true))
    }
}
