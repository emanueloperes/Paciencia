package com.emanueloperes.paciencia

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emanueloperes.paciencia.ui.home.HomeScreen
import com.emanueloperes.paciencia.ui.theme.PacienciaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PacienciaTheme {
                // A mesa de jogo entra na fase 2; por enquanto o botão só avisa.
                HomeScreen(onNewGame = {
                    Toast.makeText(this, "A mesa de jogo chega na próxima etapa", Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}
