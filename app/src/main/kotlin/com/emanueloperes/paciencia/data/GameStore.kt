package com.emanueloperes.paciencia.data

import androidx.core.util.AtomicFile
import com.emanueloperes.paciencia.engine.SavedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class StoredGame(val game: SavedGame, val elapsedMillis: Long)

/** Guarda a partida em andamento num arquivo JSON, com escrita atômica para não corromper. */
class GameStore(file: File) {
    private val file = AtomicFile(file)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): StoredGame? = withContext(Dispatchers.IO) {
        try {
            json.decodeFromString(StoredGame.serializer(), file.readFully().decodeToString())
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null // Arquivo de uma versão incompatível: começa do zero.
        }
    }

    suspend fun save(game: StoredGame) = withContext(Dispatchers.IO) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(StoredGame.serializer(), game).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
