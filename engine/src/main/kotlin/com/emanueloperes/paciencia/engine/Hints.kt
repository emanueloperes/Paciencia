package com.emanueloperes.paciencia.engine

object Hints {
    /**
     * Movimentos úteis em ordem de preferência; distribuir fica por último.
     * Lista vazia significa que não há mais jogadas que ajudem.
     */
    fun suggestions(state: GameState): List<Move> {
        val ranked = Rules.legalMoves(state)
            .filterIsInstance<Move.Transfer>()
            .mapNotNull { move -> value(state, move)?.let { move to it } }
            .sortedByDescending { it.second }
            .map { it.first }
        return if (Rules.canDeal(state)) ranked + Move.Deal else ranked
    }

    /**
     * Destino para o toque único em uma carta: primeiro uma coluna com o mesmo naipe no topo,
     * depois qualquer coluna que aceite a carta e, por último, uma coluna vazia.
     */
    fun bestTarget(state: GameState, from: Int, index: Int): Int? {
        if (!Rules.canPickUp(state, from, index)) return null
        val card = state.tableau[from][index]
        val targets = (0 until Rules.COLUMNS).filter { it != from && Rules.canDrop(state, card, it) }
        val (empty, filled) = targets.partition { state.tableau[it].isEmpty() }
        return filled.firstOrNull { state.tableau[it].last().suit == card.suit }
            ?: filled.firstOrNull()
            ?: empty.firstOrNull().takeIf { index > 0 }
    }

    /** Nota do movimento, ou null se ele não traz nenhum ganho. */
    private fun value(state: GameState, move: Move.Transfer): Int? {
        val source = state.tableau[move.from]
        val card = source[move.index]
        val target = state.tableau[move.to]
        val below = source.getOrNull(move.index - 1)
        val revealsCard = below != null && !below.faceUp
        val sitsOnParent = below != null && below.faceUp && below.rank == card.rank + 1
        val sameSuitTarget = target.lastOrNull()?.suit == card.suit

        if (target.isEmpty()) {
            // Ir para a coluna vazia só vale se descobrir algo que estava preso.
            return when {
                below == null || sitsOnParent -> null
                revealsCard -> 30
                else -> 10
            }
        }
        // Sair de um pai válido só vale para juntar com o mesmo naipe sem desfazer outra sequência.
        if (sitsOnParent && (below!!.suit == card.suit || !sameSuitTarget)) return null

        var value = source.size - move.index
        if (Rules.apply(state, move).completed.size > state.completed.size) value += 1000
        if (sameSuitTarget) value += 100
        if (revealsCard) value += 60
        if (below == null) value += 50
        return value
    }
}
