package de.jfricker.mypocketknife.tools.currency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Interne Form der Kurse (so wird auch im Cache gespeichert): 1 [base] = rates[x] x. */
@Serializable
data class LatestRatesDto(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)

/** Ein Eintrag der v2-Antwort: 1 [base] = [rate] [quote] am [date]. */
@Serializable
private data class RateDto(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double,
)

/**
 * Frankfurter v2: kostenlos, kein API-Key. Bündelt die Kurse von rund einem Dutzend
 * Zentralbanken (EZB, BCRA Argentinien, …) – damit auch Währungen wie ARS oder DOP.
 * Je nach Bank kann das Datum pro Währung leicht unterschiedlich sein.
 */
class FrankfurterClient(private val http: OkHttpClient = OkHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Holt alle aktuellen Kurse mit Basis EUR. Kreuzkurse rechnen wir selbst aus. */
    suspend fun fetchLatest(): LatestRatesDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/rates?base=EUR")
            .build()
        val list = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Leere Antwort")
            json.decodeFromString<List<RateDto>>(body)
        }
        val usable = list.filter { it.base == "EUR" && it.rate > 0 }
        if (usable.isEmpty()) throw IOException("Keine Kurse erhalten")
        LatestRatesDto(
            amount = 1.0,
            base = "EUR",
            date = usable.maxOf { it.date },
            rates = usable.filter { it.quote != "EUR" }.associate { it.quote to it.rate },
        )
    }

    companion object {
        const val BASE_URL = "https://api.frankfurter.dev/v2"
    }
}
