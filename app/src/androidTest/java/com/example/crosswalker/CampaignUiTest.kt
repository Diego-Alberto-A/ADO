package com.example.crosswalker

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class CampaignUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun assigningAnOccupiedCrossingSwapsBothWalkers() {
        val session = ViewModelProvider(ui.activity)[Session::class.java]
        ui.runOnIdle {
            session.level = session.repo.levels.first { it.id == 5 }
            session.reset()
            session.screen = "game"
            org.junit.Assert.assertEquals(1, session.plan.people.getValue("ana").crossing)
            org.junit.Assert.assertEquals(0, session.plan.people.getValue("tomas").crossing)
        }
        ui.onNodeWithText("ABRIR PLAN").performClick()
        ui.onNodeWithText("Izq.").performClick()
        ui.runOnIdle {
            org.junit.Assert.assertEquals(0, session.plan.people.getValue("ana").crossing)
            org.junit.Assert.assertEquals(1, session.plan.people.getValue("tomas").crossing)
        }
    }

    @Test fun removingHeadphonesUpdatesLeosPortrait() {
        val session = ViewModelProvider(ui.activity)[Session::class.java]
        ui.runOnIdle {
            session.level = session.repo.levels.first { it.id == 4 }
            session.reset()
            session.screen = "game"
        }

        ui.onNodeWithContentDescription("con audífonos", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ABRIR PLAN").performClick()
        val portraits = ui.onAllNodesWithContentDescription("con audífonos", substring = true)
        portraits.onFirst().assertIsDisplayed()
        val initialPortraits = portraits.fetchSemanticsNodes().size
        val tool = ui.onNodeWithText("QUITAR AUDÍFONOS")
        tool.performClick()
        portraits.assertCountEquals(0)
        tool.performClick()
        portraits.assertCountEquals(initialPortraits)
    }

    @Test fun mateoExplainsRampaBeforeThePanelOpens() {
        val session = ViewModelProvider(ui.activity)[Session::class.java]
        ui.runOnIdle {
            session.level = session.repo.levels.first { it.id == 3 }
            session.reset()
            session.screen = "game"
        }

        val tapHint = hasContentDescription("Toca para revelar el texto") or hasContentDescription("Toca para avanzar")
        ui.onNode(tapHint, useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithContentDescription("Mi silla necesita una Rampa", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ABRIR PLAN").performClick()
        ui.onNodeWithText("LA CALLE").performClick()
        ui.onNodeWithText("RAMPA").performClick()

        ui.runOnIdle {
            org.junit.Assert.assertTrue("crosswalk" in session.plan.street)
            org.junit.Assert.assertEquals("crosswalk", session.mechanicGuide?.id)
            org.junit.Assert.assertEquals(0, session.introIndex)
        }
        val firstPage = ui.onNodeWithContentDescription("Mi silla necesita una Rampa", substring = true)
        firstPage.assertIsDisplayed()
        ui.onNode(tapHint, useUnmergedTree = true).assertIsDisplayed()
        firstPage.performClick()
        if (session.introIndex == 0) firstPage.performClick()
        ui.onNodeWithContentDescription("Elige Izquierda o Centro para mí", substring = true).assertIsDisplayed()
    }

    @Test fun redesignedLevelsRenderAndExplainStreetCards() {
        val session=ViewModelProvider(ui.activity)[Session::class.java]
        for(level in session.repo.levels.filter { !it.tutorial }) {
            ui.runOnIdle {
                session.level=level;session.reset();session.screen="game"
            }
            ui.onNodeWithText("PONER EL PLAN EN MARCHA",substring=true).assertIsDisplayed()
        }
        val board = ui.onNodeWithContentDescription("Nivel 12", substring = true)
        val boardBounds = board.getBoundsInRoot()
        ui.onNodeWithText("LA CALLE").assertDoesNotExist()
        ui.onNodeWithText("ABRIR PLAN", substring = true).performClick()
        org.junit.Assert.assertEquals(boardBounds, board.getBoundsInRoot())
        ui.onNodeWithText("LA CALLE").performClick()
        ui.waitForIdle()
        val streetBefore = session.plan.street.toSet()
        ui.onAllNodesWithText("¿Para qué sirve?").onFirst().performClick()
        org.junit.Assert.assertEquals(streetBefore, session.plan.street)
        ui.onNodeWithText("ENTENDIDO").assertIsDisplayed()
        ui.onNodeWithText("ENTENDIDO").performClick()
        ui.waitForIdle()
        val image=ui.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(ui.activity.cacheDir,"campaign-12.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
