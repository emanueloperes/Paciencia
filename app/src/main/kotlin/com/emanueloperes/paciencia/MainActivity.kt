package com.emanueloperes.paciencia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emanueloperes.paciencia.ui.game.GameScreen
import com.emanueloperes.paciencia.ui.game.GameViewModel
import com.emanueloperes.paciencia.ui.home.HomeScreen
import com.emanueloperes.paciencia.ui.theme.PacienciaTheme

private enum class Screen { HOME, GAME }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = (application as PacienciaApp).gameStore
        setContent {
            PacienciaTheme {
                val game: GameViewModel = viewModel(factory = GameViewModel.factory(store))
                val state by game.state.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        savedGame = state.session?.state?.difficulty?.takeIf { state.canContinue },
                        onContinue = { screen = Screen.GAME },
                        onNewGame = { difficulty ->
                            game.newGame(difficulty)
                            screen = Screen.GAME
                        },
                    )
                    Screen.GAME -> GameScreen(
                        viewModel = game,
                        onExit = { screen = Screen.HOME },
                        onNewGame = game::newGame,
                    )
                }
            }
        }
    }
}
