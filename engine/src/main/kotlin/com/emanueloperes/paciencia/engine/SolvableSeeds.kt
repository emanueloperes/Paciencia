package com.emanueloperes.paciencia.engine

// Provisório: substituído pela lista gerada por ./gradlew :engine:generateSolvableSeeds.
object SolvableSeeds {
    private val ONE_SUIT = intArrayOf()
    private val TWO_SUITS = intArrayOf()
    private val FOUR_SUITS = intArrayOf()

    fun forDifficulty(difficulty: Difficulty): IntArray = when (difficulty) {
        Difficulty.ONE_SUIT -> ONE_SUIT
        Difficulty.TWO_SUITS -> TWO_SUITS
        Difficulty.FOUR_SUITS -> FOUR_SUITS
    }
}
