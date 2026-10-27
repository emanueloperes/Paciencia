package com.emanueloperes.paciencia.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emanueloperes.paciencia.engine.Card
import com.emanueloperes.paciencia.engine.Suit
import com.emanueloperes.paciencia.ui.theme.LocalTableColors
import com.emanueloperes.paciencia.ui.theme.PacienciaTheme

const val CARD_ASPECT_RATIO = 0.7f

/** Carta desenhada pelo app: só valor e naipe, sem figuras. O tamanho segue a largura. */
@Composable
fun PlayingCard(card: Card, width: Dp, modifier: Modifier = Modifier) {
    val colors = LocalTableColors.current
    val shape = RoundedCornerShape(width * 0.1f)
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT_RATIO)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(if (card.faceUp) colors.cardFace else colors.cardBack)
            .semantics { contentDescription = if (card.faceUp) card.spokenName() else "Carta virada para baixo" },
    ) {
        if (card.faceUp) CardFace(card, width) else CardBack()
    }
}

@Composable
private fun CardFace(card: Card, width: Dp) {
    val colors = LocalTableColors.current
    val ink = if (card.suit.isRed) colors.redSuit else colors.blackSuit
    val corner = with(LocalDensity.current) { (width * 0.3f).toSp() }
    val center = with(LocalDensity.current) { (width * 0.55f).toSp() }
    Text(
        text = "${card.rankLabel()}${card.suit.symbol}",
        style = TextStyle(color = ink, fontSize = corner, fontWeight = FontWeight.Medium, lineHeight = corner),
        modifier = Modifier.padding(start = width * 0.08f, top = width * 0.04f),
    )
    Box(Modifier.fillMaxSize().padding(top = width * 0.25f), contentAlignment = Alignment.Center) {
        Text(card.suit.symbol, style = TextStyle(color = ink, fontSize = center, lineHeight = center))
    }
}

/** Verso: linhas diagonais finas em menta sobre verde-escuro. */
@Composable
private fun CardBack() {
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
        PlayingCard(Card(0, 12, Suit.HEARTS, faceUp = true), width = 72.dp)
    }
}
