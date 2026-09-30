package com.eter.salud.data.expediente

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberDirectorioDeExpedientes(): String {
    val contexto = LocalContext.current.applicationContext
    // `filesDir` y no la cache: el sistema vacia la cache cuando le falta
    // espacio, y un expediente no puede perder sus radiografias por eso.
    return remember(contexto) { File(contexto.filesDir, "expedientes").absolutePath }
}
