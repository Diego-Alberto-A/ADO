package com.example.crosswalker

import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialStepTest {
    private val count = 11

    @Test fun narrationHighlightsTheElementBeingExplained() {
        val targets = listOf("dialogue", "dialogue", "board", "board", "pause", "tokens",
            "walkers", "tab_skill", "tab_street", "crossing")
        targets.forEachIndexed { index, target ->
            assertEquals(TutorialStep(target, "dialogue"), tutorialStepFor(index, count, false))
        }
    }
    @Test fun finalIntroRequiresChoosingACrossing() {
        assertEquals(TutorialStep("crossing"), tutorialStepFor(count - 1, count, false))
    }
    @Test fun lookingIsRequiredBeforeLaunching() {
        assertEquals(TutorialStep("tool_look"), tutorialStepFor(count, count, false))
        assertEquals(TutorialStep("launch"), tutorialStepFor(count, count, true))
    }
}
