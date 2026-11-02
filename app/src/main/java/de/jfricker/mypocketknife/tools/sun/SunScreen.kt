package de.jfricker.mypocketknife.tools.sun

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val DAY = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)

// Farben der Lichtphasen
private val C_NIGHT = Color(0xFF0B1026)
private val C_ASTRO = Color(0xFF1B2A55)
private val C_NAUTICAL = Color(0xFF2C4A8A)
private val C_BLUE = Color(0xFF3F6FD8)
private val C_GOLDEN = Color(0xFFF2A541)
private val C_DAY = Color(0xFF8EC9F0)

private fun colorFor(alt: Double) = when {
    alt >= 6 -> C_DAY
    alt >= -4 -> C_GOLDEN
    alt >= -8 -> C_BLUE
    alt >= -12 -> C_NAUTICAL
    alt >= -18 -> C_ASTRO
    else -> C_NIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunScreen(onBack: () -> Unit, vm: SunViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.onPermissionResult(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sonne") },
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // --- Standort ---
            val loc = state.location
            when {
                loc != null -> Text(
                    "📍 " + (loc.name ?: "%.3f, %.3f".format(Locale.GERMAN, loc.lat, loc.lng)),
                    style = MaterialTheme.typography.titleMedium,
                )
                !state.hasPermission -> Button(onClick = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("📍 Standort verwenden") }
                else -> Text("📍 Standort wird ermittelt …")
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val day = state.day ?: return@Column

            // --- Tag wählen ---
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = vm::previousDay) { Text("◀", fontSize = 18.sp) }
                Text(
                    day.date.format(DAY),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::nextDay) { Text("▶", fontSize = 18.sp) }
            }
            if (state.dayOffset != 0) {
                TextButton(onClick = vm::today, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Zurück zu heute")
                }
            }

            // --- Jetzt ---
            val alt = day.nowAltitude
            val az = day.nowAzimuth
            if (alt != null && az != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorFor(alt)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val onColor = if (alt >= -4) Color(0xFF1A1A1A) else Color.White
                    Column(Modifier.padding(16.dp)) {
                        Text("Jetzt: ${SunCalculator.phaseOf(alt)}", color = onColor,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (alt > 0) "Sonne ${alt.roundToInt()}° über dem Horizont im ${compass(az)}"
                            else "Sonne ${(-alt).roundToInt()}° unter dem Horizont",
                            color = onColor,
                        )
                    }
                }
            }

            // --- Tagesband ---
            DayBand(day)

            // --- Fotografen-Zeiten ---
            InfoCard("✨ Goldene Stunde") {
                WindowLine("Morgens", day.goldenMorning, day.nowAltitude != null)
                WindowLine("Abends", day.goldenEvening, day.nowAltitude != null)
            }
            InfoCard("💙 Blaue Stunde") {
                WindowLine("Morgens", day.blueMorning, day.nowAltitude != null)
                WindowLine("Abends", day.blueEvening, day.nowAltitude != null)
            }

            // --- Sonne ---
            InfoCard("☀️ Sonne") {
                when {
                    day.alwaysUp -> Text("Mitternachtssonne – die Sonne geht heute nicht unter.")
                    day.alwaysDown -> Text("Polarnacht – die Sonne geht heute nicht auf.")
                    else -> {
                        LabelValue("Aufgang", day.rise.hhmm())
                        LabelValue("Untergang", day.set.hhmm())
                    }
                }
                day.noon?.let {
                    LabelValue("Höchststand", it.hhmm() + (day.noonAltitude?.let { a -> " (${a.roundToInt()}°)" } ?: ""))
                }
                day.dayLength?.let { LabelValue("Tageslänge", formatDuration(it)) }
            }

            // --- Dämmerung ---
            InfoCard("🌗 Dämmerung") {
                Text("Beginn morgens – Ende abends", style = MaterialTheme.typography.labelSmall)
                LabelValue("Bürgerlich (−6°)", "${day.civil.start.hhmm()} – ${day.civil.end.hhmm()}")
                LabelValue("Nautisch (−12°)", "${day.nautical.start.hhmm()} – ${day.nautical.end.hhmm()}")
                LabelValue("Astronomisch (−18°)", "${day.astronomical.start.hhmm()} – ${day.astronomical.end.hhmm()}")
            }

            Text(
                "Goldene Stunde: Sonne zwischen −4° und +6°. Blaue Stunde: zwischen −8° und −4°. " +
                    "Zeiten in der Zeitzone des Handys, Berechnung offline.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Farbiges Band über 24 Stunden mit Stundenmarken und Jetzt-Strich. */
@Composable
private fun DayBand(day: SunDay) {
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val n = day.altitudes.size
            val w = size.width / n
            day.altitudes.forEachIndexed { i, alt ->
                drawRect(colorFor(alt), topLeft = Offset(i * w, 0f), size = Size(w + 1f, size.height))
            }
            // Stundenmarken alle 6 Stunden
            for (h in listOf(6, 12, 18)) {
                val x = size.width * h / 24f
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, size.height * 0.7f), Offset(x, size.height), 2f)
            }
            day.nowAltitude?.let {
                val now = ZonedDateTime.now()
                val minutes = now.hour * 60 + now.minute
                val x = size.width * minutes / (24f * 60f)
                drawLine(Color.Red, Offset(x, 0f), Offset(x, size.height), 4f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Legend(C_GOLDEN, "Golden")
            Legend(C_BLUE, "Blau")
            Legend(C_NAUTICAL, "Dämmerung")
            Legend(C_NIGHT, "Nacht")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRect(color) }
        Text(" $label", style = MaterialTheme.typography.labelSmall)
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
private fun WindowLine(label: String, w: TimeWindow, isToday: Boolean) {
    val now = ZonedDateTime.now()
    val active = isToday && w.start != null && w.end != null && now.isAfter(w.start) && now.isBefore(w.end)
    val value = if (w.start == null && w.end == null) "findet nicht statt"
    else "${w.start.hhmm()} – ${w.end.hhmm()}" + (w.duration()?.let { " (${it.toMinutes()} Min.)" } ?: "")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label + if (active) "  • jetzt" else "", fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun ZonedDateTime?.hhmm(): String = this?.format(TIME) ?: "–"

private fun TimeWindow.duration(): Duration? =
    if (start != null && end != null && end.isAfter(start)) Duration.between(start, end) else null

private fun formatDuration(d: Duration): String = "${d.toHours()} Std. ${d.toMinutesPart()} Min."
