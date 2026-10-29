package com.emanueloperes.paciencia.engine

import com.emanueloperes.paciencia.engine.Move.Deal
import com.emanueloperes.paciencia.engine.Move.Transfer
import com.emanueloperes.paciencia.engine.Suit.HEARTS
import com.emanueloperes.paciencia.engine.Suit.SPADES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun `nova partida distribui 54 cartas e deixa 50 no monte`() {
        val game = Rules.newGame(1, Difficulty.FOUR_SUITS)
        assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), game.tableau.map { it.size })
        assertEquals(50, game.stock.size)
        assertEquals(5, game.dealsLeft)
        assertEquals(Scoring.INITIAL, game.score)
        for (column in game.tableau) {
            assertTrue(column.last().faceUp)
            assertTrue(column.dropLast(1).none { it.faceUp })
        }
        assertTrue(game.stock.none { it.faceUp })
    }

    @Test
    fun `carta vai sobre outra de valor imediatamente superior de qualquer naipe`() {
        val s = state(listOf(card(7, HEARTS)), listOf(card(8, SPADES)))
        assertTrue(Rules.isLegal(s, Transfer(0, 0, 1)))
        val s2 = state(listOf(card(7)), listOf(card(9)))
        assertFalse(Rules.isLegal(s2, Transfer(0, 0, 1)))
    }

    @Test
    fun `sequência do mesmo naipe se move junta`() {
        val s = state(run(7, 5), listOf(card(8, HEARTS)))
        val after = Rules.apply(s, Transfer(0, 0, 1))
        assertEquals(listOf(8, 7, 6, 5), after.tableau[1].map { it.rank })
        assertTrue(after.tableau[0].isEmpty())
    }

    @Test
    fun `sequência de naipes misturados não se move junta`() {
        val s = state(listOf(card(7, SPADES), card(6, HEARTS)), listOf(card(8)))
        assertFalse(Rules.isLegal(s, Transfer(0, 0, 1)))
        assertFalse(Rules.canPickUp(s, 0, 0))
        assertTrue(Rules.canPickUp(s, 0, 1))
    }

    @Test
    fun `carta virada para baixo não pode ser movida`() {
        val s = state(listOf(card(7, up = false), card(3)), listOf(card(8)))
        assertFalse(Rules.isLegal(s, Transfer(0, 0, 1)))
    }

    @Test
    fun `qualquer sequência válida vai para coluna vazia`() {
        val s = state(listOf(card(1, up = false)) + run(9, 6), emptyList())
        assertTrue(Rules.isLegal(s, Transfer(0, 1, 1)))
    }

    @Test
    fun `mover para a mesma coluna ou fora da mesa é ilegal`() {
        val s = state(listOf(card(5)))
        assertFalse(Rules.isLegal(s, Transfer(0, 0, 0)))
        assertFalse(Rules.isLegal(s, Transfer(0, 0, 10)))
        assertFalse(Rules.isLegal(s, Transfer(0, 3, 1)))
        assertFalse(Rules.isLegal(s, Transfer(-1, 0, 1)))
    }

    @Test
    fun `aplicar movimento ilegal lança exceção`() {
        val s = state(listOf(card(5)), listOf(card(9)))
        assertFailsWith<IllegalArgumentException> { Rules.apply(s, Transfer(0, 0, 1)) }
    }

    @Test
    fun `carta de baixo é revelada ao mover`() {
        val s = state(listOf(card(10, up = false), card(4)), listOf(card(5)))
        val after = Rules.apply(s, Transfer(0, 1, 1))
        assertTrue(after.tableau[0].single().faceUp)
    }

    @Test
    fun `movimento custa um ponto e conta como jogada`() {
        val s = state(listOf(card(4)), listOf(card(5)))
        val after = Rules.apply(s, Transfer(0, 0, 1))
        assertEquals(Scoring.INITIAL - 1, after.score)
        assertEquals(1, after.moves)
    }

    @Test
    fun `sequência completa sai da mesa e revela a carta de baixo`() {
        val s = state(listOf(card(2, HEARTS, up = false)) + run(13, 2), listOf(card(1)))
        val after = Rules.apply(s, Transfer(1, 0, 0))
        assertEquals(listOf(SPADES), after.completed)
        assertEquals(1, after.tableau[0].size)
        assertTrue(after.tableau[0].single().faceUp)
        assertEquals(Scoring.INITIAL - 1 + Scoring.SET_BONUS, after.score)
    }

    @Test
    fun `sequência completa de naipes misturados não sai`() {
        val mixed = run(13, 8) + run(7, 2, HEARTS)
        val s = state(mixed, listOf(card(1, HEARTS)))
        val after = Rules.apply(s, Transfer(1, 0, 0))
        assertTrue(after.completed.isEmpty())
        assertEquals(13, after.tableau[0].size)
    }

    @Test
    fun `distribuir coloca uma carta virada em cada coluna`() {
        val stock = List(20) { card(it % 13 + 1, up = false) }
        val s = state(stock = stock)
        val after = Rules.apply(s, Deal)
        assertEquals(10, after.stock.size)
        for (i in 0 until Rules.COLUMNS) {
            assertEquals(stock[i].id, after.tableau[i].last().id)
            assertTrue(after.tableau[i].last().faceUp)
        }
    }

    @Test
    fun `distribuir só depende de haver monte`() {
        val stock = List(10) { card(1, up = false) }
        assertFalse(Rules.canDeal(state()))
        assertTrue(Rules.canDeal(state(stock = stock)))
        assertTrue(Rules.canDeal(state(emptyList(), stock = stock)))
    }

    @Test
    fun `coluna vazia recebe carta ao distribuir`() {
        val stock = List(10) { card(it % 13 + 1, up = false) }
        val after = Rules.apply(state(emptyList(), stock = stock), Deal)
        assertEquals(listOf(stock[0].id), after.tableau[0].map { it.id })
        assertTrue(after.tableau[0].single().faceUp)
    }

    @Test
    fun `distribuir pode completar uma sequência`() {
        val stock = listOf(card(1, up = false)) + List(9) { card(13, HEARTS, up = false) }
        val s = state(run(13, 2), stock = stock)
        val after = Rules.apply(s, Deal)
        assertEquals(listOf(SPADES), after.completed)
        assertTrue(after.tableau[0].isEmpty())
    }

    @Test
    fun `vitória com oito sequências completas`() {
        val s = state(listOf(card(1)), run(13, 2), completed = List(7) { SPADES }, fillMissing = false)
        assertFalse(s.isWon)
        assertTrue(Rules.apply(s, Transfer(0, 0, 1)).isWon)
    }

    @Test
    fun `movimentos legais incluem sequências de vários tamanhos e distribuir`() {
        val stock = List(10) { card(1, up = false) }
        val s = state(run(6, 4), listOf(card(7)), listOf(card(6, HEARTS)), stock = stock)
        val moves = Rules.legalMoves(s)
        assertTrue(Transfer(0, 0, 1) in moves)
        assertTrue(Transfer(0, 1, 2) in moves)
        assertTrue(Deal in moves)
        assertTrue(moves.all { Rules.isLegal(s, it) })
    }
}
