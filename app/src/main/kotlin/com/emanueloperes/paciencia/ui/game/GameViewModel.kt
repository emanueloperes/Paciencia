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
        val current = session ?: return
        val move = Move.Transfer(from, index, to)
        if (Rules.isLegal(current.state, move)) play(move) else _events.tryEmit(GameEvent.INVALID)
    }

    fun deal() {
        val current = session ?: return
        if (Rules.canDeal(current.state)) {
            play(Move.Deal)
        } else if (current.state.stock.isNotEmpty()) {
            _events.tryEmit(GameEvent.INVALID)
            showMessage("Preencha as colunas vazias antes de distribuir")
        }
    }

    fun undo() {
        val current = session ?: return
        if (!current.canUndo) return
        hintIndex = 0
        _state.update { it.copy(session = current.undo(), hint = null) }
        _events.tryEmit(GameEvent.UNDONE)
        requestSave()
    }

    /** Mostra a próxima dica; tocar de novo passa para a seguinte. */
    fun hint() {
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
        requestSave()
    }

    private fun replaceSession(newSession: GameSession) {
        ticker?.cancel()
        clockStart = null
        elapsedBefore = 0
        hintIndex = 0
        _state.update { it.copy(session = newSession, hint = null, elapsedSeconds = 0) }
        updateClock()
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
