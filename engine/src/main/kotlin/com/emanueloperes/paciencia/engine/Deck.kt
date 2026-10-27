package com.emanueloperes.paciencia.engine

object Deck {
    const val SIZE = 104

    /** Baralho em ordem: 8 sequências de Ás a Rei, repartidas entre os naipes da dificuldade. */
    fun create(difficulty: Difficulty): List<Card> {
        val copies = Rules.SETS / difficulty.suits.size
        var id = 0
        return buildList {
            repeat(copies) {
                for (suit in difficulty.suits) {
                    for (rank in Card.ACE..Card.KING) add(Card(id++, rank, suit))
                }
            }
        }
    }

    /** Embaralhamento determinístico: a mesma semente sempre gera a mesma partida. */
    fun shuffled(seed: Long, difficulty: Difficulty): List<Card> {
        val cards = create(difficulty).toMutableList()
        val random = SeededRandom(seed)
        for (i in cards.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            cards[i] = cards[j].also { cards[j] = cards[i] }
        }
        return cards
    }
}

/**
 * SplitMix64. Implementado aqui em vez de usar kotlin.random para que a sequência
 * não mude entre versões do Kotlin e as partidas numeradas continuem iguais.
 */
internal class SeededRandom(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += 0x9E3779B97F4A7C15uL.toLong()
        var z = state
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }
}
