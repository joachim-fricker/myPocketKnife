package de.jfricker.mypocketknife.tools.todo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TodoItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long,
    val doneAt: Long? = null,
)

@Serializable
data class TodoList(
    val id: Long,
    val name: String,
    val items: List<TodoItem> = emptyList(),
    /** Früher eingegebene Texte (neueste zuerst) – für Vorschläge */
    val history: List<String> = emptyList(),
)

@Serializable
data class TodoData(
    val lists: List<TodoList> = listOf(
        TodoList(id = 1, name = "Einkauf"),
        TodoList(id = 2, name = "Todo"),
    ),
    val selectedId: Long = 1,
)

/** Alle Listen in einer JSON-Datei im App-Speicher. */
class TodoStore(context: Context) {

    private val file = File(context.filesDir, "todo.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun load(): TodoData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext TodoData()
        runCatching { json.decodeFromString<TodoData>(file.readText()) }.getOrDefault(TodoData())
    }

    /** Blockierend schreiben – Aufrufer sorgt für Hintergrund-Thread und Reihenfolge. */
    fun saveBlocking(data: TodoData) {
        val tmp = File(file.parentFile, "todo.json.tmp")
        tmp.writeText(json.encodeToString(TodoData.serializer(), data))
        tmp.renameTo(file)
    }
}
