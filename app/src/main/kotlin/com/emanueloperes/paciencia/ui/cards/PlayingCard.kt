package com.emanueloperes.paciencia.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.emanueloperes.paciencia.engine.Card
import com.emanueloperes.paciencia.engine.Suit
import com.emanueloperes.paciencia.ui.theme.BordaCarta
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
    /** Carta aberta que não pode ser movida (fora da sequência do topo): fica mais escura. */
    dimmed: Boolean = false,
    /** Carta indicada pela dica: borda mais grossa e brilho menta. */
    glowing: Boolean = false,
    elevation: Dp = 0.5.dp,
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
        glowing -> Modifier.border(3.dp, colors.highlight, shape)
        highlighted -> Modifier.border(2.dp, colors.highlight, shape)
        // Borda fina mais escura que a mesa: separa as cartas empilhadas sem depender da sombra.
        style == DeckStyle.EXPRESSIVE -> Modifier.border(1.dp, BordaCarta, shape)
        !card.faceUp -> Modifier.border(0.75.dp, colors.cardBackPattern.copy(alpha = 0.5f), shape)
        else -> Modifier
    }
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT_RATIO)
            .upwardShadow(width)
            .then(
                if (glowing) Modifier.shadow(10.dp, shape, ambientColor = colors.highlight, spotColor = colors.highlight)
                else Modifier.shadow(elevation, shape),
            )
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (dimmed) drawRect(DimOverlay)
            }
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
 * Sombra curta e suave que sobe a partir da borda de cima, sobre a carta de baixo na coluna.
 * A sombra comum do Material quase não aparece nesse sentido.
 */
private fun Modifier.upwardShadow(width: Dp) = drawBehind {
    val height = (width * 0.035f).toPx()
    val inset = (width * 0.08f).toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x33001410)),
            startY = -height,
            endY = 0f,
        ),
        topLeft = Offset(inset, -height),
        size = Size(size.width - inset * 2, height),
    )
}

private val DimOverlay = Color(0x6600210E)

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

/** Desenhos dos naipes numa caixa 100×100, iguais aos da página de proposta do baralho. */
private val SuitPathData = mapOf(
    Suit.HEARTS to "M50 90C20 68 5 52 5 33 5 18 16 8 30 8c9 0 16 5 20 13 4-8 11-13 20-13 14 0 25 10 25 25 0 19-15 35-45 57z",
    Suit.DIAMONDS to "M50 3c10 16 24 32 40 47-16 15-30 31-40 47C40 81 26 65 10 50 26 35 40 19 50 3z",
    Suit.SPADES to "M50 5C40 22 9 37 9 60c0 13 10 22 22 22 8 0 14-4 17-9-1 9-4 16-11 22h26c-7-6-10-13-11-22 3 5 9 9 17 9 12 0 22-9 22-22C91 37 60 22 50 5z",
    Suit.CLUBS to "M50 8a19 19 0 0 1 15 31 19 19 0 1 1 3 33c-7 0-13-3-16-8 1 10 4 17 11 22H37c7-5 10-12 11-22-3 5-9 8-16 8a19 19 0 1 1 3-33A19 19 0 0 1 50 8z",
)

private val SuitPaths: Map<Suit, Path> = SuitPathData.mapValues { PathParser().parsePathString(it.value).toPath() }

/**
 * Desenha o naipe. Com [solidBase], espadas e paus ganham a base preenchida: no naipe
 * gigante a borda da carta corta bem nas reentrâncias do cabo, que viravam fiapos brancos.
 */
@Composable
private fun SuitShape(suit: Suit, color: Color, modifier: Modifier, solidBase: Boolean = false) {
    val path = SuitPaths.getValue(suit)
    val fillBase = solidBase && (suit == Suit.SPADES || suit == Suit.CLUBS)
    Canvas(modifier) {
        scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
            drawPath(path, color)
            if (fillBase) drawRect(color, topLeft = Offset(22f, 62f), size = Size(56f, 38f))
        }
    }
}

@Composable
private fun ExpressiveFace(card: Card, width: Dp) {
    val ink = if (card.suit.isRed) VermelhoSuave else VerdeEscuro
    val value = (width * 0.56f).asSp()
    Box(Modifier.fillMaxSize()) {
        // Naipe gigante com uma largura de carta, vazando pelo canto inferior direito.
        SuitShape(
            card.suit,
            ink,
            Modifier.offset(x = width * 0.3f, y = width * 0.7f).size(width),
            solidBase = true,
        )
        Text(
            card.rankLabel(),
            style = TextStyle(
                color = ink,
                fontSize = value,
                lineHeight = value,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.06).em,
            ),
            // Valor grande: é o que aparece da carta quando ela está sobreposta na coluna.
            modifier = Modifier.padding(start = width * 0.06f),
        )
        SuitShape(card.suit, ink, Modifier.offset(x = width * 0.09f, y = width * 0.62f).size(width * 0.22f))
    }
}

/** Forma "biscoito" do Material 3 Expressive, aproximada por raio ondulado: r(θ) = R·(1 + a·cos(9θ)). */
@Composable
private fun CookieShape(color: Color, modifier: Modifier) {
    val points = remember {
        (0 until 120).map { i ->
            val t = i / 120.0 * 2 * PI
            val r = 0.44 * (1 + 0.075 * cos(9 * t))
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
            CookieShape(VerdeEscuro, Modifier.fillMaxSize())
            SuitShape(Suit.SPADES, Menta, Modifier.size(width * 0.3f))
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
