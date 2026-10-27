package com.emanueloperes.paciencia.engine

private var nextId = 1000

/** Carta para testes; virada para cima por padrão. */
fun card(rank: Int, suit: Suit = Suit.SPADES, up: Boolean = true) = Card(nextId++, rank, suit, up)

/** Sequência decrescente do mesmo naipe, de [high] até [low], virada para cima. */
fun run(high: Int, low: Int, suit: Suit = Suit.SPADES) = (high downTo low).map { card(it, suit) }

/** Estado com as colunas dadas; as que faltarem ficam com uma carta virada para cima, para não ficarem vazias. */
fun state(
    vararg columns: List<Card>,
    stock: List<Card> = emptyList(),
    completed: List<Suit> = emptyList(),
    fillMissing: Boolean = true,
): GameState {
    val tableau = List(Rules.COLUMNS) { i ->
        columns.getOrNull(i) ?: if (fillMissing) listOf(card(Card.ACE, Suit.CLUBS, up = false), card(Card.ACE, Suit.CLUBS)) else emptyList()
    }
    return GameState(Difficulty.FOUR_SUITS, 0, tableau, stock, completed)
}

fun GameState.cardCount() = tableau.sumOf { it.size } + stock.size + completed.size * Rules.SET_LENGTH
