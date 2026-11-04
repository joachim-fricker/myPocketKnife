package de.jfricker.mypocketknife.tools.dice.maedn

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Kurzer "Plopp" beim Schlagen einer Figur, im Programm erzeugt (keine Sounddatei). */
class CaptureSound {

    private val track: AudioTrack? = runCatching {
        val samples = bonk()
        AudioTrack.Builder()
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
            .also { it.write(samples, 0, samples.size) }
    }.getOrNull()

    fun capture() {
        val t = track ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        runCatching { track?.release() }
    }

    /** Zwei kurze, abfallende Töne (ca. 0,25 s). */
    private fun bonk(): ShortArray {
        val count = (0.25f * RATE).toInt()
        var phase = 0.0
        return ShortArray(count) { i ->
            val p = i.toFloat() / count
            val f = if (p < 0.45f) 880f - 300f * (p / 0.45f) else 520f - 250f * ((p - 0.45f) / 0.55f)
            phase += 2 * PI * f / RATE
            val env = if (p < 0.45f) 1f - p / 0.45f * 0.5f else (1f - p) / 0.55f
            (sin(phase).toFloat() * 0.5f * env * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val RATE = 22050
    }
}
