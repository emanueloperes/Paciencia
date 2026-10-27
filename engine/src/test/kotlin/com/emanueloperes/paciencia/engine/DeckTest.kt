package com.emanueloperes.paciencia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeckTest {
    @Test
    fun `cada dificuldade tem 104 cartas com ids únicos`() {
        for (difficulty in Difficulty.entries) {
            val deck = Deck.create(difficulty)
            assertEquals(Deck.SIZE, deck.size)
            assertEquals(Deck.SIZE, deck.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `naipes são repartidos igualmente`() {
        assertEquals(mapOf(Suit.SPADES to 104), Deck.create(Difficulty.ONE_SUIT).groupingBy { it.suit }.eachCount())
        assertEquals(
            mapOf(Suit.SPADES to 52, Suit.HEARTS to 52),
            Deck.create(Difficulty.TWO_SUITS).groupingBy { it.suit }.eachCount(),
        )
        assertEquals(
            Suit.entries.associateWith { 26 },
            Deck.create(Difficulty.FOUR_SUITS).groupingBy { it.suit }.eachCount(),
        )
    }

    @Test
    fun `cada valor aparece oito vezes`() {
        val counts = Deck.create(Difficulty.FOUR_SUITS).groupingBy { it.rank }.eachCount()
        assertEquals((1..13).associateWith { 8 }, counts)
    }

    @Test
    fun `embaralhamento é determinístico pela semente`() {
        assertEquals(Deck.shuffled(42, Difficulty.FOUR_SUITS), Deck.shuffled(42, Difficulty.FOUR_SUITS))
        assertNotEquals(Deck.shuffled(42, Difficulty.FOUR_SUITS), Deck.shuffled(43, Difficulty.FOUR_SUITS))
    }

    @Test
    fun `embaralhamento mantém as mesmas cartas`() {
        val shuffled = Deck.shuffled(7, Difficulty.TWO_SUITS)
        assertEquals(Deck.create(Difficulty.TWO_SUITS).toSet(), shuffled.toSet())
    }

    @Test
    fun `sequência do gerador é fixa`() {
        // Protege as partidas numeradas contra mudanças acidentais no gerador.
        val random = SeededRandom(1234)
        assertEquals(listOf(7, 2, 3, 9, 4), List(5) { random.nextInt(10) })
    }
}
