package com.emanueloperes.paciencia.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Gap = 3.dp

// Molas no estilo do Material 3 Expressive: rápidas, com um leve assentamento no fim.
private val MoveSpring = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Dp.VisibilityThreshold)
private val FlipSpring = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium, visibilityThreshold = 0.5f)
private val LiftSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

/** Uma carta da mesa com a posição que ela deve ocupar. */
private data class PlacedCard(val card: Card, val column: Int, val index: Int, val position: DpOffset, val isTop: Boolean)

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

        // Conjuntos completos: um Rei de cada, sobrepostos; o mais novo entra com um salto.
        state.completed.forEachIndexed { i, suit ->
            key("completed-$i") {
                val scale = remember { Animatable(1.4f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, LiftSpring) }
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

        for (p in placed) {
            key(p.card.id) {
                val dragged = drag?.let { it.from == p.column && p.index >= it.index } == true
                val highlighted = (p.column in dragTargets && p.isTop) || hintTransfer != null && (
                    (hintTransfer.from == p.column && p.index >= hintTransfer.index) ||
                        (hintTransfer.to == p.column && p.isTop)
                    )
                AnimatedCard(
                    card = p.card,
                    width = cardWidth,
                    target = p.position,
                    enterFrom = stockPosition,
                    // Na mesa inicial as cartas chegam em leque; ao distribuir, uma coluna por vez.
                    enterDelayMillis = p.column * 30 + if (state.moves == 0) p.index * 60 else 0,
                    z = p.index,
                    highlighted = highlighted,
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
    val up = cardHeight * 0.32f
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
        launch { x.animateTo(target.x, MoveSpring) }
        launch { y.animateTo(target.y, MoveSpring) }
    }
    LaunchedEffect(card.faceUp, entered) {
        if (entered) flip.animateTo(if (card.faceUp) 0f else 180f, FlipSpring)
    }

    // Ao pegar, a carta "levanta": cresce um pouco e ganha sombra.
    val lift by animateFloatAsState(if (dragging) 1.08f else 1f, LiftSpring, label = "lift")
    val elevation by animateDpAsState(if (dragging) 10.dp else 1.dp, label = "elevation")

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
                scaleX = lift
                scaleY = lift
                // Entre 90° e 180° o verso aparece espelhado; o padrão é simétrico, então não importa.
                rotationY = flip.value
                cameraDistance = 12f * density
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
