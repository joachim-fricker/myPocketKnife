package de.jfricker.mypocketknife.tools.incline

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.content.edit
import java.util.Locale

/** Piepton, Vibration und Sprachausgabe für die freihändige Messung. */
class MeasureFeedback(context: Context) {

    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.GERMAN }

    fun beep() {
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
    }

    fun vibrate() {
        vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun speak(text: String) {
        if (!ttsReady) return
        tts.language = Locale.GERMAN
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "incline")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        tone?.release()
    }
}

/**
 * Erkennt, wann das Handy ruhig liegt: alle Werte der letzten [windowMs]
 * liegen innerhalb von ±[tolerance]°.
 */
class StabilityDetector(private val windowMs: Long = 2000, private val tolerance: Double = 0.4) {
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var stableSince = 0L

    fun reset() {
        samples.clear()
        stableSince = 0L
    }

    /** Liefert den Mittelwert, sobald es lange genug ruhig war, sonst null. */
    fun add(time: Long, angle: Double): Double? {
        samples.addLast(time to angle)
        while (samples.isNotEmpty() && time - samples.first().first > windowMs) samples.removeFirst()
        val min = samples.minOf { it.second }
        val max = samples.maxOf { it.second }
        if (max - min > 2 * tolerance) {
            // Bewegung: nur die letzten Werte behalten, Zeit neu starten
            samples.clear()
            samples.addLast(time to angle)
            stableSince = time
            return null
        }
        if (stableSince == 0L) stableSince = time
        return if (time - stableSince >= windowMs) samples.map { it.second }.average() else null
    }

    /** 0..1: wie weit die Ruhezeit schon erreicht ist (für den Fortschrittsbalken). */
    fun progress(time: Long): Float =
        if (stableSince == 0L) 0f else ((time - stableSince).toFloat() / windowMs).coerceIn(0f, 1f)
}

class InclineOptions(context: Context) {
    private val prefs = context.getSharedPreferences("incline_options", Context.MODE_PRIVATE)

    var autoHold: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) = prefs.edit { putBoolean("auto", v) }

    var speak: Boolean
        get() = prefs.getBoolean("speak", true)
        set(v) = prefs.edit { putBoolean("speak", v) }
}
