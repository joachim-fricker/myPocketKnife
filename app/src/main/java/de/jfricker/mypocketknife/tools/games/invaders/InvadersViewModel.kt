package de.jfricker.mypocketknife.tools.games.invaders

import android.app.Application
import android.os.VibrationEffect
import android.os.VibratorManager
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

data class InvadersUiState(
    val running: Boolean = false,
    val started: Boolean = false,
    val score: Int = 0,
    val lives: Int = InvadersEngine.START_LIVES,
    val wave: Int = 1,
    val gameOver: GameOver? = null,
    val showGameOver: Boolean = false,
    val settings: InvaderSettings = InvaderSettings(),
) {
    val inProgress get() = started && gameOver == null
}

class InvadersViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HighScoreStore.get(app)
    private val settingsStore = InvaderSettingsStore(app)
    private val vibrator = app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private var sound: InvaderSound? = null

    /** Die laufende Partie; wird jedes Bild direkt gezeichnet (kein Compose-State). */
    var engine = InvadersEngine()
        private set

    private val _state = MutableStateFlow(InvadersUiState(settings = settingsStore.load()))
    val state: StateFlow<InvadersUiState> = _state.asStateFlow()
    val highScores: StateFlow<Map<String, List<ScoreEntry>>> = store.scores

    // Eingaben aus der Oberfläche (werden pro Bild gelesen)
    @Volatile var touching = false
    @Volatile var touchX: Float? = null
    @Volatile var leftPressed = false
    @Volatile var rightPressed = false
    @Volatile var firePressed = false

    private var recorded = false

    init {
        viewModelScope.launch { store.load() }
    }

    fun start() {
        if (_state.value.gameOver != null) newGame()
        if (_state.value.settings.sound && sound == null) sound = InvaderSound()
        _state.update { it.copy(running = true, started = true) }
    }

    fun pause() = _state.update { it.copy(running = false) }

    fun newGame() {
        engine = InvadersEngine()
        recorded = false
        _state.update { InvadersUiState(settings = it.settings) }
    }

    fun dismissGameOver() = _state.update { it.copy(showGameOver = false) }

    fun updateSettings(s: InvaderSettings) {
        settingsStore.save(s)
        if (!s.sound) {
            sound?.release()
            sound = null
        }
        _state.update { it.copy(settings = s) }
    }

    /** Ein Bild weiterrechnen. */
    fun tick(dt: Float) {
        val s = _state.value
        if (!s.running) return
        val settings = s.settings
        val fire = settings.autoFire || when (settings.mode) {
            ControlMode.DRAG -> touching
            ControlMode.BUTTONS -> firePressed
        }
        val controls = when (settings.mode) {
            ControlMode.DRAG -> Controls(targetX = touchX, fire = fire)
            ControlMode.BUTTONS -> Controls(axis = (if (rightPressed) 1f else 0f) - (if (leftPressed) 1f else 0f), fire = fire)
        }
        val e = engine
        e.step(dt, controls)

        if (e.events.isNotEmpty()) {
            if (settings.sound) e.events.forEach { sound?.play(it, e.stepNote) }
            if (settings.vibration && Sfx.PLAYER_HIT in e.events) {
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
                val rank = store.add(Game.INVADERS, e.score, at)
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
