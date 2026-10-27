package com.emanueloperes.paciencia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores próprias do jogo, que não têm papel equivalente no Material 3. */
@Immutable
data class TableColors(
    val table: Color,
    val bar: Color,
    val emptySlot: Color,
    val cardFace: Color,
    val cardBack: Color,
    val cardBackPattern: Color,
    val redSuit: Color,
    val blackSuit: Color,
    val highlight: Color,
)

private val LightTable = TableColors(
    table = VerdeBase,
    bar = VerdeEscuro,
    emptySlot = VerdeFloresta,
    cardFace = BrancoEsverdeado,
    cardBack = VerdeEscuro,
    cardBackPattern = Menta,
    redSuit = VermelhoSuave,
    blackSuit = VerdeEscuro,
    highlight = Menta,
)

private val DarkTable = LightTable.copy(
    table = VerdeEscuro,
    bar = VerdeNoturno,
    // No escuro o verso precisa se destacar da mesa verde-escura.
    cardBack = VerdeBase,
    emptySlot = VerdeFloresta,
)

val LocalTableColors = staticCompositionLocalOf { LightTable }

/**
 * Os componentes Material ficam sempre sobre verde-escuro, nos dois temas:
 * o claro e o escuro mudam só a mesa e as barras (ver [TableColors]).
 */
private fun colorScheme(table: TableColors) = darkColorScheme(
    primary = Menta,
    onPrimary = VerdeEscuro,
    primaryContainer = VerdeFloresta,
    onPrimaryContainer = Menta,
    secondary = VerdeAcinzentado,
    onSecondary = VerdeNoturno,
    secondaryContainer = VerdeFloresta,
    onSecondaryContainer = BrancoEsverdeado,
    background = table.table,
    onBackground = BrancoEsverdeado,
    surface = table.bar,
    onSurface = BrancoEsverdeado,
    surfaceVariant = CinzaEsverdeado,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = table.bar,
    surfaceContainerLow = table.bar,
    surfaceContainer = table.bar,
    surfaceContainerHigh = CinzaEsverdeado,
    surfaceContainerHighest = CinzaEsverdeado,
    outline = VerdeAcinzentado,
    outlineVariant = VerdeFloresta,
    error = VermelhoSuave,
)

@Composable
fun PacienciaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val table = if (darkTheme) DarkTable else LightTable
    CompositionLocalProvider(LocalTableColors provides table) {
        MaterialTheme(colorScheme = colorScheme(table), typography = Typography, content = content)
    }
}
