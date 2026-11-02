package de.jfricker.mypocketknife

import android.os.Bundle
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
import de.jfricker.mypocketknife.tools.moon.MoonScreen
import de.jfricker.mypocketknife.ui.theme.MyPocketKnifeTheme

class MainActivity : ComponentActivity() {
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
        // Witz des Tages folgt
        else -> HomeScreen(onOpen = { current = it })
    }
}
