package de.jfricker.mypocketknife.tools.games.invaders

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kurze Retro-Töne, im Programm erzeugt (keine Sounddateien).
 * Je Ton ein statischer AudioTrack, der bei Bedarf von vorne abgespielt wird.
 */
class InvaderSound {

    private val tracks = mutableMapOf<String, AudioTrack>()

    init {
        add("shot", sweep(0.12f, 1400f, 300f, square = true, volume = 0.35f))
        add("hit", noise(0.22f, volume = 0.45f, smooth = 2))
        add("player", noise(0.8f, volume = 0.6f, smooth = 6))
        add("ufo", warble(0.45f))
        add("life", arpeggio(floatArrayOf(523f, 659f, 784f, 1047f), 0.07f))
        STEP_FREQ.forEachIndexed { i, f -> add("step$i", sweep(0.07f, f, f * 0.9f, square = true, volume = 0.4f)) }
    }

    fun play(sfx: Sfx, stepNote: Int) {
        val key = when (sfx) {
            Sfx.SHOT -> "shot"
            Sfx.INVADER_HIT -> "hit"
            Sfx.PLAYER_HIT -> "player"
            Sfx.UFO_HIT -> "ufo"
            Sfx.EXTRA_LIFE -> "life"
            Sfx.STEP -> "step${stepNote % STEP_FREQ.size}"
        }
        val t = tracks[key] ?: return
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

    // ------------------------------------------------------------------ Erzeugung

    private fun add(key: String, samples: ShortArray) {
        if (samples.isEmpty()) return
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

    private fun n(seconds: Float) = (seconds * RATE).toInt()

    private fun toShort(v: Float) = (v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()

    /** Ton, der von f0 nach f1 gleitet. */
    private fun sweep(seconds: Float, f0: Float, f1: Float, square: Boolean = false, volume: Float = 0.4f): ShortArray {
        val count = n(seconds)
        var phase = 0.0
        return ShortArray(count) { i ->
            val p = i.toFloat() / count
            val f = f0 + (f1 - f0) * p
            phase += 2 * PI * f / RATE
            val s = sin(phase).toFloat()
            val wave = if (square) (if (s >= 0) 1f else -1f) else s
            toShort(wave * volume * (1f - p))
        }
    }

    /** Rauschen mit Abklingen; smooth = Mittelung für tieferen "Knall". */
    private fun noise(seconds: Float, volume: Float, smooth: Int): ShortArray {
        val count = n(seconds)
        val rnd = Random(42)
        var acc = 0f
        return ShortArray(count) { i ->
            val p = i.toFloat() / count
            acc += (rnd.nextFloat() * 2f - 1f - acc) / smooth
            toShort(acc * volume * 2f * exp(-4f * p))
        }
    }

    private fun warble(seconds: Float): ShortArray {
        val count = n(seconds)
        var phase = 0.0
        return ShortArray(count) { i ->
            val t = i.toFloat() / RATE
            val f = 700f + 300f * sin(2 * PI * 18 * t).toFloat()
            phase += 2 * PI * f / RATE
            toShort(sin(phase).toFloat() * 0.4f * (1f - i.toFloat() / count))
        }
    }

    private fun arpeggio(freqs: FloatArray, each: Float): ShortArray =
        freqs.map { sweep(each, it, it, square = true, volume = 0.3f) }.reduce { a, b -> a + b }

    private companion object {
        const val RATE = 22050
        val STEP_FREQ = floatArrayOf(196f, 175f, 165f, 147f)
    }
}
