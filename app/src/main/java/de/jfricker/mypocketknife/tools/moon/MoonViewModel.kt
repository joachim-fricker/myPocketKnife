package de.jfricker.mypocketknife.tools.moon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.location.LocationHelper
import de.jfricker.mypocketknife.location.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

data class MoonUiState(
    val info: MoonInfo? = null,
    val location: SavedLocation? = null,
    val hasPermission: Boolean = false,
    val locating: Boolean = false,
    val message: String? = null,
)

class MoonViewModel(app: Application) : AndroidViewModel(app) {

    private val locations = LocationHelper(app)

    private val _state = MutableStateFlow(
        MoonUiState(location = locations.saved(), hasPermission = locations.hasPermission())
    )
    val state: StateFlow<MoonUiState> = _state.asStateFlow()

    init {
        // Jede Minute neu rechnen, damit Position und Beleuchtung aktuell bleiben.
        viewModelScope.launch {
            while (isActive) {
                recompute()
                delay(60_000)
            }
        }
        if (_state.value.hasPermission) updateLocation()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update {
            it.copy(
                hasPermission = granted,
                message = if (granted) null else "Ohne Standort gibt es keine Auf- und Untergangszeiten.",
            )
        }
        if (granted) updateLocation()
    }

    fun updateLocation() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, message = null) }
        viewModelScope.launch {
            val loc = locations.current()
            if (loc == null) {
                _state.update {
                    it.copy(
                        locating = false,
                        message = if (it.location != null) "Standort nicht verfügbar – letzter bekannter wird verwendet."
                        else "Standort nicht verfügbar. Ist die Ortung eingeschaltet?",
                    )
                }
                return@launch
            }
            // Ortsname nur neu holen, wenn wir uns spürbar (> ~2 km) bewegt haben.
            val old = _state.value.location
            val moved = old == null || old.name == null ||
                kotlin.math.abs(old.lat - loc.latitude) > 0.02 || kotlin.math.abs(old.lng - loc.longitude) > 0.02
            val name = if (moved) locations.placeName(loc.latitude, loc.longitude) ?: old?.name else old!!.name
            val saved = SavedLocation(loc.latitude, loc.longitude, name)
            locations.save(saved)
            _state.update { it.copy(location = saved, locating = false) }
            recompute()
        }
    }

    private suspend fun recompute() {
        val loc = _state.value.location
        val info = withContext(Dispatchers.Default) {
            MoonCalculator.compute(ZonedDateTime.now(), loc?.lat, loc?.lng)
        }
        _state.update { it.copy(info = info) }
    }
}
