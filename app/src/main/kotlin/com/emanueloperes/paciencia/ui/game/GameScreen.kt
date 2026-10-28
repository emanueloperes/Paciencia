package com.emanueloperes.paciencia.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emanueloperes.paciencia.engine.Difficulty
import com.emanueloperes.paciencia.engine.GameSession
import com.emanueloperes.paciencia.engine.SolvableSeeds
import com.emanueloperes.paciencia.ui.home.label
import com.emanueloperes.paciencia.ui.theme.LocalTableColors
import com.emanueloperes.paciencia.ui.theme.TabularNumbers
import com.emanueloperes.paciencia.ui.theme.VerdeAcinzentado
import com.emanueloperes.paciencia.ui.theme.VerdeFloresta

@Composable
fun GameScreen(viewModel: GameViewModel, onExit: () -> Unit, onNewGame: (Difficulty) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(onBack = onExit)
    LifecycleResumeEffect(viewModel) {
        viewModel.setActive(true)
        onPauseOrDispose { viewModel.setActive(false) }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            haptics.performHapticFeedback(
                when (event) {
                    GameEvent.MOVED -> HapticFeedbackType.SegmentTick
                    GameEvent.DEALT, GameEvent.HINT -> HapticFeedbackType.ContextClick
                    GameEvent.UNDONE -> HapticFeedbackType.VirtualKey
                    GameEvent.SET_COMPLETED, GameEvent.WON -> HapticFeedbackType.Confirm
                    GameEvent.INVALID -> HapticFeedbackType.Reject
                },
            )
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val session = state.session ?: return
    val game = session.state
    Scaffold(
        containerColor = LocalTableColors.current.table,
        topBar = {
            TopBar(
                session = session,
                elapsedSeconds = state.elapsedSeconds,
                onBack = onExit,
                onRestart = viewModel::restart,
                onNewGame = { onNewGame(game.difficulty) },
            )
        },
        bottomBar = {
            BottomBar(
                canUndo = session.canUndo,
                dealsLeft = game.dealsLeft,
                onUndo = viewModel::undo,
                onHint = viewModel::hint,
                onDeal = viewModel::deal,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Table(
            state = game,
            hint = state.hint,
            onCardTap = viewModel::tap,
            onCardDrop = viewModel::drop,
            onStockTap = viewModel::deal,
            modifier = Modifier.padding(padding),
        )
    }

    if (game.isWon) {
        WinDialog(
            score = session.score,
            moves = game.moves,
            elapsedSeconds = state.elapsedSeconds,
            onNewGame = { onNewGame(game.difficulty) },
            onExit = onExit,
        )
    }
}

@Composable
private fun TopBar(
    session: GameSession,
    elapsedSeconds: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onNewGame: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = LocalTableColors.current.bar) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar ao início")
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Pontos", session.score.toString())
                Stat("Jogadas", session.state.moves.toString())
                Stat("Tempo", formatTime(elapsedSeconds))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            val solvable = SolvableSeeds.forDifficulty(session.state.difficulty).binarySearch(session.state.seed.toInt()) >= 0
                            Text("Partida nº ${session.state.seed} · ${session.state.difficulty.label}" + if (solvable) " · com solução" else "")
                        },
                        onClick = {},
                        enabled = false,
                    )
                    DropdownMenuItem(text = { Text("Reiniciar esta partida") }, onClick = { menuOpen = false; onRestart() })
                    DropdownMenuItem(text = { Text("Nova partida") }, onClick = { menuOpen = false; onNewGame() })
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BottomBar(
    canUndo: Boolean,
    dealsLeft: Int,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onDeal: () -> Unit,
) {
    Surface(color = LocalTableColors.current.bar) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(Icons.AutoMirrored.Filled.Undo, "Desfazer", canUndo, onUndo)
            BarAction(Icons.Filled.Lightbulb, "Dica", true, onHint)
            BarAction(Icons.Filled.Style, "Distribuir", dealsLeft > 0, onDeal, badge = dealsLeft)
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, badge: Int? = null) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else VerdeAcinzentado
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(badge = {
                if (badge != null && badge > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(badge.toString())
                    }
                }
            }) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun WinDialog(score: Int, moves: Int, elapsedSeconds: Long, onNewGame: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = VerdeFloresta,
        title = { Text("Você venceu!") },
        text = { Text("$score pontos · $moves jogadas · ${formatTime(elapsedSeconds)}", style = TabularNumbers) },
        confirmButton = { TextButton(onClick = onNewGame) { Text("Nova partida") } },
        dismissButton = { TextButton(onClick = onExit) { Text("Início") } },
    )
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
