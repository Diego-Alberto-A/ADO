package com.example.crosswalker

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class CampaignUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun redesignedLevelsRenderAndExplainStreetCards() {
        val session=ViewModelProvider(ui.activity)[Session::class.java]
        for(level in session.repo.levels.filter { !it.tutorial }) {
            ui.runOnIdle {
                session.level=level;session.reset();session.screen="game"
            }
            ui.onNodeWithText("PONER EL PLAN EN MARCHA",substring=true).assertIsDisplayed()
        }
        ui.onNodeWithText("LA CALLE").performClick()
        ui.onAllNodesWithText("VER EFECTO").onFirst().performClick()
        ui.onNodeWithText("ENTENDIDO").assertIsDisplayed()
        ui.onNodeWithText("ENTENDIDO").performClick()
        ui.waitForIdle()
        val image=ui.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(ui.activity.cacheDir,"campaign-12.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
