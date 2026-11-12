package com.example.crosswalker.audio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.crosswalker.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun playMusicStartsAndStopMusicStopsIt() {
        val engine = SoundEngine(context) { true }
        engine.playMusic(R.raw.music_menu)
        Thread.sleep(300)
        assertTrue(engine.isMusicPlaying())
        engine.stopMusic()
        assertFalse(engine.isMusicPlaying())
        engine.release()
    }

    @Test fun disabledEngineNeverStartsMusic() {
        val engine = SoundEngine(context) { false }
        engine.playMusic(R.raw.music_menu)
        Thread.sleep(300)
        assertFalse(engine.isMusicPlaying())
        engine.release()
    }

    @Test fun playOneShotReachesHalfwayCallback() {
        val engine = SoundEngine(context) { true }
        var halfwayHit = false
        engine.playOneShot(R.raw.music_win) { halfwayHit = true }
        // win.mp3 dura unos pocos segundos; 6s de margen alcanza para pasar su mitad.
        Thread.sleep(6000)
        assertTrue(halfwayHit)
        engine.release()
    }
}
