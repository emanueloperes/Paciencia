package com.emanueloperes.paciencia.engine

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Gera SolvableSeeds.kt: sorteia números de partida, roda o Solver em cada um e guarda os
 * que ele resolveu, conferindo que a solução termina em vitória. Rode com
 * ./gradlew :engine:generateSolvableSeeds (leva alguns minutos, a maior parte em 4 naipes).
 */
fun main(args: Array<String>) {
    val output = File(args[0])
    val wanted = mapOf(Difficulty.ONE_SUIT to 500, Difficulty.TWO_SUITS to 500, Difficulty.FOUR_SUITS to 200)
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val lists = wanted.mapValues { (difficulty, count) ->
        val started = System.currentTimeMillis()
        val candidates = java.util.Random(2026L + difficulty.ordinal)
        val found = sortedSetOf<Int>()
        var tried = 0
        while (found.size < count) {
            val batch = List(64) { candidates.nextInt(999_999) + 1 }.filter { it !in found }
            val results: List<Pair<Int, Future<Boolean>>> = batch.map { seed ->
                seed to pool.submit<Boolean> {
                    val start = Rules.newGame(seed.toLong(), difficulty)
                    val solution = Solver.solve(start).solution ?: return@submit false
                    check(solution.fold(start) { state, move -> Rules.apply(state, move) }.isWon)
                    true
                }
            }
            for ((seed, result) in results) {
                tried++
                if (result.get() && found.size < count) found.add(seed)
            }
            System.err.println("${difficulty.name}: ${found.size}/$count resolvidas de $tried testadas")
        }
        val seconds = (System.currentTimeMillis() - started) / 1000
        println("${difficulty.name}: ${found.size} de $tried testadas em ${seconds}s")
        Triple(found.toIntArray(), tried, seconds)
    }
    pool.shutdown()

    fun array(name: String, difficulty: Difficulty): String {
        val (seeds, tried, _) = lists.getValue(difficulty)
        val body = seeds.toList().chunked(10).joinToString(",\n") { line -> "        " + line.joinToString(", ") }
        return "    // ${seeds.size} resolvidas de $tried testadas.\n    private val $name = intArrayOf(\n$body,\n    )"
    }
    output.writeText(
        """
        |package com.emanueloperes.paciencia.engine
        |
        |/**
        | * Números de partidas que o [Solver] resolveu, por dificuldade.
        | * Gerado por ./gradlew :engine:generateSolvableSeeds — não edite à mão.
        | */
        |object SolvableSeeds {
        |${array("ONE_SUIT", Difficulty.ONE_SUIT)}
        |
        |${array("TWO_SUITS", Difficulty.TWO_SUITS)}
        |
        |${array("FOUR_SUITS", Difficulty.FOUR_SUITS)}
        |
        |    fun forDifficulty(difficulty: Difficulty): IntArray = when (difficulty) {
        |        Difficulty.ONE_SUIT -> ONE_SUIT
        |        Difficulty.TWO_SUITS -> TWO_SUITS
        |        Difficulty.FOUR_SUITS -> FOUR_SUITS
        |    }
        |}
        |
        """.trimMargin(),
    )
}
