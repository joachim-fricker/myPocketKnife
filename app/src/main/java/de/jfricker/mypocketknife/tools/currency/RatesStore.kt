package de.jfricker.mypocketknife.tools.currency

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json

/** Zuletzt geladene Kurse und die Listen-Konfiguration, damit der Rechner auch offline geht. */
class RatesStore(context: Context) {

    private val prefs = context.getSharedPreferences("currency", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRates(): LatestRatesDto? =
        prefs.getString(KEY_RATES, null)?.let {
            runCatching { json.decodeFromString<LatestRatesDto>(it) }.getOrNull()
        }

    fun saveRates(rates: LatestRatesDto) = prefs.edit {
        putString(KEY_RATES, json.encodeToString(LatestRatesDto.serializer(), rates))
        putLong(KEY_FETCHED_AT, System.currentTimeMillis())
    }

    val fetchedAt: Long get() = prefs.getLong(KEY_FETCHED_AT, 0L)

    /** Die frei konfigurierbaren Währungen (ohne EUR/CHF), in Anzeigereihenfolge. */
    var extraCodes: List<String>
        get() = prefs.getString(KEY_EXTRA, "")!!.split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_EXTRA, value.joinToString(",")) }

    /** Zeile, in die zuletzt getippt wurde, und der Betrag darin. */
    var inputCode: String
        get() = prefs.getString(KEY_INPUT_CODE, "EUR") ?: "EUR"
        set(value) = prefs.edit { putString(KEY_INPUT_CODE, value) }

    var inputText: String
        get() = prefs.getString(KEY_INPUT_TEXT, "100") ?: "100"
        set(value) = prefs.edit { putString(KEY_INPUT_TEXT, value) }

    private companion object {
        const val KEY_RATES = "rates_json"
        const val KEY_FETCHED_AT = "fetched_at"
        const val KEY_EXTRA = "extra_codes"
        const val KEY_INPUT_CODE = "input_code"
        const val KEY_INPUT_TEXT = "input_text"
    }
}
