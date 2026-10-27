package com.emanueloperes.paciencia.engine

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GameSessionTest {
    @Test
    fun `desfazer volta ao estado anterior e custa um ponto`() {
        val start = GameSession.start(5, Difficulty.ONE_SUIT)
        val played = start.play(Move.Deal)
        val undone = played.undo()
        assertEquals(start.state, undone.state)
        assertEquals(Scoring.INITIAL - Scoring.UNDO_COST, undone.score)
        assertEquals(1, undone.undoCount)
    }

    @Test
    fun `não dá para desfazer no início`() {
        val start = GameSession.start(5, Difficulty.ONE_SUIT)
        assertFalse(start.canUndo)
        assertFailsWith<IllegalStateException> { start.undo() }
    }

    @Test
    fun `repetir a jogada depois de desfazer custa só o desfazer`() {
        val s = GameSession.start(1, Difficulty.ONE_SUIT)
        val played = s.play(Move.Deal)
        val score = played.undo().play(Move.Deal).score
        assertEquals(played.score - Scoring.UNDO_COST, score)
    }

    @Test
    fun `partida salva é reconstruída igual, inclusive depois de serializar`() {
        var session = GameSession.start(99, Difficulty.FOUR_SUITS)
        val random = Random(3)
        repeat(40) {
            val moves = Rules.legalMoves(session.state)
            if (moves.isEmpty()) return@repeat
            session = session.play(moves[random.nextInt(moves.size)])
            if (random.nextInt(5) == 0 && session.canUndo) session = session.undo()
        }
        val json = Json.encodeToString(SavedGame.serializer(), session.save())
        val restored = GameSession.restore(Json.decodeFromString(SavedGame.serializer(), json))
        assertEquals(session.state, restored.state)
        assertEquals(session.score, restored.score)
        assertEquals(session.moves, restored.moves)
    }
}
