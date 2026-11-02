package de.jfricker.mypocketknife.tools.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoUiState(
    val data: TodoData = TodoData(),
    val loaded: Boolean = false,
    /** Zustand vor der letzten Löschaktion – für "Rückgängig" */
    val undo: TodoData? = null,
    val undoMessage: String? = null,
) {
    val current: TodoList? get() = data.lists.firstOrNull { it.id == data.selectedId } ?: data.lists.firstOrNull()
    val open: List<TodoItem> get() = current?.items.orEmpty().filter { !it.done }
    val done: List<TodoItem> get() = current?.items.orEmpty().filter { it.done }.sortedByDescending { it.doneAt ?: 0 }

    /** Vorschläge passend zum eingetippten Text, ohne Einträge, die schon offen sind. */
    fun suggestions(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val openTexts = open.map { it.text.lowercase() }.toSet()
        return current?.history.orEmpty()
            .filter { it.contains(q, ignoreCase = true) && it.lowercase() !in openTexts && !it.equals(q, ignoreCase = true) }
            .take(6)
    }
}

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val store = TodoStore(app)
    private val _state = MutableStateFlow(TodoUiState())
    val state: StateFlow<TodoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = store.load()
            _state.update { it.copy(data = data, loaded = true) }
        }
    }

    // ---------- Listen ----------

    fun select(id: Long) = change { it.copy(selectedId = id) }

    fun addList(name: String) {
        val n = name.trim().ifEmpty { return }
        val id = newId()
        change { it.copy(lists = it.lists + TodoList(id = id, name = n), selectedId = id) }
    }

    fun renameList(name: String) {
        val n = name.trim().ifEmpty { return }
        updateCurrent { it.copy(name = n) }
    }

    fun deleteList() {
        val s = _state.value
        val cur = s.current ?: return
        if (s.data.lists.size <= 1) return
        snapshotForUndo("Liste „${cur.name}“ gelöscht")
        change { d ->
            val rest = d.lists.filterNot { it.id == cur.id }
            d.copy(lists = rest, selectedId = rest.first().id)
        }
    }

    // ---------- Einträge ----------

    fun add(text: String) {
        val t = text.trim().ifEmpty { return }
        // Gleicher Eintrag schon erledigt? Dann einfach wieder öffnen.
        val existingDone = _state.value.done.firstOrNull { it.text.equals(t, ignoreCase = true) }
        updateCurrent { list ->
            val items = if (existingDone != null)
                list.items.map { if (it.id == existingDone.id) it.copy(done = false, doneAt = null) else it }
            else list.items + TodoItem(id = newId(), text = t, createdAt = System.currentTimeMillis())
            val history = (listOf(t) + list.history.filterNot { it.equals(t, ignoreCase = true) }).take(300)
            list.copy(items = items, history = history)
        }
    }

    fun toggle(id: Long) = updateCurrent { list ->
        list.copy(items = list.items.map {
            if (it.id == id) it.copy(done = !it.done, doneAt = if (!it.done) System.currentTimeMillis() else null) else it
        })
    }

    fun delete(id: Long) {
        val text = _state.value.current?.items?.firstOrNull { it.id == id }?.text ?: return
        snapshotForUndo("„$text“ gelöscht")
        updateCurrent { list -> list.copy(items = list.items.filterNot { it.id == id }) }
    }

    fun clearDone() {
        val n = _state.value.done.size
        if (n == 0) return
        snapshotForUndo("$n erledigte gelöscht")
        updateCurrent { list -> list.copy(items = list.items.filterNot { it.done }) }
    }

    fun undo() {
        val prev = _state.value.undo ?: return
        _state.update { it.copy(data = prev, undo = null, undoMessage = null) }
        persist()
    }

    fun undoShown() = _state.update { it.copy(undoMessage = null) }

    /** Liste als Text, z. B. zum Teilen per WhatsApp. */
    fun shareText(): String {
        val s = _state.value
        val cur = s.current ?: return ""
        return buildString {
            append(cur.name).append('\n')
            s.open.forEach { append("☐ ").append(it.text).append('\n') }
        }.trim()
    }

    // ---------- intern ----------

    private fun snapshotForUndo(message: String) =
        _state.update { it.copy(undo = it.data, undoMessage = message) }

    private fun updateCurrent(f: (TodoList) -> TodoList) {
        val id = _state.value.current?.id ?: return
        change { d -> d.copy(lists = d.lists.map { if (it.id == id) f(it) else it }) }
    }

    private fun change(f: (TodoData) -> TodoData) {
        _state.update { it.copy(data = f(it.data)) }
        persist()
    }

    /** Ein einziger Hintergrund-Thread: Speichervorgänge laufen strikt nacheinander. */
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    private fun persist() {
        val data = _state.value.data
        viewModelScope.launch(saveDispatcher) { store.saveBlocking(data) }
    }

    private var lastId = 0L
    private fun newId(): Long {
        val t = System.currentTimeMillis()
        lastId = if (t > lastId) t else lastId + 1
        return lastId
    }
}
