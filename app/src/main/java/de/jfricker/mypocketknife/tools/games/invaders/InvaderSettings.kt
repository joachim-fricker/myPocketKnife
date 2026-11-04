package de.jfricker.mypocketknife.tools.games.invaders

import android.content.Context
import androidx.core.content.edit

enum class ControlMode(val title: String, val description: String) {
    DRAG("Finger ziehen", "Kanone folgt dem Finger, schiesst solange du berührst"),
    BUTTONS("Knöpfe", "◀ ▶ und Feuer-Knopf unten"),
}

data class InvaderSettings(
    val mode: ControlMode = ControlMode.DRAG,
    /** Immer schiessen, ohne Finger/Knopf. */
    val autoFire: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

/** Einstellungen dauerhaft in den SharedPreferences "invaders". */
class InvaderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("invaders", Context.MODE_PRIVATE)

    fun load() = InvaderSettings(
        mode = runCatching { ControlMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ControlMode.DRAG),
        autoFire = prefs.getBoolean("autoFire", false),
        sound = prefs.getBoolean("sound", true),
        vibration = prefs.getBoolean("vibration", true),
    )

    fun save(s: InvaderSettings) = prefs.edit {
        putString("mode", s.mode.name)
        putBoolean("autoFire", s.autoFire)
        putBoolean("sound", s.sound)
        putBoolean("vibration", s.vibration)
    }
}
