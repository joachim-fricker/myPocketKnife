package de.jfricker.mypocketknife

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.jfricker.mypocketknife.home.HomeScreen
import de.jfricker.mypocketknife.home.Tool
import de.jfricker.mypocketknife.tools.currency.CurrencyScreen
import de.jfricker.mypocketknife.tools.dice.DiceGamesScreen
import de.jfricker.mypocketknife.tools.games.GamesScreen
import de.jfricker.mypocketknife.tools.incline.InclineScreen
import de.jfricker.mypocketknife.tools.joke.JokeScreen
import de.jfricker.mypocketknife.tools.moon.MoonScreen
import de.jfricker.mypocketknife.tools.sun.SunScreen
import de.jfricker.mypocketknife.tools.todo.TodoScreen
import de.jfricker.mypocketknife.ui.theme.MyPocketKnifeTheme

class MainActivity : ComponentActivity() {

    /** Lautstärketasten an das gerade offene Werkzeug weiterreichen (z. B. Neigungsmesser). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = HardwareKeys.onVolumeKey
        if (handler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.repeatCount == 0) handler()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPocketKnifeTheme {
                PocketKnifeApp()
            }
        }
    }
}

/**
 * Einfache Navigation: Startbildschirm oder ein Werkzeug.
 * Neues Werkzeug = Eintrag in [Tool] + ein Zweig im when-Block.
 */
@Composable
fun PocketKnifeApp() {
    var current by rememberSaveable { mutableStateOf<Tool?>(null) }
    val back = { current = null }

    BackHandler(enabled = current != null) { back() }

    when (current) {
        null -> HomeScreen(onOpen = { current = it })
        Tool.CURRENCY -> CurrencyScreen(onBack = back)
        Tool.MOON -> MoonScreen(onBack = back)
        Tool.SUN -> SunScreen(onBack = back)
        Tool.INCLINE -> InclineScreen(onBack = back)
        Tool.JOKE -> JokeScreen(onBack = back)
        Tool.TODO -> TodoScreen(onBack = back)
        Tool.GAMES -> GamesScreen(onBack = back)
        Tool.DICE -> DiceGamesScreen(onBack = back)
    }
}
