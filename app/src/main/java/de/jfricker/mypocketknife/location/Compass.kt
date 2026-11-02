package de.jfricker.mypocketknife.location

/** Himmelsrichtung aus Azimut (0° = Norden, 90° = Osten) in 8 Richtungen. */
fun compass(azimuth: Double): String {
    val names = listOf("Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten")
    val i = (((azimuth % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
    return names[i]
}
