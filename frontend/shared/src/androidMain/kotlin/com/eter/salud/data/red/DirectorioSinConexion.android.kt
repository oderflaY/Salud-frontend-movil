package com.eter.salud.data.red

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberDirectorioSinConexion(): String {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) { File(contexto.filesDir, "sin_conexion").absolutePath }
}
