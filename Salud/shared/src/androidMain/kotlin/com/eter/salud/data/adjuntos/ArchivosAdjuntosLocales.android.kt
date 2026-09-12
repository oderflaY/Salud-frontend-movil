package com.eter.salud.data.adjuntos

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class ArchivosAdjuntosLocalesAndroid(
    private val contexto: Context,
) : ArchivosAdjuntosLocales {

    override fun rutaLocalDe(idConversacion: String, idAdjunto: String, nombreSugerido: String): String {
        val carpeta = File(contexto.filesDir, "adjuntos/$idConversacion")
        val extension = nombreSugerido.substringAfterLast('.', "")
        val nombreArchivo = if (extension.isNotEmpty()) "$idAdjunto.$extension" else idAdjunto
        return File(carpeta, nombreArchivo).absolutePath
    }

    override suspend fun existe(rutaLocal: String): Boolean = withContext(Dispatchers.IO) {
        File(rutaLocal).exists()
    }

    override suspend fun leerBytes(rutaLocal: String): ByteArray? = withContext(Dispatchers.IO) {
        val archivo = File(rutaLocal)
        if (archivo.exists()) runCatching { archivo.readBytes() }.getOrNull() else null
    }

    override suspend fun guardarBytes(rutaLocal: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val archivo = File(rutaLocal)
            archivo.parentFile?.mkdirs()
            archivo.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }
}

@Composable
actual fun rememberArchivosAdjuntosLocales(): ArchivosAdjuntosLocales {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) { ArchivosAdjuntosLocalesAndroid(contexto) }
}
