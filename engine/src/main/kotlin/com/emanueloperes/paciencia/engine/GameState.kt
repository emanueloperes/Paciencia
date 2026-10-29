package com.emanueloperes.paciencia.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val difficulty: Difficulty,
    val seed: Long,
    /** As 10 colunas da mesa; o último elemento de cada lista é a carta de cima. */
    val tableau: List<List<Card>>,
    /** Monte: cada distribuição tira as 10 primeiras cartas, uma para cada coluna (vazias inclusive). */
    val stock: List<Card>,
    /** Naipes das sequências completas, na ordem em que saíram da mesa. */
    val completed: List<Suit> = emptyList(),
    val score: Int = Scoring.INITIAL,
    val moves: Int = 0,
) {
    val dealsLeft: Int get() = stock.size / Rules.COLUMNS

    val isWon: Boolean get() = completed.size == Rules.SETS
}

@Serializable
sealed interface Move {
    /** Move as cartas de [from] a partir de [index] (inclusive) para o topo de [to]. */
    @Serializable
    @SerialName("transfer")
    data class Transfer(val from: Int, val index: Int, val to: Int) : Move

    /** Distribui uma carta do monte em cada coluna. */
    @Serializable
    @SerialName("deal")
    data object Deal : Move
}

object Scoring {
    const val INITIAL = 500
    const val MOVE_COST = 1
    const val UNDO_COST = 1
    const val SET_BONUS = 100
}
