package com.emanueloperes.paciencia.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emanueloperes.paciencia.data.GameStore
import com.emanueloperes.paciencia.data.Settings
import com.emanueloperes.paciencia.data.StoredGame
import com.emanueloperes.paciencia.engine.Difficulty
import com.emanueloperes.paciencia.engine.GameSession
import com.emanueloperes.paciencia.engine.Hints
import com.emanueloperes.paciencia.engine.Move
import com.emanueloperes.paciencia.engine.Rules
import com.emanueloperes.paciencia.engine.SolvableSeeds
import com.emanueloperes.paciencia.engine.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Acontecimentos pontuais da partida, usados para dar retorno tátil. */
enum class GameEvent { MOVED, DEALT, UNDONE, HINT, SET_COMPLETED, WON, INVALID }

data class GameUiState(
    val loaded: Boolean = false,
    val session: GameSession? = null,
    val elapsedSeconds: Long = 0,
    /** Dica em destaque na mesa, até a próxima jogada. */
    val hint: Move? = null,
    val message: String? = null,
    val solvableOnly: Boolean = true,
    /** Jogadas que terminam a partida, quando só falta agrupar as cartas; habilita "Completar". */
    val autoComplete: List<Move>? = null,
    /** O app está jogando sozinho o final da partida: toques e botões ficam parados. */
    val autoPlaying: Boolean = false,
) {
    val canContinue: Boolean get() = session != null && !session.state.isWon
}

class GameViewModel(private val store: GameStore, private val settings: Settings) : ViewModel() {
    private val _state = MutableStateFlow(GameUiState(solvableOnly = settings.solvableOnly))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val session: GameSession? get() = _state.value.session

    // Cronômetro: só corre com a mesa visível e a partida em andamento.
    private var active = false
    private var clockStart: TimeMark? = null
    private var elapsedBefore = 0L
    private var ticker: Job? = null

    private var hintIndex = 0
    private var autoCompleteJob: Job? = null

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    /** Último pedido de gravação; null dentro do pedido significa apagar a partida salva. */
    private data class SaveRequest(val game: StoredGame?)
    private val saves = MutableStateFlow<SaveRequest?>(null)

    init {
        viewModelScope.launch {
            val stored = store.load()
            val restored = stored?.let { runCatching { GameSession.restore(it.game) }.getOrNull() }
            if (restored != null && session == null) {
                elapsedBefore = stored.elapsedMillis
                _state.update { it.copy(session = restored) }
                checkAutoComplete()
            }
            _state.update { it.copy(loaded = true) }
            updateClock()
        }
        viewModelScope.launch {
            // StateFlow descarta pedidos intermediários: grava sempre o mais recente, em ordem.
            saves.filterNotNull().collect { request ->
                if (request.game != null) store.save(request.game) else store.clear()
            }
        }
    }

    fun newGame(difficulty: Difficulty) {
        val solvable = SolvableSeeds.forDifficulty(difficulty)
        val previous = session?.state?.seed
        val seed = if (_state.value.solvableOnly && solvable.isNotEmpty()) {
            // Evita repetir a partida que acabou de ser jogada.
            solvable.filter { it.toLong() != previous }.ifEmpty { solvable.toList() }.random().toLong()
        } else {
            Random.nextLong(1, 1_000_000)
        }
        startGame(difficulty, seed)
    }

    fun setSolvableOnly(enabled: Boolean) {
        settings.solvableOnly = enabled
        _state.update { it.copy(solvableOnly = enabled) }
    }

    private fun startGame(difficulty: Difficulty, seed: Long) {
        replaceSession(GameSession.start(seed, difficulty))
    }

    fun restart() {
        val current = session ?: return
        startGame(current.state.difficulty, current.state.seed)
    }

    /** Toque numa carta: move para o melhor destino, se houver. */
    fun tap(column: Int, index: Int) {
        if (_state.value.autoPlaying) return
        val current = session ?: return
        val target = Hints.bestTarget(current.state, column, index)
        if (target == null) {
            _events.tryEmit(GameEvent.INVALID)
            return
        }
        play(Move.Transfer(column, index, target))
    }

    /** Soltar cartas arrastadas sobre uma coluna; movimento ilegal é ignorado. */
    fun drop(from: Int, index: Int, to: Int) {
        if (_state.value.autoPlaying) return
        val current = session ?: return
        val move = Move.Transfer(from, index, to)
        if (Rules.isLegal(current.state, move)) play(move) else _events.tryEmit(GameEvent.INVALID)
    }

    fun deal() {
        if (_state.value.autoPlaying) return
        val current = session ?: return
        if (Rules.canDeal(current.state)) play(Move.Deal) else _events.tryEmit(GameEvent.INVALID)
    }

    fun undo() {
        if (_state.value.autoPlaying) return
        val current = session ?: return
        if (!current.canUndo) return
        hintIndex = 0
        _state.update { it.copy(session = current.undo(), hint = null) }
        _events.tryEmit(GameEvent.UNDONE)
        checkAutoComplete()
        requestSave()
    }

    /** Joga sozinho o final da partida, uma jogada por vez, com as animações normais. */
    fun autoComplete() {
        val moves = _state.value.autoComplete ?: return
        _state.update { it.copy(autoComplete = null, autoPlaying = true, hint = null) }
        viewModelScope.launch {
            for (move in moves) {
                val current = session ?: break
                if (!Rules.isLegal(current.state, move)) break
                play(move)
                // Dá tempo de a carta pousar; fechar uma sequência anima por mais tempo.
                delay(if (session?.state?.completed?.size != current.state.completed.size) 650 else 260)
            }
            _state.update { it.copy(autoPlaying = false) }
            checkAutoComplete()
        }
    }

    /** Mostra a próxima dica; tocar de novo passa para a seguinte. */
    fun hint() {
        if (_state.value.autoPlaying) return
        val current = session ?: return
        val suggestions = Hints.suggestions(current.state)
        if (suggestions.isEmpty()) {
            _events.tryEmit(GameEvent.INVALID)
            showMessage("Não há jogadas úteis. Desfaça ou comece outra partida.")
            return
        }
        _state.update { it.copy(hint = suggestions[hintIndex % suggestions.size]) }
        _events.tryEmit(GameEvent.HINT)
        hintIndex++
    }

    fun setActive(active: Boolean) {
        this.active = active
        updateClock()
        if (!active) requestSave()
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun play(move: Move) {
        val current = session ?: return
        hintIndex = 0
        val next = current.play(move)
        _state.update { it.copy(session = next, hint = null) }
        _events.tryEmit(
            when {
                next.state.isWon -> GameEvent.WON
                next.state.completed.size > current.state.completed.size -> GameEvent.SET_COMPLETED
                move == Move.Deal -> GameEvent.DEALT
                else -> GameEvent.MOVED
            },
        )
        updateClock()
        checkAutoComplete()
        requestSave()
    }

    /**
     * Com o monte vazio e todas as cartas à mostra, procura em segundo plano uma solução para
     * o resto; se achar, aparece o botão "Completar". Qualquer jogada refaz a busca.
     */
    private fun checkAutoComplete() {
        autoCompleteJob?.cancel()
        if (_state.value.autoPlaying) return
        val position = session?.state
        val endgame = position != null && !position.isWon && position.stock.isEmpty() &&
            position.tableau.all { column -> column.all { it.faceUp } }
        if (!endgame) {
            _state.update { it.copy(autoComplete = null) }
            return
        }
        autoCompleteJob = viewModelScope.launch {
            val solution = withContext(Dispatchers.Default) { Solver.solve(position!!, 20_000, 5).solution }
            if (session?.state === position) _state.update { it.copy(autoComplete = solution) }
        }
    }

    private fun replaceSession(newSession: GameSession) {
        ticker?.cancel()
        clockStart = null
        elapsedBefore = 0
        hintIndex = 0
        _state.update { it.copy(session = newSession, hint = null, elapsedSeconds = 0) }
        updateClock()
        checkAutoComplete()
        requestSave()
    }

    private fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    private fun elapsedMillis(): Long = elapsedBefore + (clockStart?.elapsedNow()?.inWholeMilliseconds ?: 0)

    private fun updateClock() {
        val running = active && session?.state?.isWon == false
        if (running && clockStart == null) {
            clockStart = TimeSource.Monotonic.markNow()
            ticker = viewModelScope.launch {
                while (true) {
                    _state.update { it.copy(elapsedSeconds = elapsedMillis() / 1000) }
                    delay(1000 - elapsedMillis() % 1000)
                }
            }
        } else if (!running && clockStart != null) {
            elapsedBefore = elapsedMillis()
            clockStart = null
            ticker?.cancel()
        }
        _state.update { it.copy(elapsedSeconds = elapsedMillis() / 1000) }
    }

    private fun requestSave() {
        val current = session ?: return
        // Partida vencida não precisa ser retomada.
        val game = if (current.state.isWon) null else StoredGame(current.save(), elapsedMillis())
        saves.value = SaveRequest(game)
    }

    companion object {
        fun factory(store: GameStore, settings: Settings) = viewModelFactory {
            initializer { GameViewModel(store, settings) }
        }
    }
}
