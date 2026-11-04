package de.jfricker.mypocketknife.tools.games.asteroids

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Retro-Töne für Asteroids, im Programm erzeugt (keine Sounddateien). */
class AsteroidsSound {

    private val tracks = mutableMapOf<AsSfx, AudioTrack>()

    init {
        add(AsSfx.FIRE, sweep(0.10f, 1500f, 500f, square = true, volume = 0.25f))
        add(AsSfx.BANG_LARGE, noise(0.55f, volume = 0.7f, smooth = 8))
        add(AsSfx.BANG_MEDIUM, noise(0.38f, volume = 0.55f, smooth = 5))
        add(AsSfx.BANG_SMALL, noise(0.22f, volume = 0.45f, smooth = 3))
        add(AsSfx.SHIP_EXPLODE, noise(1.0f, volume = 0.7f, smooth = 10))
        add(AsSfx.EXTRA_LIFE, sweep(0.08f, 1000f, 1000f, square = true, volume = 0.3f) + sweep(0.08f, 1300f, 1300f, square = true, volume = 0.3f) + sweep(0.12f, 1600f, 1600f, square = true, volume = 0.3f))
        add(AsSfx.SAUCER_FIRE, sweep(0.10f, 900f, 400f, square = true, volume = 0.2f))
        add(AsSfx.BEAT_HIGH, sweep(0.09f, 110f, 95f, square = true, volume = 0.45f))
        add(AsSfx.BEAT_LOW, sweep(0.09f, 90f, 78f, square = true, volume = 0.45f))
        add(AsSfx.HYPER, sweep(0.25f, 300f, 1800f, square = false, volume = 0.35f))
        add(AsSfx.THRUST, noise(0.11f, volume = 0.18f, smooth = 12))
    }

    fun play(sfx: AsSfx) {
        val t = tracks[sfx] ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun add(key: AsSfx, samples: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            tracks[key] = track
        }
    }

    private fun toShort(v: Float) = (v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()

    private fun sweep(seconds: Float, f0: Float, f1: Float, square: Boolean, volume: Float): ShortArray {
        val count = (seconds * RATE).toInt()
        var phase = 0.0
        return ShortArray(count) { i ->
            val p = i.toFloat() / count
            phase += 2 * PI * (f0 + (f1 - f0) * p) / RATE
            val s = sin(phase).toFloat()
            toShort((if (square) (if (s >= 0) 1f else -1f) else s) * volume * (1f - p * 0.8f))
        }
    }

    private fun noise(seconds: Float, volume: Float, smooth: Int): ShortArray {
        val count = (seconds * RATE).toInt()
        val rnd = Random(7)
        var acc = 0f
        return ShortArray(count) { i ->
            acc += (rnd.nextFloat() * 2f - 1f - acc) / smooth
            toShort(acc * volume * 2.5f * exp(-3.5f * i.toFloat() / count))
        }
    }

    private companion object {
        const val RATE = 22050
    }
}
