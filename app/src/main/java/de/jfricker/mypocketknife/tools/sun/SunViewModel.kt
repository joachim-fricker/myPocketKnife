package de.jfricker.mypocketknife.tools.sun

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

data class SunUiState(
    val day: SunDay? = null,
    /** 0 = heute, -1 = gestern, +1 = morgen … */
    val dayOffset: Int = 0,
    val location: SavedLocation? = null,
    val hasPermission: Boolean = false,
    val locating: Boolean = false,
    val message: String? = null,
)

class SunViewModel(app: Application) : AndroidViewModel(app) {

    private val locations = LocationHelper(app)

    private val _state = MutableStateFlow(
        SunUiState(location = locations.saved(), hasPermission = locations.hasPermission())
    )
    val state: StateFlow<SunUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                recompute()
                delay(60_000)
            }
        }
        if (_state.value.hasPermission) updateLocation()
    }

    fun previousDay() = changeDay(-1)
    fun nextDay() = changeDay(+1)
    fun today() = changeDay(-_state.value.dayOffset)

    private fun changeDay(delta: Int) {
        _state.update { it.copy(dayOffset = it.dayOffset + delta) }
        viewModelScope.launch { recompute() }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update {
            it.copy(hasPermission = granted, message = if (granted) null else "Ohne Standort lassen sich die Zeiten nicht berechnen.")
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
            val old = _state.value.location
            val moved = old == null || old.name == null ||
                abs(old.lat - loc.latitude) > 0.02 || abs(old.lng - loc.longitude) > 0.02
            val name = if (moved) locations.placeName(loc.latitude, loc.longitude) ?: old?.name else old?.name
            val saved = SavedLocation(loc.latitude, loc.longitude, name)
            locations.save(saved)
            _state.update { it.copy(location = saved, locating = false) }
            recompute()
        }
    }

    private suspend fun recompute() {
        val s = _state.value
        val loc = s.location ?: return
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).plusDays(s.dayOffset.toLong())
        val now = if (s.dayOffset == 0) ZonedDateTime.now(zone) else null
        val day = withContext(Dispatchers.Default) {
            SunCalculator.compute(date, zone, loc.lat, loc.lng, now)
        }
        _state.update { it.copy(day = day) }
    }
}
