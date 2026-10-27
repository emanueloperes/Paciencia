package com.emanueloperes.paciencia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

// Escala padrão do Material 3 (Roboto no Android); só os números ganham largura fixa.
val Typography = Typography()

/** Algarismos de largura fixa, para o placar não "pular" enquanto muda. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium)
