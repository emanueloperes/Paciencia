package com.emanueloperes.paciencia.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Joga partidas aleatórias e confere invariantes que precisam valer sempre. */
class PropertyTest {
    @Test
    fun `invariantes valem em partidas aleatórias`() {
        for (seed in 0L until 200L) {
            val difficulty = Difficulty.entries[(seed % 3).toInt()]
            val random = Random(seed)
            var session = GameSession.start(seed, difficulty)
            val initial = session.state
            repeat(300) {
                val moves = Rules.legalMoves(session.state)
                if (moves.isEmpty()) return@repeat
                val before = session.state
                session = session.play(moves[random.nextInt(moves.size)])
                val state = session.state

                assertEquals(Deck.SIZE, state.cardCount(), "semente $seed: cartas sumiram ou duplicaram")
                val ids = allIds(state)
                assertEquals(ids.size, ids.toSet().size, "semente $seed: ids repetidos")
                assertTrue(state.tableau.all { it.isEmpty() || it.last().faceUp }, "semente $seed: topo virado para baixo")
                assertTrue(state.stock.size % Rules.COLUMNS == 0)
                assertEquals(before, session.undo().state, "semente $seed: desfazer não voltou ao estado anterior")
            }
            while (session.canUndo) session = session.undo()
            assertEquals(initial, session.state)
        }
    }

    @Test
    fun `seguindo as dicas a partida avança`() {
        var wins = 0
        for (seed in 0L until 50L) {
            var state = Rules.newGame(seed, Difficulty.ONE_SUIT)
            val seen = mutableSetOf<List<List<Card>>>()
            while (!state.isWon && seen.add(state.tableau)) {
                val hint = Hints.suggestions(state).firstOrNull() ?: break
                state = Rules.apply(state, hint)
            }
            if (state.isWon) wins++
        }
        // Hoje as dicas vencem 21 de 50; o piso avisa se uma mudança piorar muito a heurística.
        assertTrue(wins >= 15, "dicas venceram só $wins de 50 partidas")
    }

    private fun allIds(state: GameState) = state.tableau.flatten().map { it.id } + state.stock.map { it.id }
}
