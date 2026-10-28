package com.emanueloperes.paciencia.engine

/**
 * Procura uma sequência de jogadas até a vitória, conhecendo todas as cartas (inclusive as
 * viradas para baixo). É uma busca em profundidade que tenta primeiro as jogadas que deixam
 * a mesa mais organizada, com vários recomeços curtos: cada tentativa tem um limite de
 * posições e uma pequena variação aleatória na ordem, para não gastar tudo numa linha
 * que começou mal. Usada para montar a lista de partidas com solução; não roda no jogo.
 *
 * Um resultado sem solução significa só que a busca desistiu: a partida ainda pode ter solução.
 */
object Solver {
    // Pesos da avaliação; ajustáveis por propriedade do sistema para experimentar.
    private fun weight(name: String, default: Int) = System.getProperty("solver.$name")?.toInt() ?: default
    private val NOISE = weight("noise", 150)
    private val W_EMPTY = weight("empty", 600)
    private val W_MOVABLE = weight("movable", 15)
    private val W_FACE_DOWN = weight("faceDown", 50)
    private val W_BREAK = weight("break", 25)
    private val W_SAME_SUIT = weight("sameSuit", 60)
    private val W_OTHER_SUIT = weight("otherSuit", 0)

    class Result(val solution: List<Move>?, val nodes: Int) {
        val solved: Boolean get() = solution != null
    }

    /** Esforço usado na geração da lista: posições por tentativa e número de tentativas. */
    fun budgetFor(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.FOUR_SUITS -> 5_000 to 200
        else -> 20_000 to 10
    }

    fun solve(start: GameState, difficulty: Difficulty = start.difficulty): Result {
        val (nodes, attempts) = budgetFor(difficulty)
        return solve(start, nodes, attempts)
    }

    fun solve(start: GameState, nodesPerAttempt: Int, attempts: Int = 1): Result {
        var total = 0
        for (attempt in 0 until attempts) {
            // A primeira tentativa segue a avaliação pura; as seguintes embaralham um pouco.
            val noise = if (attempt == 0) null else java.util.Random(start.seed * 31 + attempt)
            val result = search(start, nodesPerAttempt, noise)
            total += result.nodes
            if (result.solved) return Result(result.solution, total)
        }
        return Result(null, total)
    }

    private fun search(start: GameState, maxNodes: Int, noise: java.util.Random?): Result {
        val visited = HashSet<Long>()
        val path = ArrayList<Move>()
        var nodes = 0

        fun dfs(state: GameState): Boolean {
            if (state.isWon) return true
            if (++nodes > maxNodes || !visited.add(key(state))) return false
            for (move in ordered(state, noise)) {
                path.add(move)
                if (dfs(Rules.apply(state, move))) return true
                path.removeAt(path.lastIndex)
                if (nodes > maxNodes) return false
            }
            return false
        }

        val solved = dfs(start)
        return Result(if (solved) path.toList() else null, nodes)
    }

    /** Jogadas candidatas da mais promissora para a menos; distribuir sempre por último. */
    private fun ordered(state: GameState, noise: java.util.Random?): List<Move> {
        val transfers = candidates(state)
            .map { move -> move to evaluate(Rules.apply(state, move)) + (noise?.nextInt(NOISE) ?: 0) }
            .sortedByDescending { it.second }
            .map { it.first }
        return if (Rules.canDeal(state)) transfers + Move.Deal else transfers
    }

    /**
     * Transferências que valem a pena tentar. Mais ampla que as dicas: com vários naipes, às vezes
     * é preciso tirar uma carta de cima de outra de naipe diferente só para liberar o lugar.
     */
    private fun candidates(state: GameState): List<Move.Transfer> {
        val seenEmpty = HashSet<Pair<Int, Int>>()
        return Rules.legalMoves(state).filterIsInstance<Move.Transfer>().filter { move ->
            val source = state.tableau[move.from]
            val card = source[move.index]
            val below = source.getOrNull(move.index - 1)
            val target = state.tableau[move.to]
            when {
                // Coluna inteira para coluna vazia não muda nada.
                target.isEmpty() && below == null -> false
                // Todas as colunas vazias são equivalentes: tenta só uma.
                target.isEmpty() -> seenEmpty.add(move.from to move.index)
                // Separar uma sequência do mesmo naipe nunca ajuda.
                below != null && below.faceUp && below.rank == card.rank + 1 && below.suit == card.suit -> false
                else -> true
            }
        }
    }

    /**
     * Quão perto da vitória a posição parece: sequências fechadas valem muito; cartas viradas
     * para baixo e "quebras" na coluna (carta fora de ordem) pesam contra; sequências do mesmo
     * naipe no topo, que podem ser movidas inteiras, e colunas vazias contam a favor.
     */
    private fun evaluate(state: GameState): Int {
        var value = state.completed.size * 1000
        for (column in state.tableau) {
            if (column.isEmpty()) {
                value += W_EMPTY
                continue
            }
            var movable = 1
            while (movable < column.size && Rules.isRun(column, column.size - movable - 1)) movable++
            value += movable * W_MOVABLE
            for (i in column.indices) {
                val card = column[i]
                if (!card.faceUp) {
                    value -= W_FACE_DOWN
                    continue
                }
                val above = column.getOrNull(i - 1) ?: continue
                value += when {
                    !above.faceUp -> 0
                    above.rank != card.rank + 1 -> -W_BREAK
                    above.suit == card.suit -> W_SAME_SUIT
                    else -> W_OTHER_SUIT
                }
            }
        }
        return value
    }

    /**
     * Identifica a posição pelo valor e naipe das cartas (não pelo id), para que duas
     * cartas iguais trocadas de lugar contem como a mesma posição.
     */
    private fun key(state: GameState): Long {
        var hash = state.stock.size.toLong()
        for (column in state.tableau) {
            for (card in column) {
                val code = card.rank * 8 + card.suit.ordinal * 2 + if (card.faceUp) 1 else 0
                hash = hash * 1_000_003 + code
            }
            hash = hash * 1_000_003 + 255
        }
        return hash
    }
}
