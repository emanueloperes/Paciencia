package com.emanueloperes.paciencia

import android.app.Application
import com.emanueloperes.paciencia.data.GameStore
import com.emanueloperes.paciencia.data.Settings
import java.io.File

class PacienciaApp : Application() {
    val gameStore: GameStore by lazy { GameStore(File(filesDir, "partida.json")) }
    val settings: Settings by lazy { Settings(getSharedPreferences("configuracoes", MODE_PRIVATE)) }
}
