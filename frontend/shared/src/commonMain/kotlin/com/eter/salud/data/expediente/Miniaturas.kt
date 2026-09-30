package com.eter.salud.data.expediente

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodifica una imagen del telefono REDUCIDA a [ladoMaximo] pixeles por lado.
 *
 * Reducir al decodificar y no despues: una radiografia escaneada de 3000 x 4000
 * ocupa unos 48 MB ya decodificada, y una galeria de diez la llevaria a mas de
 * lo que un telefono le deja a una app. Para una miniatura basta una fraccion.
 *
 * `null` si el archivo no existe o no es una imagen que el sistema sepa leer.
 */
expect suspend fun decodificarMiniatura(ruta: String, ladoMaximo: Int): ImageBitmap?
