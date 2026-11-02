package de.jfricker.mypocketknife.tools.moon

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jfricker.mypocketknife.location.compass
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DATE_TIME = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy 'um' HH:mm", Locale.GERMAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonScreen(onBack: () -> Unit, vm: MoonViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mondphase") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                actions = {
                    if (state.locating) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(24.dp))
                    } else if (state.hasPermission) {
                        IconButton(onClick = vm::updateLocation) { Text("⌖", fontSize = 22.sp) }
                    }
                },
            )
        }
    ) { padding ->
        val info = state.info
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            LocationBar(state, onRequest = {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            })

            if (info == null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            // --- Mond + Phase ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MoonDrawing(
                    fraction = info.fraction,
                    waxing = info.waxing,
                    southern = info.southern,
                    modifier = Modifier.size(200.dp),
                )
                Text(info.phaseName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "${(info.fraction * 100).roundToInt()} % beleuchtet · ${"%.1f".format(Locale.GERMAN, info.ageDays)} Tage alt · " +
                        if (info.waxing) "zunehmend" else "abnehmend",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // --- Nächster Voll- / Neumond ---
            InfoCard("Nächste Termine") {
                EventLine("🌕 Vollmond", info.nextFull, info.now)
                EventLine("🌑 Neumond", info.nextNew, info.now)
            }

            // --- Standortabhängig ---
            if (info.hasLocation) {
                InfoCard("Heute an deinem Standort") {
                    when {
                        info.alwaysUp -> Text("Der Mond geht heute nicht unter.")
                        info.alwaysDown -> Text("Der Mond geht heute nicht auf.")
                        else -> {
                            val items = listOfNotNull(
                                info.rise?.let { "Aufgang" to it },
                                info.set?.let { "Untergang" to it },
                            ).sortedBy { it.second }
                            items.forEach { (label, time) -> LabelValue(label, time.format(TIME) + " Uhr") }
                            if (info.rise == null) Text("Heute kein Mondaufgang", style = MaterialTheme.typography.bodySmall)
                            if (info.set == null) Text("Heute kein Monduntergang", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val alt = info.altitude
                    val az = info.azimuth
                    if (alt != null && az != null) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            if (alt > 0) "Jetzt sichtbar: ${alt.roundToInt()}° über dem Horizont im ${compass(az)}"
                            else "Jetzt unter dem Horizont (${compass(az)})",
                        )
                    }
                }
            }

            // --- Kommende Termine ---
            InfoCard("Vorschau") {
                info.upcoming.forEach { e ->
                    val label = if (e.type == MoonEvent.Type.FULL) "🌕" else "🌑"
                    LabelValue(
                        "$label ${e.time.format(DATE_TIME)}",
                        if (e.superMoon) "Supermond" else "",
                    )
                }
            }

            Text(
                "Zeiten in der Zeitzone des Handys. Berechnung offline.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun LocationBar(state: MoonUiState, onRequest: () -> Unit) {
    val loc = state.location
    Column {
        when {
            loc != null -> Text(
                "📍 " + (loc.name ?: "%.3f, %.3f".format(Locale.GERMAN, loc.lat, loc.lng)),
                style = MaterialTheme.typography.titleMedium,
            )
            !state.hasPermission -> Button(onClick = onRequest) { Text("📍 Standort verwenden") }
            else -> Text("📍 Standort wird ermittelt …")
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun EventLine(label: String, e: MoonEvent, now: ZonedDateTime) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label + if (e.superMoon) " (Supermond)" else "", style = MaterialTheme.typography.labelLarge)
        Text("${e.time.format(DATE_TIME)} Uhr · ${until(now, e.time)}")
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun until(now: ZonedDateTime, t: ZonedDateTime): String {
    val d = Duration.between(now, t)
    val days = d.toDays()
    val hours = d.minusDays(days).toHours()
    return when {
        days >= 2 -> "in $days Tagen"
        days == 1L -> "in 1 Tag, $hours Std."
        else -> "in $hours Std."
    }
}
