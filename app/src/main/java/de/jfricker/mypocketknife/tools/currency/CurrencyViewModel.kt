package de.jfricker.mypocketknife.tools.currency

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** EUR und CHF stehen immer oben und lassen sich weder löschen noch verschieben. */
val FIXED_CODES = listOf("EUR", "CHF")

data class CurrencyUiState(
    val extraCodes: List<String> = emptyList(),
    /** Zeile, in die zuletzt getippt wurde – alle anderen werden daraus berechnet. */
    val inputCode: String = "EUR",
    val inputText: String = "100",
    /** Kurse relativ zu 1 EUR, inkl. EUR = 1.0 */
    val rates: Map<String, Double> = emptyMap(),
    val ratesDate: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val editMode: Boolean = false,
) {
    val codes: List<String> get() = FIXED_CODES + extraCodes

    /** Währungen, die man noch hinzufügen kann. */
    val addable: List<String> get() = rates.keys.filter { it !in codes }.sorted()

    /** Eingegebener Betrag umgerechnet in EUR, oder null. */
    private val amountInEur: Double?
        get() {
            val amount = parseAmount(inputText) ?: return null
            val rate = rates[inputCode] ?: return null
            return amount / rate
        }

    /** Berechneter Betrag für eine Zeile (nicht für die Eingabezeile selbst). */
    fun amountFor(code: String): Double? {
        val eur = amountInEur ?: return null
        val rate = rates[code] ?: return null
        return eur * rate
    }
}

/** Akzeptiert "1234,56", "1.234,56", "1'234.56", "1,234.56", "1234.56". */
fun parseAmount(text: String): Double? {
    var s = text.trim().replace(" ", "").replace(" ", "")
        .replace("'", "").replace("’", "")
    if (s.isEmpty()) return null
    val lastComma = s.lastIndexOf(',')
    val lastDot = s.lastIndexOf('.')
    s = when {
        lastComma > lastDot -> s.replace(".", "").replace(',', '.') // Komma ist Dezimaltrenner
        lastDot > lastComma && lastComma >= 0 -> s.replace(",", "") // Komma ist Tausendertrenner
        else -> s
    }
    return s.toDoubleOrNull()
}

class CurrencyViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RatesStore(app)
    private val client = FrankfurterClient()

    private val _state = MutableStateFlow(
        CurrencyUiState(
            extraCodes = store.extraCodes.filter { it !in FIXED_CODES },
            inputCode = store.inputCode,
            inputText = store.inputText,
        )
    )
    val state: StateFlow<CurrencyUiState> = _state.asStateFlow()

    init {
        store.loadRates()?.let { applyRates(it) }
        // Kurse sind höchstens einmal pro Arbeitstag neu; nach 3 Stunden erneut fragen.
        val stale = System.currentTimeMillis() - store.fetchedAt > 3 * 60 * 60 * 1000L
        // Alter Cache (nur EZB, ~30 Währungen) → sofort neu laden, damit ARS, DOP usw. da sind.
        val oldCache = _state.value.rates.size < 40
        if (_state.value.rates.isEmpty() || stale || oldCache) refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val latest = client.fetchLatest()
                store.saveRates(latest)
                applyRates(latest)
                _state.update { it.copy(loading = false) }
            } catch (_: Exception) {
                val msg = if (_state.value.rates.isEmpty())
                    "Kurse konnten nicht geladen werden. Internet prüfen."
                else
                    "Keine Verbindung – es werden die gespeicherten Kurse verwendet."
                _state.update { it.copy(loading = false, error = msg) }
            }
        }
    }

    /** Der Nutzer tippt in die Zeile [code]: sie wird zur Eingabezeile. */
    fun onAmountChange(code: String, text: String) {
        _state.update { it.copy(inputCode = code, inputText = text) }
        store.inputCode = code
        store.inputText = text
    }

    fun toggleEditMode() = _state.update { it.copy(editMode = !it.editMode) }

    fun add(code: String) = setExtras(_state.value.extraCodes + code)

    fun remove(code: String) {
        setExtras(_state.value.extraCodes - code)
        // Wurde die Eingabezeile gelöscht, Betrag nach EUR übernehmen.
        val s = _state.value
        if (s.inputCode == code) {
            val eur = s.amountFor("EUR")
            onAmountChange("EUR", eur?.let { formatPlain(it) } ?: "")
        }
    }

    fun moveUp(code: String) = move(code, -1)
    fun moveDown(code: String) = move(code, +1)

    private fun move(code: String, delta: Int) {
        val list = _state.value.extraCodes.toMutableList()
        val i = list.indexOf(code)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        setExtras(list)
    }

    private fun setExtras(list: List<String>) {
        val clean = list.distinct().filter { it !in FIXED_CODES }
        _state.update { it.copy(extraCodes = clean) }
        store.extraCodes = clean
    }

    private fun applyRates(dto: LatestRatesDto) {
        val all = dto.rates + (dto.base to 1.0)
        _state.update { it.copy(rates = all, ratesDate = dto.date) }
    }

    private fun formatPlain(v: Double) = "%.2f".format(v).replace('.', ',')
}
