package de.jfricker.mypocketknife.tools.dice

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Laufende Partie je Regelwerk in `files/dice_<key>.json` (überlebt das Schliessen der App),
 * zuletzt verwendete Spielernamen in den SharedPreferences "dice".
 */
class DiceStore(context: Context) {

    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("dice", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private fun file(rules: RuleSet) = File(dir, "dice_${rules.key}.json")

    suspend fun load(rules: RuleSet): DiceState? = withContext(Dispatchers.IO) {
        val f = file(rules)
        if (!f.exists()) null
        else runCatching { json.decodeFromString<DiceState>(f.readText()) }.getOrNull()?.takeIf { it.rules == rules }
    }

    /** null = Partie entfernen. */
    suspend fun save(rules: RuleSet, state: DiceState?) = lock.withLock {
        withContext(Dispatchers.IO) {
            val f = file(rules)
            if (state == null) {
                f.delete()
            } else {
                val tmp = File(dir, "dice_${rules.key}.json.tmp")
                tmp.writeText(json.encodeToString(DiceState.serializer(), state))
                tmp.renameTo(f)
            }
        }
    }

    var playerCount: Int
        get() = prefs.getInt("count", 2).coerceIn(1, 4)
        set(v) = prefs.edit { putInt("count", v) }

    fun names(): List<String> = (0 until 4).map { prefs.getString("name$it", null) ?: "Spieler ${it + 1}" }

    fun saveNames(names: List<String>) = prefs.edit {
        names.forEachIndexed { i, n -> putString("name$i", n) }
    }
}
