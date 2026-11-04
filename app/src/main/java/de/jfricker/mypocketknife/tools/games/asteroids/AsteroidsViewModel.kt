package de.jfricker.mypocketknife.tools.games.asteroids

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jfricker.mypocketknife.tools.games.Game
import de.jfricker.mypocketknife.tools.games.GameOver
import de.jfricker.mypocketknife.tools.games.HighScoreStore
import de.jfricker.mypocketknife.tools.games.ScoreEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AsteroidsUiState(
    val running: Boolean = false,
    val started: Boolean = false,
    val score: Int = 0,
    val lives: Int = AsteroidsEngine.START_LIVES,
    val wave: Int = 1,
    val sound: Boolean = true,
    val gameOver: GameOver? = null,
    val showGameOver: Boolean = false,
) {
    val inProgress get() = started && gameOver == null
}

class AsteroidsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HighScoreStore.get(app)
    private val prefs = app.getSharedPreferences("asteroids", Context.MODE_PRIVATE)
    private val vibrator = app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private var sound: AsteroidsSound? = null

    /** Laufende Partie; wird jedes Bild direkt gezeichnet. */
    var engine = AsteroidsEngine()
        private set

    private val _state = MutableStateFlow(AsteroidsUiState(sound = prefs.getBoolean("sound", true)))
    val state: StateFlow<AsteroidsUiState> = _state.asStateFlow()
    val highScores: StateFlow<Map<String, List<ScoreEntry>>> = store.scores

    // Knöpfe (werden pro Bild gelesen)
    @Volatile var left = false
    @Volatile var right = false
    @Volatile var thrust = false
    @Volatile var fire = false
    /** Tipp ins Spielfeld = Hyperraum (wird beim nächsten Bild einmal ausgelöst). */
    @Volatile private var hyperRequested = false

    fun hyperspace() {
        if (_state.value.running) hyperRequested = true
    }

    private var recorded = false

    init {
        viewModelScope.launch { store.load() }
    }

    fun start() {
        if (_state.value.gameOver != null) newGame()
        if (_state.value.sound && sound == null) sound = AsteroidsSound()
        _state.update { it.copy(running = true, started = true) }
    }

    fun pause() = _state.update { it.copy(running = false) }

    fun newGame() {
        engine = AsteroidsEngine()
        recorded = false
        _state.update { AsteroidsUiState(sound = it.sound) }
    }

    fun dismissGameOver() = _state.update { it.copy(showGameOver = false) }

    fun toggleSound() {
        val on = !_state.value.sound
        prefs.edit { putBoolean("sound", on) }
        if (on) {
            if (sound == null) sound = AsteroidsSound()
        } else {
            sound?.release()
            sound = null
        }
        _state.update { it.copy(sound = on) }
    }

    fun tick(dt: Float) {
        val s = _state.value
        if (!s.running) return
        val e = engine
        val hyper = hyperRequested
        hyperRequested = false
        e.step(dt, AsControls(left, right, thrust, fire, hyper))

        if (e.events.isNotEmpty()) {
            if (s.sound) e.events.forEach { sound?.play(it) }
            if (AsSfx.SHIP_EXPLODE in e.events) {
                runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)) }
            }
        }
        if (s.score != e.score || s.lives != e.lives || s.wave != e.wave) {
            _state.update { it.copy(score = e.score, lives = e.lives, wave = e.wave) }
        }
        if (e.gameOver && !recorded) {
            recorded = true
            _state.update { it.copy(running = false) }
            viewModelScope.launch {
                val at = System.currentTimeMillis()
                val rank = store.add(Game.ASTEROIDS, e.score, at)
                _state.update {
                    it.copy(gameOver = GameOver(e.score, rank, at, "Bis Welle ${e.wave} gekommen"), showGameOver = true)
                }
            }
        }
    }

    override fun onCleared() {
        sound?.release()
    }
}
