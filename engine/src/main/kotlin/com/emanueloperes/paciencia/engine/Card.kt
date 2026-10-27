package com.emanueloperes.paciencia.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val isRed: Boolean) {
    SPADES(false),
    HEARTS(true),
    DIAMONDS(true),
    CLUBS(false),
}

/** Uma carta do baralho. [id] é único entre as 104 cartas e estável durante a partida. */
@Serializable
data class Card(
    val id: Int,
    val rank: Int,
    val suit: Suit,
    val faceUp: Boolean = false,
) {
    init {
        require(rank in ACE..KING) { "Valor inválido: $rank" }
    }

    fun faceUp(): Card = if (faceUp) this else copy(faceUp = true)

    fun faceDown(): Card = if (faceUp) copy(faceUp = false) else this

    companion object {
        const val ACE = 1
        const val KING = 13
    }
}

@Serializable
enum class Difficulty(val suits: List<Suit>) {
    ONE_SUIT(listOf(Suit.SPADES)),
    TWO_SUITS(listOf(Suit.SPADES, Suit.HEARTS)),
    FOUR_SUITS(listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS)),
}
