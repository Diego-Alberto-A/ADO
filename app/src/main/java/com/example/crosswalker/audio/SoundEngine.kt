package com.example.crosswalker.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import com.example.crosswalker.R

/**
 * Música de fondo (una pista a la vez, sin crossfade), voz de diálogo (pista aparte, en bucle) y
 * SFX cortos vía SoundPool. Todo respeta `enabled()`, que el llamador conecta al ajuste de
 * Sonidos. No hay abstracción de prueba: es una envoltura fina de MediaPlayer/SoundPool, igual que
 * el ToneGenerator que reemplaza para música y SFX (los blips de dialogo por letra se quedan en
 * ToneGenerator, ver Dialogue.kt).
 */
class SoundEngine(private val context: Context, private val enabled: () -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private var music: MediaPlayer? = null
    private var voice: MediaPlayer? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    // Los SFX cortos se cargan una vez y quedan listos para reproducirse sin demora.
    private val sfxIds = listOf(R.raw.sfx_button, R.raw.sfx_enter_level, R.raw.sfx_collapse)
        .associateWith { pool.load(context, it, 1) }

    fun isMusicPlaying(): Boolean = music?.isPlaying == true

    fun playMusic(@RawRes resId: Int, loop: Boolean = true) {
        stopMusic()
        if (!enabled()) return
        music = MediaPlayer.create(context, resId)?.apply { isLooping = loop; start() }
    }

    fun stopMusic() { music?.release(); music = null }

    fun playSfx(@RawRes resId: Int) {
        if (!enabled()) return
        sfxIds[resId]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    /**
     * Un `MediaPlayer` propio (no el de la música de fondo), con callbacks opcionales a mitad de
     * su duración y al terminar. Si el sonido está apagado (o el `MediaPlayer` no pudo crearse),
     * ambos callbacks se disparan de inmediato: quien los use para decidir cuándo mostrar algo en
     * pantalla (p. ej. un modal) nunca se queda esperando una música que nunca va a sonar.
     */
    fun playOneShot(@RawRes resId: Int, onHalfway: (() -> Unit)? = null, onComplete: (() -> Unit)? = null): MediaPlayer? {
        if (!enabled()) { onHalfway?.invoke(); onComplete?.invoke(); return null }
        val player = MediaPlayer.create(context, resId)
        if (player == null) { onHalfway?.invoke(); onComplete?.invoke(); return null }
        player.start()
        if (onHalfway != null) handler.postDelayed({ onHalfway() }, (player.duration / 2).toLong())
        if (onComplete != null) player.setOnCompletionListener { onComplete() }
        return player
    }

    fun loopVoice(@RawRes resId: Int) {
        stopVoice()
        if (!enabled()) return
        voice = MediaPlayer.create(context, resId)?.apply { isLooping = true; start() }
    }

    fun stopVoice() { voice?.release(); voice = null }

    fun release() { stopMusic(); stopVoice(); pool.release() }
}
