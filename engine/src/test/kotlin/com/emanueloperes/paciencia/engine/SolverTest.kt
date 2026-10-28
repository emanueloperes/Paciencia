package com.emanueloperes.paciencia.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class SolverTest {
    @Test
    fun `solução encontrada leva à vitória`() {
        for (seed in 1L..5L) {
            val start = Rules.newGame(seed, Difficulty.ONE_SUIT)
            val result = Solver.solve(start)
            val solution = result.solution ?: continue
            val end = solution.fold(start) { state, move -> Rules.apply(state, move) }
            assertTrue(end.isWon, "semente $seed: a solução não termina em vitória")
        }
    }

    @Test
    fun `partidas da lista têm solução`() {
        // Confere as primeiras de cada lista; a lista inteira é verificada na geração.
        for (difficulty in Difficulty.entries) {
            for (seed in SolvableSeeds.forDifficulty(difficulty).take(2)) {
                val start = Rules.newGame(seed.toLong(), difficulty)
                val solution = Solver.solve(start).solution
                assertTrue(solution != null, "partida $seed (${difficulty.name}) deveria ter solução")
                assertTrue(solution.fold(start) { state, move -> Rules.apply(state, move) }.isWon)
            }
        }
    }
}
