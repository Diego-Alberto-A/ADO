package com.example.crosswalker.audio

import com.example.crosswalker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMappingTest {
    @Test fun menuScreensPlayMenuSongRegardlessOfWorld() {
        assertEquals(R.raw.music_menu, musicForScreen("home", world = 0))
        assertEquals(R.raw.music_menu, musicForScreen("map", world = 2))
    }
    @Test fun gameScreenPlaysItsWorldsSong() {
        assertEquals(R.raw.music_world1, musicForScreen("game", world = 0))
        assertEquals(R.raw.music_world2, musicForScreen("game", world = 1))
        assertEquals(R.raw.music_world3, musicForScreen("game", world = 2))
    }
    @Test fun unknownScreenPlaysNothing() {
        assertNull(musicForScreen("settings", world = 0))
    }
}
