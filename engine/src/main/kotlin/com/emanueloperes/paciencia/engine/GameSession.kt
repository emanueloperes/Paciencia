package com.emanueloperes.paciencia.engine

import kotlinx.serialization.Serializable

/** O que é preciso para reconstruir uma partida: a semente e os movimentos feitos. */
@Serializable
data class SavedGame(
    val seed: Long,
    val difficulty: Difficulty,
    val moves: List<Move>,
    val undoCount: Int = 0,
)

/**
 * Uma partida em andamento com desfazer ilimitado.
 *
 * Desfazer custa [Scoring.UNDO_COST] pontos, descontados em [score]. A pontuação dos
 * estados não é restaurada para evitar ganhar o bônus de uma sequência duas vezes.
 */
class GameSession private constructor(
    private val history: List<GameState>,
    val moves: List<Move>,
    val undoCount: Int,
) {
    val state: GameState get() = history.last()

    val score: Int get() = state.score - undoCount * Scoring.UNDO_COST

    val canUndo: Boolean get() = history.size > 1

    fun play(move: Move): GameSession =
        GameSession(history + Rules.apply(state, move), moves + move, undoCount)

    fun undo(): GameSession {
        check(canUndo) { "Nada para desfazer" }
        return GameSession(history.dropLast(1), moves.dropLast(1), undoCount + 1)
    }

    fun save(): SavedGame = SavedGame(state.seed, state.difficulty, moves, undoCount)

    companion object {
        fun start(seed: Long, difficulty: Difficulty): GameSession =
            GameSession(listOf(Rules.newGame(seed, difficulty)), emptyList(), 0)

        fun restore(saved: SavedGame): GameSession {
            val replayed = saved.moves.fold(start(saved.seed, saved.difficulty)) { session, move -> session.play(move) }
            return GameSession(replayed.history, replayed.moves, saved.undoCount)
        }
    }
}
