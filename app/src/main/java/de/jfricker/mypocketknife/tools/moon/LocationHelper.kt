package de.jfricker.mypocketknife.tools.moon

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.edit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class SavedLocation(val lat: Double, val lng: Double, val name: String?)

/** Standort über den System-LocationManager (ohne Google Play) + Ortsname per Geocoder. */
class LocationHelper(private val context: Context) {

    private val lm = context.getSystemService(LocationManager::class.java)
    private val prefs = context.getSharedPreferences("location", Context.MODE_PRIVATE)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Zuletzt verwendeter Standort – damit der Mond sofort (und offline) richtig angezeigt wird. */
    fun saved(): SavedLocation? {
        if (!prefs.contains("lat")) return null
        return SavedLocation(
            prefs.getFloat("lat", 0f).toDouble(),
            prefs.getFloat("lng", 0f).toDouble(),
            prefs.getString("name", null),
        )
    }

    fun save(loc: SavedLocation) = prefs.edit {
        putFloat("lat", loc.lat.toFloat())
        putFloat("lng", loc.lng.toFloat())
        putString("name", loc.name)
    }

    /** Aktueller Standort (max. 10 s warten), sonst der letzte bekannte. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val provider = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { lm.hasProvider(it) && lm.isProviderEnabled(it) }

        val fresh = provider?.let { p ->
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        lm.getCurrentLocation(p, signal, context.mainExecutor) { loc -> cont.resume(loc) }
                    }.onFailure { cont.resume(null) }
                }
            }
        }
        return fresh ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? =
        lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    /** "Eglisau, Schweiz" o. ä.; null ohne Internet/Geocoder. */
    suspend fun placeName(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<String?> { cont ->
                runCatching {
                    Geocoder(context, Locale.GERMAN).getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull()?.let(::format))
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }.onFailure { cont.resume(null) }
            }
        }
    }

    private fun format(a: Address): String? {
        val place = a.locality ?: a.subAdminArea ?: a.adminArea
        return listOfNotNull(place, a.countryName).joinToString(", ").ifBlank { null }
    }
}
