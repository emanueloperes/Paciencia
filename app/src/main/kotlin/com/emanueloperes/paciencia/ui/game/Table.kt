package com.emanueloperes.paciencia.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.emanueloperes.paciencia.engine.Card
import com.emanueloperes.paciencia.engine.GameState
import com.emanueloperes.paciencia.engine.Move
import com.emanueloperes.paciencia.engine.Rules
import com.emanueloperes.paciencia.ui.cards.CARD_ASPECT_RATIO
import com.emanueloperes.paciencia.ui.cards.PlayingCard
import com.emanueloperes.paciencia.ui.cards.cardShape
import com.emanueloperes.paciencia.ui.theme.LocalTableColors
import com.emanueloperes.paciencia.ui.theme.TabularNumbers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val Gap = 3.dp

// Curvas fixas e curtas: a carta chega rápido e desacelera no fim, sem passar do lugar.
private const val MOVE_MILLIS = 180
private val MoveSpec = tween<Dp>(durationMillis = MOVE_MILLIS, easing = FastOutSlowInEasing)
private val FlipSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
private val LiftSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)
private val HopSpec = tween<Float>(durationMillis = 170, easing = FastOutSlowInEasing)

/** Uma carta da mesa com a posição que ela deve ocupar. */
private data class PlacedCard(val card: Card, val column: Int, val index: Int, val position: DpOffset, val isTop: Boolean)

/**
 * Carta que acabou de sair da mesa e ainda está a caminho do destino: o monte (ao desfazer
 * uma distribuição) ou a pilha de sequências completas.
 *
 * Ao fechar uma sequência o caminho tem três trechos: a carta pousa na coluna ([landing]),
 * as 13 se juntam sobre o Rei ([gather]) e o bloco voa para a pilha ([to]).
 */
private data class Ghost(
    val serial: Int,
    val card: Card,
    val from: DpOffset,
    val to: DpOffset,
    val delayMillis: Int,
    val toStock: Boolean,
    val landing: DpOffset? = null,
    val gather: DpOffset? = null,
    /** Posição na pilha de completas que esta carta vai ocupar; -1 se volta ao monte. */
    val foundationIndex: Int = -1,
)

/** Memória da mesa entre composições, para descobrir quais cartas saíram e de onde. */
private class TableMemory {
    var lastState: GameState? = null
    var positions: Map<Int, PlacedCard> = emptyMap()
    val ghosts = mutableListOf<Ghost>()
    var serial = 0
}

/** Cartas sendo arrastadas: a partir de [index] na coluna [from], deslocadas [offset] pixels. */
private data class DragState(val from: Int, val index: Int, val offset: Offset)

/**
 * A mesa: conjuntos completos e monte em cima, as 10 colunas embaixo.
 * Todas as cartas são irmãs numa única lista com chave pelo id: assim, ao mudar
 * de coluna, a carta continua sendo o mesmo elemento e só anima a posição.
 */
@Composable
fun Table(
    state: GameState,
    hint: Move?,
    onCardTap: (column: Int, index: Int) -> Unit,
    onCardDrop: (from: Int, index: Int, to: Int) -> Unit,
    onStockTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<DragState?>(null) }
    val memory = remember { TableMemory() }
    // Muda quando um fantasma termina, para a mesa se recompor sem ele.
    var ghostTick by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp)) {
        val columns = Rules.COLUMNS
        // Em paisagem a altura limita o tamanho da carta; a mesa fica centralizada.
        val byWidth = (maxWidth - Gap * (columns - 1)) / columns
        val byHeight = maxHeight / 4.5f * CARD_ASPECT_RATIO
        val cardWidth = min(byWidth, byHeight)
        val cardHeight = cardWidth / CARD_ASPECT_RATIO
        val left = (maxWidth - cardWidth * columns - Gap * (columns - 1)) / 2
        fun columnX(column: Int) = left + (cardWidth + Gap) * column

        val tableauTop = cardHeight + 12.dp
        val stockPosition = DpOffset(columnX(columns - 1), 0.dp)
        val hintTransfer = hint as? Move.Transfer

        // Enquanto arrasta, destaca todas as colunas que aceitam as cartas.
        val dragTargets = drag?.let { d ->
            (0 until columns).filter { Rules.isLegal(state, Move.Transfer(d.from, d.index, it)) }.toSet()
        }.orEmpty()

        fun endDrag() {
            val d = drag ?: return
            drag = null
            val draggedX = columnX(d.from) + with(density) { d.offset.x.toDp() }
            val to = (0 until columns).minBy { abs((columnX(it) - draggedX).value) }
            when {
                to == d.from -> Unit // Soltou no mesmo lugar: só volta.
                Rules.isLegal(state, Move.Transfer(d.from, d.index, to)) -> onCardDrop(d.from, d.index, to)
                else -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }

        fun foundationPosition(count: Int) = DpOffset(columnX(0) + cardWidth * 0.3f * (count - 1).coerceAtLeast(0), 0.dp)

        val placed = buildList {
            for (column in 0 until columns) {
                val cards = state.tableau[column]
                val offsets = columnOffsets(cards, cardHeight, maxHeight - tableauTop)
                cards.forEachIndexed { index, card ->
                    val position = DpOffset(columnX(column), tableauTop + offsets[index])
                    add(PlacedCard(card, column, index, position, isTop = index == cards.lastIndex))
                }
            }
        }

        // Compara com a jogada anterior para animar as cartas que saíram da mesa.
        val previous = memory.lastState
        if (previous !== state) {
            // Só uma jogada de diferença (jogar ou desfazer); nova partida não anima saídas.
            val oneStep = previous != null && previous.seed == state.seed && abs(previous.moves - state.moves) == 1
            if (oneStep && state.stock.size > previous!!.stock.size) {
                // Desfazer distribuição: da direita para a esquerda, cada carta vira e volta ao monte.
                val ids = placed.mapTo(HashSet()) { it.card.id }
                val gone = memory.positions.values.filter { it.card.id !in ids }
                gone.sortedByDescending { it.column }.forEachIndexed { k, p ->
                    memory.ghosts += Ghost(memory.serial++, p.card, p.position, stockPosition, k * 45, toStock = true)
                }
            } else if (oneStep && state.moves > previous!!.moves && state.completed.size > previous.completed.size) {
                // Sequência fechada: a mesa como ficou logo depois da jogada, antes de a sequência sair.
                val ids = placed.mapTo(HashSet()) { it.card.id }
                val dealt = previous.stock.size > state.stock.size
                val gone = (previous.tableau.flatten() + if (dealt) previous.stock.take(columns) else emptyList())
                    .filter { it.id !in ids }
                    .mapTo(HashSet()) { it.id }
                val virtual = if (dealt) {
                    previous.tableau.mapIndexed { c, column -> column + previous.stock[c].faceUp() }
                } else {
                    val target = (0 until columns).first { c -> previous.tableau[c].any { it.id in gone && it.rank == Card.KING } }
                    val from = (0 until columns).first { c -> c != target && previous.tableau[c].any { it.id in gone } }
                    val moved = previous.tableau[from].filter { it.id in gone }
                    previous.tableau.mapIndexed { c, column ->
                        when (c) {
                            target -> column + moved
                            from -> column.filter { it.id !in gone }
                            else -> column
                        }
                    }
                }
                var foundationIndex = previous.completed.size
                for (c in 0 until columns) {
                    val column = virtual[c]
                    val run = column.filter { it.id in gone }
                    if (run.isEmpty()) continue
                    val offsets = columnOffsets(column, cardHeight, maxHeight - tableauTop)
                    val landings = run.map { card -> DpOffset(columnX(c), tableauTop + offsets[column.indexOf(card)]) }
                    run.forEachIndexed { k, card ->
                        memory.ghosts += Ghost(
                            serial = memory.serial++,
                            card = card,
                            from = memory.positions[card.id]?.position ?: stockPosition,
                            to = foundationPosition(foundationIndex + 1),
                            // No voo final o bloco sai em leque, de cima para baixo.
                            delayMillis = (run.size - 1 - k) * 12,
                            toStock = false,
                            landing = landings[k],
                            gather = landings.first(),
                            foundationIndex = foundationIndex,
                        )
                    }
                    foundationIndex++
                }
            }
            memory.lastState = state
            memory.positions = placed.associateBy { it.card.id }
        }
        // Cartas que voltam de uma sequência desfeita saem da pilha de completas.
        val enterFrom = if (previous != null && state.completed.size < previous.completed.size) {
            foundationPosition(previous.completed.size)
        } else {
            stockPosition
        }
        ghostTick.let { } // Lê o contador para recompor quando um fantasma termina.
        val returningToStock = memory.ghosts.any { it.toStock }
        // Sequência ainda a caminho da pilha: o Rei dela só aparece quando o bloco chega.
        val pendingSets = memory.ghosts.mapNotNullTo(HashSet()) { ghost -> ghost.foundationIndex.takeIf { it >= 0 } }
        val shownCompleted = state.completed.filterIndexed { i, _ -> i !in pendingSets }

        // Conjuntos completos: um Rei de cada, sobrepostos; o mais novo entra com um salto.
        shownCompleted.forEachIndexed { i, suit ->
            key("completed-$i") {
                val scale = remember { Animatable(1.4f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
                PlayingCard(
                    Card(id = -1 - i, rank = Card.KING, suit = suit, faceUp = true),
                    cardWidth,
                    Modifier
                        .offset(x = columnX(0) + cardWidth * 0.3f * i)
                        .zIndex(i.toFloat())
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                )
            }
        }
        if (shownCompleted.isNotEmpty()) {
            // Contador ao lado da pilha: quantas das 8 sequências já foram fechadas.
            Text(
                "${shownCompleted.size}/${Rules.SETS}",
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = LocalTableColors.current.highlight,
                modifier = Modifier.offset(
                    x = columnX(0) + cardWidth * 0.3f * (shownCompleted.size - 1) + cardWidth + 8.dp,
                    y = cardHeight / 2 - 12.dp,
                ),
            )
        }

        // Monte: um verso por distribuição restante, abrindo para a esquerda. Enquanto cartas
        // voltam de uma distribuição desfeita, o verso devolvido só aparece quando elas chegam.
        val deals = state.dealsLeft - if (returningToStock) 1 else 0
        if (deals == 0) {
            EmptySlot(cardWidth, stockPosition, highlighted = false)
        }
        repeat(deals) { i ->
            val isTop = i == deals - 1
            PlayingCard(
                state.stock[0].copy(faceUp = false),
                cardWidth,
                Modifier.offset(x = stockPosition.x - cardWidth * 0.2f * (deals - 1 - i)),
                glowing = isTop && hint == Move.Deal,
                onClick = if (isTop) onStockTap else null,
            )
        }

        for (column in 0 until columns) {
            val highlightSlot = state.tableau[column].isEmpty() && (hintTransfer?.to == column || column in dragTargets)
            EmptySlot(cardWidth, DpOffset(columnX(column), tableauTop), highlightSlot)
        }

        for (ghost in memory.ghosts.toList()) {
            key("ghost-${ghost.serial}") {
                GhostCard(ghost, cardWidth) {
                    memory.ghosts.remove(ghost)
                    ghostTick++
                }
            }
        }

        for (p in placed) {
            key(p.card.id) {
                val dragged = drag?.let { it.from == p.column && p.index >= it.index } == true
                val hintedSource = hintTransfer?.let { it.from == p.column && p.index >= it.index } == true
                val hintedTarget = hintTransfer?.to == p.column && p.isTop
                AnimatedCard(
                    card = p.card,
                    width = cardWidth,
                    target = p.position,
                    enterFrom = enterFrom,
                    // Na mesa inicial as cartas chegam em leque; ao distribuir, uma coluna por vez.
                    enterDelayMillis = p.column * 30 + if (state.moves == 0) p.index * 60 else 0,
                    z = p.index,
                    highlighted = p.column in dragTargets && p.isTop,
                    hinted = hintedSource || hintedTarget,
                    // O destino pula logo depois das cartas sugeridas, como se as recebesse.
                    hintDelayMillis = if (hintedTarget) 140 else 0,
                    dimmed = p.card.faceUp && !Rules.canPickUp(state, p.column, p.index),
                    dragOffset = if (dragged) drag?.offset else null,
                    onClick = { onCardTap(p.column, p.index) },
                    onDragStart = {
                        if (Rules.canPickUp(state, p.column, p.index)) {
                            drag = DragState(p.column, p.index, Offset.Zero)
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    },
                    onDrag = { delta -> drag = drag?.let { it.copy(offset = it.offset + delta) } },
                    onDragEnd = { endDrag() },
                    onDragCancel = { drag = null },
                )
            }
        }
    }
}

/**
 * Deslocamento vertical de cada carta da coluna. Cartas viradas para baixo ficam
 * mais juntas; se a coluna não couber, o espaçamento todo é comprimido.
 */
private fun columnOffsets(cards: List<Card>, cardHeight: Dp, available: Dp): List<Dp> {
    val down = cardHeight * 0.16f
    // Passo das cartas abertas: deixa o valor grande inteiro à mostra, mesmo com a sombra da carta de cima.
    val up = cardHeight * 0.42f
    val natural = cards.dropLast(1).fold(0.dp) { acc, card -> acc + if (card.faceUp) up else down }
    val room = available - cardHeight
    val scale = if (natural > room && natural > 0.dp) (room / natural).coerceAtLeast(0.05f) else 1f
    var y = 0.dp
    return cards.map { card ->
        y.also { y += (if (card.faceUp) up else down) * scale }
    }
}

/** Último deslocamento de arraste da carta, para ela partir de onde foi solta. */
private class DragMemory(var last: Offset = Offset.Zero)

@Composable
private fun AnimatedCard(
    card: Card,
    width: Dp,
    target: DpOffset,
    enterFrom: DpOffset,
    enterDelayMillis: Int,
    z: Int,
    highlighted: Boolean,
    hinted: Boolean,
    hintDelayMillis: Int,
    dimmed: Boolean,
    dragOffset: Offset?,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val localDensity = LocalDensity.current
    val x = remember { Animatable(enterFrom.x, Dp.VectorConverter) }
    val y = remember { Animatable(enterFrom.y, Dp.VectorConverter) }
    // Toda carta nasce de costas (saindo do monte) e vira se precisar: 180° = verso, 0° = frente.
    val flip = remember { Animatable(180f) }
    var entered by remember { mutableStateOf(false) }
    val memory = remember { DragMemory() }
    if (dragOffset != null) memory.last = dragOffset
    val dragging = dragOffset != null

    LaunchedEffect(target, dragging) {
        if (dragging) return@LaunchedEffect
        if (memory.last != Offset.Zero) {
            // Acabou de ser solta: continua do ponto onde o dedo largou, seja para o destino ou de volta.
            with(localDensity) {
                x.snapTo(x.value + memory.last.x.toDp())
                y.snapTo(y.value + memory.last.y.toDp())
            }
            memory.last = Offset.Zero
        }
        if (!entered) delay(enterDelayMillis.toLong())
        entered = true
        launch { x.animateTo(target.x, MoveSpec) }
        launch { y.animateTo(target.y, MoveSpec) }
    }
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(card.faceUp, entered) {
        if (!entered) return@LaunchedEffect
        // Saindo do monte, a carta voa de costas e só vira depois de pousar.
        if (!landed) delay(MOVE_MILLIS.toLong())
        landed = true
        flip.animateTo(if (card.faceUp) 0f else 180f, FlipSpec)
    }

    // Dica: as cartas dão três pulinhos e crescem um pouco a cada um.
    val hop = remember { Animatable(0f) }
    LaunchedEffect(hinted) {
        if (!hinted) {
            hop.snapTo(0f)
            return@LaunchedEffect
        }
        delay(hintDelayMillis.toLong())
        repeat(3) {
            hop.animateTo(1f, HopSpec)
            hop.animateTo(0f, HopSpec)
        }
    }

    // Ao pegar, a carta "levanta": cresce um pouco e ganha sombra.
    val lift by animateFloatAsState(if (dragging) 1.08f else 1f, LiftSpec, label = "lift")
    val elevation by animateDpAsState(if (dragging) 10.dp else 0.5.dp, tween(120, easing = FastOutSlowInEasing), label = "elevation")

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val moving = x.isRunning || y.isRunning || !entered
    PlayingCard(
        // Passou da metade do giro: mostra a frente.
        card = if ((flip.value < 90f) == card.faceUp) card else card.copy(faceUp = flip.value < 90f),
        width = width,
        highlighted = highlighted,
        glowing = hinted,
        dimmed = dimmed && !dragging,
        elevation = elevation,
        onClick = onClick,
        modifier = Modifier
            .offset {
                val drag = dragOffset ?: Offset.Zero
                IntOffset((x.value.toPx() + drag.x).roundToInt(), (y.value.toPx() + drag.y).roundToInt())
            }
            // Carta arrastada ou em movimento passa por cima das outras colunas.
            .zIndex(
                when {
                    dragging -> 2000f + z
                    moving -> 1000f + z
                    else -> z.toFloat()
                },
            )
            .graphicsLayer {
                scaleX = lift * (1f + 0.06f * hop.value)
                scaleY = lift * (1f + 0.06f * hop.value)
                translationY = -hop.value * (width * 0.2f).toPx()
                applyFlip(flip.value)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentOnDrag(amount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                )
            },
    )
}

/**
 * Giro plano e simétrico: a carta fecha pelo meio (escala horizontal), troca de lado e abre,
 * crescendo um pouco na vertical. Sem perspectiva, nenhum lado fica maior que o outro.
 * [flip] vai de 0 (frente) a 180 (verso).
 */
private fun GraphicsLayerScope.applyFlip(flip: Float) {
    val radians = flip / 180f * PI.toFloat()
    scaleX *= abs(cos(radians))
    scaleY *= 1f + 0.06f * sin(radians)
}

@Composable
private fun GhostCard(ghost: Ghost, width: Dp, onDone: () -> Unit) {
    val x = remember { Animatable(ghost.from.x, Dp.VectorConverter) }
    val y = remember { Animatable(ghost.from.y, Dp.VectorConverter) }
    val flip = remember { Animatable(if (ghost.card.faceUp) 0f else 180f) }
    val currentOnDone by rememberUpdatedState(onDone)
    // Cada trecho dura um tempo fixo, mesmo para a carta que já está no lugar: assim as 13
    // cartas de uma sequência passam juntas de um trecho para o outro.
    suspend fun leg(target: DpOffset, millis: Int) = coroutineScope {
        val spec = tween<Dp>(millis, easing = FastOutSlowInEasing)
        launch { x.animateTo(target.x, spec) }
        launch { y.animateTo(target.y, spec) }
        launch { delay(millis.toLong()) }
    }
    LaunchedEffect(Unit) {
        if (ghost.toStock) {
            // Voltando ao monte, primeiro vira no lugar e só então voa.
            delay(ghost.delayMillis.toLong())
            flip.animateTo(180f, FlipSpec)
        } else {
            ghost.landing?.let { leg(it, MOVE_MILLIS) }
            delay(120)
            ghost.gather?.let { leg(it, 180) }
            delay(ghost.delayMillis.toLong())
        }
        leg(ghost.to, 220)
        currentOnDone()
    }
    PlayingCard(
        card = ghost.card.copy(faceUp = flip.value < 90f),
        width = width,
        modifier = Modifier
            .offset { IntOffset(x.value.roundToPx(), y.value.roundToPx()) }
            .zIndex(3000f + ghost.serial % 1000)
            .graphicsLayer {
                scaleX = 1f
                scaleY = 1f
                applyFlip(flip.value)
            },
    )
}

@Composable
private fun EmptySlot(width: Dp, position: DpOffset, highlighted: Boolean) {
    val colors = LocalTableColors.current
    val shape = cardShape(width)
    Box(
        Modifier
            .offset(position.x, position.y)
            .width(width)
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(shape)
            .background(colors.emptySlot.copy(alpha = 0.6f))
            .then(if (highlighted) Modifier.border(2.dp, colors.highlight, shape) else Modifier),
    )
}
