package com.eter.salud.data.expediente

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun decodificarMiniatura(ruta: String, ladoMaximo: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        // Primero solo las medidas, sin reservar memoria para los pixeles.
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return@runCatching null

        // Potencia de dos: es lo que el decodificador reduce sin costo extra.
        var factor = 1
        while (medidas.outWidth / (factor * 2) >= ladoMaximo && medidas.outHeight / (factor * 2) >= ladoMaximo) {
            factor *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = factor }
        BitmapFactory.decodeFile(ruta, opciones)?.asImageBitmap()
    }.getOrNull()
}
