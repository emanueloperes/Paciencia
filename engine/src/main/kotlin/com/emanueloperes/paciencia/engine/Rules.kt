package com.emanueloperes.paciencia.engine

object Rules {
    const val COLUMNS = 10
    const val SETS = 8
    const val SET_LENGTH = 13

    private val INITIAL_COLUMN_SIZES = listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5)

    fun newGame(seed: Long, difficulty: Difficulty): GameState {
        val deck = Deck.shuffled(seed, difficulty)
        var next = 0
        val tableau = INITIAL_COLUMN_SIZES.map { size ->
            val column = deck.subList(next, next + size)
            next += size
            column.mapIndexed { i, card -> if (i == size - 1) card.faceUp() else card }
        }
        return GameState(difficulty, seed, tableau, stock = deck.drop(next))
    }

    /** Verdadeiro se as cartas de [from] até o topo formam uma sequência decrescente do mesmo naipe. */
    fun isRun(cards: List<Card>, from: Int): Boolean {
        if (from !in cards.indices) return false
        for (i in from until cards.size) {
            val card = cards[i]
            if (!card.faceUp) return false
            if (i > from) {
                val above = cards[i - 1]
                if (card.suit != above.suit || card.rank != above.rank - 1) return false
            }
        }
        return true
    }

    fun canPickUp(state: GameState, column: Int, index: Int): Boolean =
        column in 0 until COLUMNS && isRun(state.tableau[column], index)

    fun canDrop(state: GameState, card: Card, to: Int): Boolean {
        val target = state.tableau[to]
        return target.isEmpty() || target.last().rank == card.rank + 1
    }

    fun canDeal(state: GameState): Boolean =
        state.stock.isNotEmpty() && state.tableau.none { it.isEmpty() }

    fun isLegal(state: GameState, move: Move): Boolean = when (move) {
        Move.Deal -> canDeal(state)
        is Move.Transfer ->
            move.to in 0 until COLUMNS &&
                move.from != move.to &&
                canPickUp(state, move.from, move.index) &&
                canDrop(state, state.tableau[move.from][move.index], move.to)
    }

    fun legalMoves(state: GameState): List<Move> = buildList {
        for (from in 0 until COLUMNS) {
            val column = state.tableau[from]
            // Percorre de cima para baixo enquanto a sequência continua válida.
            var index = column.lastIndex
            while (index >= 0 && isRun(column, index)) {
                for (to in 0 until COLUMNS) {
                    if (to != from && canDrop(state, column[index], to)) add(Move.Transfer(from, index, to))
                }
                index--
            }
        }
        if (canDeal(state)) add(Move.Deal)
    }

    /** Aplica [move], revelando cartas e retirando sequências completas. Lança exceção se for ilegal. */
    fun apply(state: GameState, move: Move): GameState {
        require(isLegal(state, move)) { "Movimento inválido: $move" }
        val tableau = state.tableau.toMutableList()
        val touched: List<Int>
        val stock: List<Card>
        when (move) {
            is Move.Transfer -> {
                val source = tableau[move.from]
                tableau[move.to] = tableau[move.to] + source.subList(move.index, source.size)
                tableau[move.from] = revealTop(source.subList(0, move.index))
                touched = listOf(move.to)
                stock = state.stock
            }
            Move.Deal -> {
                for (i in 0 until COLUMNS) tableau[i] = tableau[i] + state.stock[i].faceUp()
                touched = (0 until COLUMNS).toList()
                stock = state.stock.drop(COLUMNS)
            }
        }
        var completed = state.completed
        var score = state.score - Scoring.MOVE_COST
        for (i in touched) {
            val column = tableau[i]
            val start = column.size - SET_LENGTH
            if (start >= 0 && column[start].rank == Card.KING && isRun(column, start)) {
                completed = completed + column[start].suit
                score += Scoring.SET_BONUS
                tableau[i] = revealTop(column.subList(0, start))
            }
        }
        return state.copy(
            tableau = tableau,
            stock = stock,
            completed = completed,
            score = score,
            moves = state.moves + 1,
        )
    }

    private fun revealTop(cards: List<Card>): List<Card> =
        if (cards.isEmpty() || cards.last().faceUp) cards.toList()
        else cards.dropLast(1) + cards.last().faceUp()
}
