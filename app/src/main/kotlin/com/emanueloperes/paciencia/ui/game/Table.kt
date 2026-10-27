package com.emanueloperes.paciencia.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Gap = 4.dp
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val MOVE_MILLIS = 300

/**
 * A mesa: conjuntos completos e monte em cima, as 10 colunas embaixo.
 * Todas as cartas ficam numa mesma camada, posicionadas pelo id, para que
 * mudar de coluna seja só uma animação de posição.
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
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp)) {
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

        fun dropColumn(d: DragState): Int {
            val draggedX = columnX(d.from) + with(density) { d.offset.x.toDp() }
            return (0 until columns).minBy { abs((columnX(it) - draggedX).value) }
        }

        fun endDrag() {
            val d = drag ?: return
            drag = null
            val to = dropColumn(d)
            if (to != d.from && Rules.isLegal(state, Move.Transfer(d.from, d.index, to))) onCardDrop(d.from, d.index, to)
        }

        // Conjuntos completos: um Rei de cada, sobrepostos.
        state.completed.forEachIndexed { i, suit ->
            PlayingCard(
                Card(id = -1 - i, rank = Card.KING, suit = suit, faceUp = true),
                cardWidth,
                Modifier.offset(x = columnX(0) + cardWidth * 0.3f * i),
            )
        }

        // Monte: um verso por distribuição restante, abrindo para a esquerda.
        if (state.dealsLeft == 0) {
            EmptySlot(cardWidth, stockPosition, highlighted = false)
        }
        repeat(state.dealsLeft) { i ->
            val isTop = i == state.dealsLeft - 1
            PlayingCard(
                state.stock[0].copy(faceUp = false),
                cardWidth,
                Modifier.offset(x = stockPosition.x - cardWidth * 0.2f * (state.dealsLeft - 1 - i)),
                highlighted = isTop && hint == Move.Deal,
                onClick = if (isTop) onStockTap else null,
            )
        }

        for (column in 0 until columns) {
            val highlightSlot = state.tableau[column].isEmpty() && (hintTransfer?.to == column || column in dragTargets)
            EmptySlot(cardWidth, DpOffset(columnX(column), tableauTop), highlightSlot)
        }

        for (column in 0 until columns) {
            val cards = state.tableau[column]
            val offsets = columnOffsets(cards, cardHeight, maxHeight - tableauTop)
            cards.forEachIndexed { index, card ->
                key(card.id) {
                    val isTop = index == cards.lastIndex
                    val dragged = drag?.let { it.from == column && index >= it.index } == true
                    val highlighted = (column in dragTargets && isTop) || hintTransfer != null && (
                        (hintTransfer.from == column && index >= hintTransfer.index) ||
                            (hintTransfer.to == column && isTop)
                        )
                    AnimatedCard(
                        card = card,
                        width = cardWidth,
                        target = DpOffset(columnX(column), tableauTop + offsets[index]),
                        enterFrom = stockPosition,
                        // Na mesa inicial as cartas chegam em leque; ao distribuir, uma coluna por vez.
                        enterDelayMillis = column * 30 + if (state.moves == 0) index * 60 else 0,
                        z = index,
                        highlighted = highlighted,
                        dragOffset = if (dragged) drag?.offset else null,
                        onClick = { onCardTap(column, index) },
                        onDragStart = {
                            if (Rules.canPickUp(state, column, index)) drag = DragState(column, index, Offset.Zero)
                        },
                        onDrag = { delta -> drag = drag?.let { it.copy(offset = it.offset + delta) } },
                        onDragEnd = { endDrag() },
                        onDragCancel = { drag = null },
                    )
                }
            }
        }
    }
}

/**
 * Deslocamento vertical de cada carta da coluna. Cartas viradas para baixo ficam
 * mais juntas; se a coluna não couber, o espaçamento todo é comprimido.
 */
private fun columnOffsets(cards: List<Card>, cardHeight: Dp, available: Dp): List<Dp> {
    val down = cardHeight * 0.14f
    val up = cardHeight * 0.3f
    val natural = cards.dropLast(1).fold(0.dp) { acc, card -> acc + if (card.faceUp) up else down }
    val room = available - cardHeight
    val scale = if (natural > room && natural > 0.dp) (room / natural).coerceAtLeast(0.05f) else 1f
    var y = 0.dp
    return cards.map { card ->
        y.also { y += (if (card.faceUp) up else down) * scale }
    }
}

/** Cartas sendo arrastadas: a partir de [index] na coluna [from], deslocadas [offset] pixels. */
private data class DragState(val from: Int, val index: Int, val offset: Offset)

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
    dragOffset: Offset?,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val x = remember { Animatable(enterFrom.x, Dp.VectorConverter) }
    val y = remember { Animatable(enterFrom.y, Dp.VectorConverter) }
    var entered by remember { mutableStateOf(false) }
    val memory = remember { DragMemory() }
    if (dragOffset != null) memory.last = dragOffset
    val dragging = dragOffset != null

    LaunchedEffect(target, dragging) {
        if (dragging) return@LaunchedEffect
        if (memory.last != Offset.Zero) {
            // Acabou de ser solta: continua do ponto onde o dedo largou, seja para o destino ou de volta.
            with(density) {
                x.snapTo(x.value + memory.last.x.toDp())
                y.snapTo(y.value + memory.last.y.toDp())
            }
            memory.last = Offset.Zero
        }
        // Só a primeira animação (saindo do monte) espera, para as cartas chegarem uma a uma.
        if (!entered) delay(enterDelayMillis.toLong())
        entered = true
        val spec = tween<Dp>(MOVE_MILLIS, easing = EmphasizedDecelerate)
        launch { x.animateTo(target.x, spec) }
        launch { y.animateTo(target.y, spec) }
    }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val moving = x.isRunning || y.isRunning || !entered
    PlayingCard(
        card = card,
        width = width,
        highlighted = highlighted,
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
