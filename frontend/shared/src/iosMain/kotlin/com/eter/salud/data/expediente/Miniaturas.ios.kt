package com.eter.salud.data.expediente

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * En iOS se decodifica completa: el decodificador comun no reduce al leer.
 * Suficiente para pocas imagenes; si la galeria crece, este es el lugar para
 * reducirla con ImageIO (`CGImageSourceCreateThumbnailAtIndex`).
 */
@OptIn(ExperimentalResourceApi::class)
actual suspend fun decodificarMiniatura(ruta: String, ladoMaximo: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val archivo = Path(ruta)
        if (!SystemFileSystem.exists(archivo)) return@runCatching null
        SystemFileSystem.source(archivo).buffered().use { it.readByteArray() }.decodeToImageBitmap()
    }.getOrNull()
}
