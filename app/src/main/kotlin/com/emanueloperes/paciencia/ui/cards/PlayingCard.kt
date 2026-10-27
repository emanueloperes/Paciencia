package com.emanueloperes.paciencia.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.emanueloperes.paciencia.engine.Card
import com.emanueloperes.paciencia.engine.Suit
import com.emanueloperes.paciencia.ui.theme.FaceMenta
import com.emanueloperes.paciencia.ui.theme.FaceRosa
import com.emanueloperes.paciencia.ui.theme.LocalTableColors
import com.emanueloperes.paciencia.ui.theme.Menta
import com.emanueloperes.paciencia.ui.theme.PacienciaTheme
import com.emanueloperes.paciencia.ui.theme.VerdeEscuro
import com.emanueloperes.paciencia.ui.theme.VermelhoSuave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val CARD_ASPECT_RATIO = 0.7f

enum class DeckStyle { MINIMAL, EXPRESSIVE }

val LocalDeckStyle = staticCompositionLocalOf { DeckStyle.EXPRESSIVE }

@Composable
fun cardShape(width: Dp): Shape =
    RoundedCornerShape(width * if (LocalDeckStyle.current == DeckStyle.EXPRESSIVE) 0.16f else 0.1f)

/** Carta desenhada pelo app no estilo de [LocalDeckStyle]. O tamanho segue a largura. */
@Composable
fun PlayingCard(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTableColors.current
    val style = LocalDeckStyle.current
    val shape = cardShape(width)
    val background = when {
        style == DeckStyle.MINIMAL -> if (card.faceUp) colors.cardFace else colors.cardBack
        !card.faceUp -> Menta
        card.suit.isRed -> FaceRosa
        else -> FaceMenta
    }
    val outline = when {
        highlighted -> Modifier.border(2.dp, colors.highlight, shape)
        // Contorno separa os versos empilhados na coluna.
        !card.faceUp && style == DeckStyle.EXPRESSIVE -> Modifier.border(1.5.dp, VerdeEscuro.copy(alpha = 0.6f), shape)
        !card.faceUp -> Modifier.border(0.75.dp, colors.cardBackPattern.copy(alpha = 0.5f), shape)
        else -> Modifier
    }
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT_RATIO)
            .upwardShadow(width)
            .shadow(elevation, shape)
            .clip(shape)
            .background(background)
            .then(outline)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = if (card.faceUp) card.spokenName() else "Carta virada para baixo" },
    ) {
        when {
            style == DeckStyle.MINIMAL && card.faceUp -> MinimalFace(card, width)
            style == DeckStyle.MINIMAL -> MinimalBack()
            card.faceUp -> ExpressiveFace(card, width)
            else -> ExpressiveBack(width)
        }
    }
}

/**
 * Sombra que sobe a partir da borda de cima, sobre a carta de baixo na coluna.
 * A sombra comum do Material quase não aparece nesse sentido.
 */
private fun Modifier.upwardShadow(width: Dp) = drawBehind {
    val height = (width * 0.07f).toPx()
    val inset = (width * 0.08f).toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x73001410)),
            startY = -height,
            endY = 0f,
        ),
        topLeft = Offset(inset, -height),
        size = Size(size.width - inset * 2, height),
    )
}

@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

// --- Baralho minimalista ---

@Composable
private fun MinimalFace(card: Card, width: Dp) {
    val colors = LocalTableColors.current
    val ink = if (card.suit.isRed) colors.redSuit else colors.blackSuit
    val corner = (width * 0.34f).asSp()
    val center = (width * 0.55f).asSp()
    Text(
        text = "${card.rankLabel()}${card.suit.symbol}",
        style = TextStyle(color = ink, fontSize = corner, fontWeight = FontWeight.Bold, lineHeight = corner),
        modifier = Modifier.padding(start = width * 0.07f, top = width * 0.03f),
    )
    Box(Modifier.fillMaxSize().padding(top = width * 0.25f), contentAlignment = Alignment.Center) {
        Text(card.suit.symbol, style = TextStyle(color = ink, fontSize = center, lineHeight = center))
    }
}

/** Verso: linhas diagonais finas em menta. */
@Composable
private fun MinimalBack() {
    val pattern = LocalTableColors.current.cardBackPattern.copy(alpha = 0.35f)
    Canvas(Modifier.fillMaxSize()) {
        val step = size.width / 6
        val stroke = size.width / 60
        var x = -size.height
        while (x < size.width) {
            drawLine(pattern, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
            x += step
        }
    }
}

// --- Baralho expressivo ---

/** Formas do Material 3 Expressive aproximadas por raio ondulado: r(θ) = R·(1 + a·cos(nθ)). */
private enum class BadgeShape(val lobes: Int, val amplitude: Float) {
    COOKIE(9, 0.075f),
    CLOVER(4, 0.16f),
    SUNNY(12, 0.05f),
}

@Composable
private fun ExpressiveFace(card: Card, width: Dp) {
    val ink = if (card.suit.isRed) VermelhoSuave else VerdeEscuro
    val face = if (card.suit.isRed) FaceRosa else FaceMenta
    val value = (width * 0.44f).asSp()
    val smallSuit = (width * 0.24f).asSp()
    Box(Modifier.fillMaxSize()) {
        // Arte primeiro, para o índice ficar por cima quando a carta for estreita.
        when (card.rank) {
            Card.ACE -> Badge(BadgeShape.SUNNY, card.suit.symbol, ink, face, width, Modifier.align(Alignment.BottomEnd))
            11, 13 -> Badge(BadgeShape.COOKIE, card.rankLabel(), ink, face, width, Modifier.align(Alignment.BottomEnd))
            12 -> Badge(BadgeShape.CLOVER, card.rankLabel(), ink, face, width, Modifier.align(Alignment.BottomEnd))
            else -> {
                val big = (width * 1.15f).asSp()
                Text(
                    card.suit.symbol,
                    style = TextStyle(color = ink.copy(alpha = 0.92f), fontSize = big, lineHeight = big),
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = width * 0.24f, y = width * 0.3f),
                )
            }
        }
        Text(
            card.rankLabel(),
            style = TextStyle(
                color = ink,
                fontSize = value,
                lineHeight = value,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.04).em,
            ),
            modifier = Modifier.padding(start = width * 0.08f, top = width * 0.03f),
        )
        Text(
            card.suit.symbol,
            style = TextStyle(color = ink, fontSize = smallSuit, lineHeight = smallSuit),
            modifier = Modifier.padding(start = width * 0.1f, top = width * 0.5f),
        )
    }
}

/** Forma colorida que vaza pelo canto inferior direito, com a letra ou o naipe dentro. */
@Composable
private fun Badge(shape: BadgeShape, label: String, color: Color, labelColor: Color, width: Dp, modifier: Modifier) {
    val size = width * 0.82f
    Box(modifier.offset(x = width * 0.14f, y = width * 0.12f).size(size)) {
        ShapeCanvas(shape, color, Modifier.fillMaxSize())
        val text = (width * 0.4f).asSp()
        Row(Modifier.fillMaxSize().padding(end = size * 0.12f, bottom = size * 0.1f), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                label,
                style = TextStyle(color = labelColor, fontSize = text, lineHeight = text, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShapeCanvas(shape: BadgeShape, color: Color, modifier: Modifier) {
    // Pontos da forma num quadrado unitário; escalados no desenho.
    val points = remember(shape) {
        (0 until 120).map { i ->
            val t = i / 120.0 * 2 * PI
            val r = 0.44 * (1 + shape.amplitude * cos(shape.lobes * t))
            Offset((0.5 + r * cos(t)).toFloat(), (0.5 + r * sin(t)).toFloat())
        }
    }
    Canvas(modifier) {
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = p.x * size.width
            val y = p.y * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/** Verso: menta com moldura interna e um selo de espadas no centro. */
@Composable
private fun ExpressiveBack(width: Dp) {
    val innerShape = RoundedCornerShape(width * 0.1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(width * 0.07f)
                .border(1.5.dp, VerdeEscuro.copy(alpha = 0.35f), innerShape),
        )
        Box(Modifier.size(width * 0.7f), contentAlignment = Alignment.Center) {
            ShapeCanvas(BadgeShape.COOKIE, VerdeEscuro, Modifier.fillMaxSize())
            val spade = (width * 0.3f).asSp()
            Text(Suit.SPADES.symbol, style = TextStyle(color = Menta, fontSize = spade, lineHeight = spade))
        }
    }
}

// O seletor de variação U+FE0E força o desenho em texto, sem virar emoji.
val Suit.symbol: String
    get() = when (this) {
        Suit.SPADES -> "♠︎"
        Suit.HEARTS -> "♥︎"
        Suit.DIAMONDS -> "♦︎"
        Suit.CLUBS -> "♣︎"
    }

fun Card.rankLabel(): String = when (rank) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> rank.toString()
}

fun Card.spokenName(): String {
    val name = when (rank) {
        1 -> "Ás"
        11 -> "Valete"
        12 -> "Dama"
        13 -> "Rei"
        else -> rank.toString()
    }
    val suitName = when (suit) {
        Suit.SPADES -> "espadas"
        Suit.HEARTS -> "copas"
        Suit.DIAMONDS -> "ouros"
        Suit.CLUBS -> "paus"
    }
    return "$name de $suitName"
}

@Preview
@Composable
private fun PlayingCardPreview() {
    PacienciaTheme {
        Row {
            PlayingCard(Card(0, 12, Suit.HEARTS, faceUp = true), width = 72.dp)
            PlayingCard(Card(1, 7, Suit.SPADES, faceUp = true), width = 72.dp)
            PlayingCard(Card(2, 7, Suit.SPADES), width = 72.dp)
        }
    }
}
