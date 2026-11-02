package de.jfricker.mypocketknife.tools.joke

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedJoke(
    val id: String,
    val text: String,
    val source: String,
    /** 1 … 5 Sterne */
    val stars: Int,
    val savedAt: Long,
)

@Serializable
data class JokeData(
    val favorites: List<SavedJoke> = emptyList(),
    /** Witz des Tages: Datum (yyyy-MM-dd) + Witz */
    val dailyDate: String? = null,
    val dailyText: String? = null,
    val dailySource: String? = null,
    /** IDs der zuletzt gezeigten Witze, um Wiederholungen zu vermeiden */
    val seen: List<String> = emptyList(),
)

/** Alles in einer kleinen JSON-Datei im App-Speicher. */
class JokeStore(context: Context) {

    private val file = File(context.filesDir, "jokes.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun load(): JokeData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext JokeData()
        runCatching { json.decodeFromString<JokeData>(file.readText()) }.getOrDefault(JokeData())
    }

    private val lock = Mutex()

    suspend fun save(data: JokeData) = withContext(Dispatchers.IO) {
        lock.withLock { write(data) }
    }

    private fun write(data: JokeData) {
        val tmp = File(file.parentFile, "jokes.json.tmp")
        tmp.writeText(json.encodeToString(JokeData.serializer(), data))
        tmp.renameTo(file)
    }
}
