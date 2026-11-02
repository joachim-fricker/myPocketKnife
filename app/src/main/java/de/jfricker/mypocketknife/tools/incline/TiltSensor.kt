package de.jfricker.mypocketknife.tools.incline

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Liest den Schwerkraftvektor (TYPE_GRAVITY, sonst Beschleunigungssensor mit Tiefpass)
 * und rechnet ihn in Neigungswinkel um.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = sensor != null

    private val g = FloatArray(3)
    private var initialized = false
    var onGravity: ((FloatArray) -> Unit)? = null

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        // Glätten, damit die Anzeige ruhig steht
        val alpha = 0.08f
        for (i in 0..2) {
            g[i] = if (initialized) g[i] + alpha * (event.values[i] - g[i]) else event.values[i]
        }
        initialized = true
        onGravity?.invoke(g.copyOf())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Kalibrierung, dauerhaft gespeichert. */
class TiltCalibration(context: Context) {
    private val prefs = context.getSharedPreferences("incline", Context.MODE_PRIVATE)

    /** Referenz-Schwerkraftrichtung auf ebener Fläche (Einheitsvektor). */
    var flatRef: FloatArray
        get() = floatArrayOf(prefs.getFloat("rx", 0f), prefs.getFloat("ry", 0f), prefs.getFloat("rz", 1f))
        set(v) = prefs.edit { putFloat("rx", v[0]); putFloat("ry", v[1]); putFloat("rz", v[2]) }

    /** Nullpunktkorrektur der Längskante in Grad. */
    var edgeOffset: Float
        get() = prefs.getFloat("edge", 0f)
        set(v) = prefs.edit { putFloat("edge", v) }

    fun reset() = prefs.edit { clear() }
}

object TiltMath {
    private fun norm(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    fun unit(v: FloatArray): FloatArray {
        val n = norm(v).takeIf { it > 0f } ?: 1f
        return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    /**
     * Auflegen: Handy liegt flach auf dem Hang (oder auf dem Skistock).
     * Winkel zwischen aktueller Schwerkraft und der kalibrierten "flach"-Richtung.
     * Display oben oder unten ist egal.
     */
    fun surfaceAngle(g: FloatArray, ref: FloatArray): Double {
        val u = unit(g)
        val r = unit(ref)
        val dot = abs(u[0] * r[0] + u[1] * r[1] + u[2] * r[2]).coerceIn(0f, 1f)
        return Math.toDegrees(acos(dot.toDouble()))
    }

    /**
     * Anpeilen: Längskante des Handys entlang der Falllinie ausrichten.
     * Winkel der Längsachse gegenüber der Horizontalen.
     */
    fun edgeAngle(g: FloatArray, offset: Float): Double {
        val horiz = sqrt(g[0] * g[0] + g[2] * g[2])
        val a = Math.toDegrees(atan2(abs(g[1]).toDouble(), horiz.toDouble()))
        return (a - offset).coerceAtLeast(0.0)
    }

    /** Roher Längskantenwinkel (für die Kalibrierung). */
    fun rawEdge(g: FloatArray): Float = edgeAngle(g, 0f).toFloat()
}

/** Steilheitsklassen nach SLF. */
enum class SteepnessClass(val label: String, val from: Double) {
    MODERATE("mässig steil", 0.0),
    STEEP("steil", 30.0),
    VERY_STEEP("sehr steil", 35.0),
    EXTREME("extrem steil", 40.0);

    companion object {
        fun of(angle: Double): SteepnessClass = entries.last { angle >= it.from }
    }
}
