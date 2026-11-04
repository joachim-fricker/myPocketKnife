package de.jfricker.mypocketknife.tools.games

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class HighScoreData(val scores: Map<String, List<ScoreEntry>> = emptyMap())

/**
 * Bestenlisten aller Spiele (je Top 5 mit Zeitpunkt) in `files/highscores.json`.
 * Eine gemeinsame Instanz für Spiele-Übersicht und Spiele, damit alle denselben Stand sehen.
 */
class HighScoreStore private constructor(context: Context) {

    private val file = File(context.filesDir, "highscores.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Mutex()
    private var loaded = false

    private val _scores = MutableStateFlow<Map<String, List<ScoreEntry>>>(emptyMap())
    val scores: StateFlow<Map<String, List<ScoreEntry>>> = _scores.asStateFlow()

    suspend fun load() = lock.withLock { ensureLoaded() }

    /** Trägt ein Ergebnis ein. Liefert den Platz (1…5) oder null, wenn es nicht für die Liste reicht. */
    suspend fun add(game: Game, score: Int, at: Long = System.currentTimeMillis()): Int? = add(game.key, score, at)

    /** Wie oben, mit freiem Schlüssel (z. B. Würfelspiele) und optionalem Spielernamen. */
    suspend fun add(key: String, score: Int, at: Long, name: String? = null, lowerIsBetter: Boolean = false): Int? = lock.withLock {
        ensureLoaded()
        val all = _scores.value
        val (list, rank) = ScoreBoard.insert(all[key].orEmpty(), ScoreEntry(score, at, name), lowerIsBetter)
        if (rank != null) {
            val updated = all + (key to list)
            _scores.value = updated
            write(updated)
        }
        rank
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _scores.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyMap()
            else runCatching { json.decodeFromString<HighScoreData>(file.readText()).scores }.getOrDefault(emptyMap())
        }
        loaded = true
    }

    private suspend fun write(scores: Map<String, List<ScoreEntry>>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "highscores.json.tmp")
        tmp.writeText(json.encodeToString(HighScoreData.serializer(), HighScoreData(scores)))
        tmp.renameTo(file)
    }

    companion object {
        @Volatile
        private var instance: HighScoreStore? = null

        fun get(context: Context): HighScoreStore =
            instance ?: synchronized(this) {
                instance ?: HighScoreStore(context.applicationContext).also { instance = it }
            }
    }
}
