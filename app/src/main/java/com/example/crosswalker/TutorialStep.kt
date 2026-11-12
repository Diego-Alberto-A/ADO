package com.example.crosswalker

/** Qué se recorta del velo (`spotlightId`) y qué toque es válido ahora mismo (`tapId`). */
data class TutorialStep(val spotlightId: String, val tapId: String = spotlightId)

/**
 * El paso activo del tutorial del nivel 1, derivado de estado que ya existe en `Session`
 * (`introIndex`, si "look" ya está en las habilidades del peaton seleccionado) — no es un
 * contador paralelo. Siempre devuelve un paso concreto; es el llamador (Task 14, que ya sabe si
 * el tutorial completo de este nivel terminó vía `GameRepository.tutorialDone`) quien decide
 * cuándo no llamarla en absoluto y publicar `null` en el controlador.
 */
fun tutorialStepFor(introIndex: Int, introLineCount: Int, lookChosen: Boolean): TutorialStep = when {
    introIndex < introLineCount - 1 -> when (introIndex) {
        0, 1 -> TutorialStep("dialogue")
        2 -> TutorialStep("pause", "dialogue")
        3 -> TutorialStep("tokens", "dialogue")
        else -> TutorialStep("tab_skill", "dialogue")
    }
    introIndex == introLineCount - 1 -> TutorialStep("crossing")
    !lookChosen -> TutorialStep("tool_look")
    else -> TutorialStep("launch")
}
