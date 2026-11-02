package de.jfricker.mypocketknife

/**
 * Ein Werkzeug kann sich hier für die Lautstärketasten registrieren.
 * Ist niemand registriert, regeln die Tasten wie gewohnt die Lautstärke.
 */
object HardwareKeys {
    var onVolumeKey: (() -> Unit)? = null
}
