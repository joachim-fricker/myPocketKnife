package de.jfricker.mypocketknife.tools.joke

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

data class Joke(val text: String, val source: String) {
    /** Stabile ID aus dem Text, damit derselbe Witz nicht doppelt gespeichert wird. */
    val id: String get() = jokeId(text)
}

fun jokeId(text: String): String =
    text.trim().lowercase().filter { it.isLetterOrDigit() }.hashCode().toUInt().toString(16)

/** Kategorien von witzapi.de */
val JOKE_CATEGORIES = listOf(
    "Flachwitze", "Scherzfragen", "Antiwitze", "Arztwitze", "Lehrerwitze",
    "Schulwitze", "Programmierwitze", "Chuck-Norris-Witze", "Blondinenwitze",
)

@Serializable
private data class WitzApiDto(val text: String, val language: String? = null)

@Serializable
private data class JokeApiDto(
    val error: Boolean = false,
    val type: String? = null,
    val joke: String? = null,
    val setup: String? = null,
    val delivery: String? = null,
)

/**
 * Deutsche Witze: zuerst witzapi.de, bei Fehler JokeAPI (lang=de, safe-mode).
 * Beide kostenlos und ohne API-Key.
 */
class JokeClient(private val http: OkHttpClient = OkHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun random(category: String?): Joke = withContext(Dispatchers.IO) {
        runCatching { witzApi(category) }
            .recoverCatching { if (category != null) witzApi(null) else throw it }
            .recoverCatching { jokeApi() }
            .getOrThrow()
    }

    private fun witzApi(category: String?): Joke {
        val query = category?.let { "?category=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val list = json.decodeFromString<List<WitzApiDto>>(get("https://witzapi.de/api/joke$query"))
        val text = list.firstOrNull()?.text?.trim()
        if (text.isNullOrBlank()) throw IOException("Kein Witz erhalten")
        return Joke(text, "witzapi.de")
    }

    private fun jokeApi(): Joke {
        val dto = json.decodeFromString<JokeApiDto>(get("https://v2.jokeapi.dev/joke/Any?lang=de&safe-mode"))
        if (dto.error) throw IOException("JokeAPI-Fehler")
        val text = when (dto.type) {
            "twopart" -> listOfNotNull(dto.setup?.trim(), dto.delivery?.trim()).joinToString("\n\n")
            else -> dto.joke?.trim().orEmpty()
        }
        if (text.isBlank()) throw IOException("Kein Witz erhalten")
        return Joke(text, "jokeapi.dev")
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Leere Antwort")
        }
    }
}
