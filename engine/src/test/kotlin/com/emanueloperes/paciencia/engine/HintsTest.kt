package com.emanueloperes.paciencia.engine

import com.emanueloperes.paciencia.engine.Move.Deal
import com.emanueloperes.paciencia.engine.Move.Transfer
import com.emanueloperes.paciencia.engine.Suit.HEARTS
import com.emanueloperes.paciencia.engine.Suit.SPADES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintsTest {
    @Test
    fun `completar sequência vem primeiro`() {
        val s = state(run(13, 2), listOf(card(1)), listOf(card(8, up = false), card(4, HEARTS)), listOf(card(5, HEARTS)))
        assertEquals(Transfer(1, 0, 0), Hints.suggestions(s).first())
    }

    @Test
    fun `mesmo naipe é preferido a outro naipe`() {
        val s = state(listOf(card(7)), listOf(card(8, HEARTS)), listOf(card(8, SPADES)))
        assertEquals(Transfer(0, 0, 2), Hints.suggestions(s).first())
    }

    @Test
    fun `não sugere quebrar sequência do mesmo naipe`() {
        val s = state(run(9, 8), listOf(card(9, HEARTS)))
        assertFalse(Transfer(0, 1, 1) in Hints.suggestions(s))
    }

    @Test
    fun `não sugere levar coluna inteira para coluna vazia`() {
        val s = state(listOf(card(5)), emptyList())
        assertFalse(Hints.suggestions(s).any { it is Transfer && it.from == 0 && it.to == 1 })
    }

    @Test
    fun `distribuir fica por último`() {
        val stock = List(10) { card(1, up = false) }
        val s = state(listOf(card(7)), listOf(card(8)), stock = stock)
        val hints = Hints.suggestions(s)
        assertEquals(Deal, hints.last())
        assertTrue(hints.size > 1)
    }

    @Test
    fun `sem jogadas úteis a lista fica vazia`() {
        val s = state(*Array(10) { listOf(card(1, up = false), card(5)) }, fillMissing = false)
        assertTrue(Hints.suggestions(s).isEmpty())
    }

    @Test
    fun `toque prefere mesmo naipe, depois outro naipe, depois coluna vazia`() {
        val s = state(listOf(card(9, up = false), card(7)), listOf(card(8, HEARTS)), listOf(card(8)), emptyList())
        assertEquals(2, Hints.bestTarget(s, 0, 1))
        val s2 = state(listOf(card(9, up = false), card(7)), listOf(card(8, HEARTS)), emptyList())
        assertEquals(1, Hints.bestTarget(s2, 0, 1))
        val s3 = state(listOf(card(9, up = false), card(7)), emptyList())
        assertEquals(1, Hints.bestTarget(s3, 0, 1))
    }

    @Test
    fun `toque sem destino ou em carta virada retorna null`() {
        val s = state(listOf(card(9, up = false), card(7)), listOf(card(3)))
        assertNull(Hints.bestTarget(s, 0, 1))
        assertNull(Hints.bestTarget(s, 0, 0))
        // Coluna inteira não vai para coluna vazia.
        assertNull(Hints.bestTarget(state(listOf(card(5)), emptyList()), 0, 0))
    }
}
