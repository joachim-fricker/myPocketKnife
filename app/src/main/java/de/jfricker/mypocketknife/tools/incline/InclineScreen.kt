package de.jfricker.mypocketknife.tools.incline

import android.os.SystemClock
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import de.jfricker.mypocketknife.HardwareKeys
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private enum class Mode(val label: String) { SURFACE("Auflegen"), EDGE("Anpeilen") }

private val C_MODERATE = Color(0xFF4CAF50)
private val C_STEEP = Color(0xFFFFC107)
private val C_VERY = Color(0xFFFF7A00)
private val C_EXTREME = Color(0xFFD32F2F)

private fun colorOf(c: SteepnessClass) = when (c) {
    SteepnessClass.MODERATE -> C_MODERATE
    SteepnessClass.STEEP -> C_STEEP
    SteepnessClass.VERY_STEEP -> C_VERY
    SteepnessClass.EXTREME -> C_EXTREME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InclineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensor = remember { TiltSensor(context) }
    val calibration = remember { TiltCalibration(context) }
    val grmSettings = remember { GrmSettings(context) }
    var level by remember { mutableStateOf(grmSettings.level) }
    var outsideCore by remember { mutableStateOf(grmSettings.outsideCoreZone) }

    var mode by rememberSaveable { mutableStateOf(Mode.SURFACE) }
    var gravity by remember { mutableStateOf(floatArrayOf(0f, 0f, 9.81f)) }
    var held by remember { mutableStateOf<Double?>(null) }
    var showCalibrate by remember { mutableStateOf(false) }
    var calibrationVersion by remember { mutableStateOf(0) }

    // Freihändig messen
    val options = remember { InclineOptions(context) }
    var autoHold by remember { mutableStateOf(options.autoHold) }
    var speak by remember { mutableStateOf(options.speak) }
    val feedback = remember { MeasureFeedback(context) }
    val detector = remember { StabilityDetector() }
    var progress by remember { mutableFloatStateOf(0f) }

    // Sensor nur laufen lassen, solange der Bildschirm offen ist; Display bleibt an.
    DisposableEffect(Unit) {
        sensor.onGravity = { gravity = it }
        sensor.start()
        view.keepScreenOn = true
        onDispose {
            sensor.stop()
            sensor.onGravity = null
            view.keepScreenOn = false
            feedback.shutdown()
        }
    }

    // Kalibrierwerte nach jeder Kalibrierung neu lesen
    val flatRef = remember(calibrationVersion) { calibration.flatRef }
    val edgeOffset = remember(calibrationVersion) { calibration.edgeOffset }
    val live = when (mode) {
        Mode.SURFACE -> TiltMath.surfaceAngle(gravity, flatRef)
        Mode.EDGE -> TiltMath.edgeAngle(gravity, edgeOffset)
    }
    val angle = held ?: live
    val cls = SteepnessClass.of(angle)

    /** Wert festhalten und Rückmeldung geben (Ton, Vibration, Ansage). */
    fun hold(value: Double) {
        held = value
        progress = 0f
        feedback.beep()
        feedback.vibrate()
        if (speak) {
            val zone = Grm.zone(Grm.position(level, outsideCore), value)
            val zoneWord = when (zone) {
                Zone.GREEN -> "grün"
                Zone.ORANGE -> "orange"
                Zone.RED -> "rot"
            }
            feedback.speak("${value.roundToInt()} Grad, ${SteepnessClass.of(value).label}, Zone $zoneWord")
        }
    }

    /** Neue Messung starten. */
    fun rearm() {
        held = null
        detector.reset()
        progress = 0f
    }

    // Lautstärketaste: halten bzw. neu messen
    val holdOrRearm by rememberUpdatedState {
        if (held == null) hold(live) else rearm()
    }
    DisposableEffect(Unit) {
        HardwareKeys.onVolumeKey = { holdOrRearm() }
        onDispose { HardwareKeys.onVolumeKey = null }
    }

    // Automatisch halten, sobald das Handy ca. 2 s ruhig liegt
    val liveState = rememberUpdatedState(live)
    val heldState = rememberUpdatedState(held)
    val holdState = rememberUpdatedState(::hold)
    LaunchedEffect(autoHold, mode, calibrationVersion) {
        detector.reset()
        if (!autoHold) return@LaunchedEffect
        snapshotFlow { liveState.value }.collect { value ->
            if (heldState.value != null) return@collect
            val now = SystemClock.elapsedRealtime()
            val stable = detector.add(now, value)
            progress = detector.progress(now)
            if (stable != null) holdState.value(stable)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neigungsmesser") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
                actions = { TextButton(onClick = { showCalibrate = true }) { Text("Kalibrieren") } },
            )
        }
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!sensor.available) {
                Text("Dieses Gerät hat keinen Lagesensor.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            // 1) Messwert und Zone kompakt in einer Zeile
            val x = Grm.position(level, outsideCore)
            val zone = Grm.zone(x, angle)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    String.format(Locale.GERMAN, "%.1f°", angle),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorOf(cls),
                )
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(zone.title, color = zoneColor(zone), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(cls.label + if (held != null) " · gehalten" else "", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 2) GRM-Grafik direkt sichtbar
            GrmChart(x = x, angle = angle, modifier = Modifier.fillMaxWidth())

            // 3) Gefahrenstufe
            GrmLevelPicker(
                level = level,
                outsideCore = outsideCore,
                onLevel = { level = it; grmSettings.level = it },
                onOutsideCore = { outsideCore = it; grmSettings.outsideCoreZone = it },
            )

            // 4) Messen
            if (held == null && autoHold) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { if (held == null) hold(live) else rearm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(if (held == null) "Messwert halten" else "Neu messen", fontSize = 20.sp)
            }
            Text(
                if (autoHold) "Handy ruhig hinlegen – hält automatisch. Lautstärketaste = halten / neu messen."
                else "Lautstärketaste = halten / neu messen.",
                style = MaterialTheme.typography.bodySmall,
            )

            // 5) Details und Einstellungen
            GrmDetails(angle = angle, level = level, x = x)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Einstellungen", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m; rearm() },
                                label = { Text(m.label) },
                            )
                        }
                    }
                    Text(
                        when (mode) {
                            Mode.SURFACE -> "Handy flach auf den Hang legen – am besten auf den Skistock in Falllinie."
                            Mode.EDGE -> "Längskante des Handys in Falllinie halten bzw. entlang des Hangs anpeilen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OptionSwitch("Automatisch halten, wenn das Handy ruhig liegt", autoHold) {
                        autoHold = it; options.autoHold = it; rearm()
                    }
                    OptionSwitch("Wert und GRM-Zone ansagen", speak) {
                        speak = it; options.speak = it
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Steilheitsklassen (SLF)", fontWeight = FontWeight.Bold)
                    SteepnessClass.entries.forEach { c ->
                        val range = when (c) {
                            SteepnessClass.MODERATE -> "unter 30°"
                            SteepnessClass.STEEP -> "30° – 35°"
                            SteepnessClass.VERY_STEEP -> "35° – 40°"
                            SteepnessClass.EXTREME -> "über 40°"
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("● ${c.label}", color = colorOf(c), fontWeight = if (c == cls) FontWeight.Bold else FontWeight.Normal)
                            Text(range)
                        }
                    }
                }
            }

            Text(
                "Gemessen wird die Neigung an einer Stelle. Für die Beurteilung zählt die steilste Stelle " +
                    "im massgebenden Hangbereich (ca. 20 × 20 m) – mehrmals messen. " +
                    "Hilfsmittel, ersetzt keine Lawinenausbildung.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (showCalibrate) {
        AlertDialog(
            onDismissRequest = { showCalibrate = false },
            title = { Text("Kalibrieren") },
            text = {
                Text(
                    "Handy auf eine ebene, waagrechte Fläche legen (Display nach oben) und „Jetzt kalibrieren“ drücken. " +
                        "Damit werden kleine Sensorfehler beider Modi ausgeglichen."
                )
            },
            confirmButton = {
                Button(onClick = {
                    calibration.flatRef = TiltMath.unit(gravity)
                    calibration.edgeOffset = TiltMath.rawEdge(gravity)
                    calibrationVersion++
                    held = null
                    showCalibrate = false
                }) { Text("Jetzt kalibrieren") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    calibration.reset()
                    calibrationVersion++
                    showCalibrate = false
                }) { Text("Zurücksetzen") }
            },
        )
    }
}

private fun zoneColor(zone: Zone) = when (zone) {
    Zone.GREEN -> C_MODERATE
    Zone.ORANGE -> C_VERY
    Zone.RED -> C_EXTREME
}

@Composable
private fun GrmLevelPicker(
    level: DangerLevel,
    outsideCore: Boolean,
    onLevel: (DangerLevel) -> Unit,
    onOutsideCore: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            DangerLevel.entries.forEach { l ->
                FilterChip(selected = level == l, onClick = { onLevel(l) }, label = { Text(l.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = outsideCore, onCheckedChange = onOutsideCore)
            Text(
                "Ausserhalb kritischer Expositionen/Höhenlagen (eine Stufe tiefer)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GrmDetails(angle: Double, level: DangerLevel, x: Double) {
    val beginner = Grm.beginnerLimit(x)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Grafische Reduktionsmethode – ${level.label} (${level.word})", fontWeight = FontWeight.Bold)
            Text(
                String.format(
                    Locale.GERMAN,
                    "Grün bis %.1f°, orange bis %.1f°, darüber rot.",
                    Grm.greenLimit(x), Grm.redLimit(x),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                String.format(
                    Locale.GERMAN,
                    if (angle < beginner) "Unter der schwarzen Linie (Einsteiger: unter %.0f°)."
                    else "Über der schwarzen Linie (Einsteiger-Grenze %.0f°).",
                    beginner,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Steilste Stelle suchen in: ${level.relevantArea}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Grenzen aus der GRM-Grafik übernommen. Die Übergänge sind fliessend – " +
                    "Werte nahe einer Grenze vorsichtig behandeln.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
