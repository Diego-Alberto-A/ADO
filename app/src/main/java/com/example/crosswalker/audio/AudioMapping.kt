package com.example.crosswalker.audio

import androidx.annotation.RawRes
import com.example.crosswalker.R

/** Qué música de fondo corresponde a esta pantalla (null = no cambiar nada). */
@RawRes fun musicForScreen(screen: String, world: Int): Int? = when (screen) {
    "home", "map" -> R.raw.music_menu
    "game" -> when (world) {
        0 -> R.raw.music_world1
        1 -> R.raw.music_world2
        else -> R.raw.music_world3
    }
    else -> null
}
