package com.emanueloperes.paciencia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emanueloperes.paciencia.BuildConfig
import com.emanueloperes.paciencia.engine.Card
import com.emanueloperes.paciencia.engine.Difficulty
import com.emanueloperes.paciencia.engine.Suit
import com.emanueloperes.paciencia.ui.cards.PlayingCard
import com.emanueloperes.paciencia.ui.theme.LocalTableColors
import com.emanueloperes.paciencia.ui.theme.PacienciaTheme

@Composable
fun HomeScreen(
    savedGame: Difficulty?,
    onContinue: () -> Unit,
    onNewGame: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    solvableOnly: Boolean = true,
    onSolvableOnlyChange: (Boolean) -> Unit = {},
) {
    val colors = LocalTableColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.table)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CardFan()
        Spacer(Modifier.height(32.dp))
        Text("Paciência", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Text("Spider", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(40.dp))
        Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (savedGame != null) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.highlight, contentColor = colors.bar),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text("Continuar · ${savedGame.label}", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Novo jogo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            for (difficulty in Difficulty.entries) {
                Button(
                    onClick = { onNewGame(difficulty) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.bar, contentColor = colors.highlight),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(difficulty.label, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = solvableOnly, role = Role.Switch, onValueChange = onSolvableOnlyChange)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Só partidas com solução", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Sorteia entre partidas já resolvidas pelo app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
                Switch(checked = solvableOnly, onCheckedChange = null)
            }
        }
        Spacer(Modifier.height(24.dp))
        // Versão visível para conferir se a atualização foi instalada.
        Text(
            "Versão ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

val Difficulty.label: String
    get() = when (this) {
        Difficulty.ONE_SUIT -> "1 naipe"
        Difficulty.TWO_SUITS -> "2 naipes"
        Difficulty.FOUR_SUITS -> "4 naipes"
    }

/** Três cartas em leque, só para decorar a tela inicial. */
@Composable
private fun CardFan() {
    val width = 72.dp
    Box(contentAlignment = Alignment.Center) {
        PlayingCard(Card(0, 13, Suit.SPADES), width, Modifier.offset(x = (-40).dp).rotate(-12f))
        PlayingCard(Card(1, 1, Suit.SPADES, faceUp = true), width, Modifier.offset(x = 40.dp, y = 4.dp).rotate(12f))
        PlayingCard(Card(2, 12, Suit.HEARTS, faceUp = true), width, Modifier.offset(y = (-8).dp))
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    PacienciaTheme { HomeScreen(savedGame = Difficulty.TWO_SUITS, onContinue = {}, onNewGame = {}) }
}
