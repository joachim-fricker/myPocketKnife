package de.jfricker.mypocketknife.tools.currency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.Normalizer
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyScreen(onBack: () -> Unit, vm: CurrencyViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Währungsrechner") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
                },
                actions = {
                    TextButton(onClick = vm::toggleEditMode) {
                        Text(if (state.editMode) "Fertig" else "Bearbeiten")
                    }
                    if (state.loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
                        )
                    } else {
                        IconButton(onClick = vm::refresh) { Text("↻", fontSize = 22.sp) }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(state.codes, key = { it }) { code ->
                val extraIndex = state.extraCodes.indexOf(code)
                CurrencyRow(
                    code = code,
                    state = state,
                    fixed = code in FIXED_CODES,
                    canMoveUp = extraIndex > 0,
                    canMoveDown = extraIndex in 0 until state.extraCodes.lastIndex,
                    onAmountChange = { vm.onAmountChange(code, it) },
                    onMoveUp = { vm.moveUp(code) },
                    onMoveDown = { vm.moveDown(code) },
                    onRemove = { vm.remove(code) },
                    modifier = Modifier.animateItem(),
                )
            }

            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = state.addable.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("+ Währung hinzufügen") }
            }

            item {
                Column(Modifier.padding(top = 8.dp)) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    state.ratesDate?.let {
                        Text("Kurse vom ${formatDate(it)}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (state.rates.isEmpty() && state.loading) {
                        Text("Kurse werden geladen …", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showPicker) {
        CurrencyPickerDialog(
            currencies = state.addable,
            onPick = { vm.add(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun CurrencyRow(
    code: String,
    state: CurrencyUiState,
    fixed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAmountChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInput = code == state.inputCode
    val computed = if (isInput) null else state.amountFor(code)
    val display = if (isInput) state.inputText else computed?.let { formatMoney(it, code) } ?: ""

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isInput) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(flagOf(code), fontSize = 28.sp)
            Column(
                Modifier
                    .padding(start = 10.dp, end = 8.dp)
                    .weight(1f)
            ) {
                Text(code, style = MaterialTheme.typography.titleMedium)
                Text(
                    nameOf(code),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (code != "EUR") {
                    state.rates[code]?.let {
                        Text("1 EUR = ${formatRate(it)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (state.editMode) {
                if (fixed) {
                    Text("fix", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                } else {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑", fontSize = 20.sp) }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓", fontSize = 20.sp) }
                    IconButton(onClick = onRemove) {
                        Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                AmountField(
                    display = display,
                    editableValue = { computed?.let { formatEditable(it, code) } ?: "" },
                    isInput = isInput,
                    onChange = onAmountChange,
                    modifier = Modifier.width(175.dp),
                )
            }
        }
    }
}

/**
 * Betragsfeld. Tippt man in eine berechnete Zeile, wird sie zur Eingabezeile:
 * der Wert wird ohne Tausendertrenner übernommen und komplett markiert,
 * damit man direkt überschreiben kann.
 */
@Composable
private fun AmountField(
    display: String,
    editableValue: () -> String,
    isInput: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(display, TextRange(display.length))) }

    LaunchedEffect(display) {
        if (value.text != display) value = TextFieldValue(display, TextRange(display.length))
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            val changed = it.text != value.text
            value = it
            if (changed) onChange(it.text)
        },
        singleLine = true,
        isError = isInput && display.isNotBlank() && parseAmount(display) == null,
        trailingIcon = if (isInput && value.text.isNotEmpty()) {
            {
                IconButton(onClick = {
                    value = TextFieldValue("")
                    onChange("")
                }) { Text("✕", fontSize = 16.sp) }
            }
        } else null,
        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.onFocusChanged { f ->
            if (f.isFocused && !isInput) {
                val text = editableValue()
                value = TextFieldValue(text, TextRange(0, text.length))
                onChange(text)
            }
        },
    )
}

@Composable
private fun CurrencyPickerDialog(
    currencies: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = normalize(query.trim())
    // Pro Währung: passt Code/Name? sonst welche Länder passen?
    val filtered = currencies.mapNotNull { code ->
        when {
            q.isEmpty() -> code to null
            normalize(code).contains(q) || normalize(nameOf(code)).contains(q) -> code to null
            else -> countriesOf(code).filter { normalize(it).contains(q) }
                .takeIf { it.isNotEmpty() }
                ?.let { code to it.joinToString(", ") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text("Währung hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Währung oder Land, z. B. Brasilien") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered, key = { it.first }) { (code, matchedCountries) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(flagOf(code), fontSize = 22.sp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("$code  ${nameOf(code)}")
                                matchedCountries?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

// ---------- Hilfsfunktionen ----------

/** Deutscher Name aus dem System, z. B. "Schweizer Franken". */
private fun nameOf(code: String): String =
    SPECIAL_NAMES[code]
        ?: runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)

/** Codes, die Android nicht kennt oder die keine Landeswährung sind. */
private val CRYPTO = setOf("BTC", "ETH", "LTC", "BCH", "XRP")
private val METALS = setOf("XAU", "XAG", "XPT", "XPD")
private val SPECIAL_NAMES = mapOf(
    "BTC" to "Bitcoin", "ETH" to "Ether", "LTC" to "Litecoin", "BCH" to "Bitcoin Cash", "XRP" to "XRP",
    "XAU" to "Gold (Feinunze)", "XAG" to "Silber (Feinunze)",
    "XPT" to "Platin (Feinunze)", "XPD" to "Palladium (Feinunze)",
    "XDR" to "Sonderziehungsrecht (IWF)", "CNH" to "Chinesischer Yuan (Offshore)",
)

/**
 * Deutsche Ländernamen je Währung, z. B. EUR → Deutschland, Österreich, …;
 * USD → Vereinigte Staaten, Ecuador, … Einmalig aus den Systemdaten aufgebaut.
 */
private val countriesByCurrency: Map<String, List<String>> by lazy {
    val german = Locale.GERMAN
    Locale.getISOCountries()
        .mapNotNull { cc ->
            runCatching {
                val region = Locale.Builder().setRegion(cc).build()
                val currency = Currency.getInstance(region) ?: return@runCatching null
                currency.currencyCode to region.getDisplayCountry(german)
            }.getOrNull()
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, names) -> names.sorted() }
}

private fun countriesOf(code: String): List<String> = countriesByCurrency[code].orEmpty()

/** Kleinbuchstaben, ohne Akzente/Umlaut-Punkte: "Österreich" findet man auch mit "osterreich". */
private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.GERMAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

/** Flagge aus den ersten zwei Buchstaben des ISO-Codes (CHF → CH, EUR → EU). */
private fun flagOf(code: String): String {
    if (code in CRYPTO) return "🪙"
    if (code in METALS) return "🥇"
    if (code.startsWith("X")) return "🌐" // Gemeinschaftswährungen wie XOF, XAF, XCD
    if (code.length < 2) return ""
    val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private fun fractionDigits(code: String): Int =
    runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

/** Anzeige mit Tausendertrennern, z. B. "1.234,56". */
private fun formatMoney(value: Double, code: String): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits(code)
        maximumFractionDigits = fractionDigits(code)
    }.format(value)

/** Zum Weitertippen ohne Tausendertrenner, z. B. "1234,56". */
private fun formatEditable(value: Double, code: String): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits(code)
    }.format(value)

private fun formatRate(value: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 4
        maximumFractionDigits = 4
    }.format(value)

private fun formatDate(iso: String): String =
    runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }.getOrDefault(iso)
