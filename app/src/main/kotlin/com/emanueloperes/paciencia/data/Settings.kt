package com.emanueloperes.paciencia.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Preferências do jogador, guardadas no aparelho. */
class Settings(private val prefs: SharedPreferences) {
    /** Sortear só partidas que o solver já resolveu (lista em SolvableSeeds). */
    var solvableOnly: Boolean
        get() = prefs.getBoolean(KEY_SOLVABLE_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_SOLVABLE_ONLY, value) }

    private companion object {
        const val KEY_SOLVABLE_ONLY = "so_partidas_com_solucao"
    }
}
