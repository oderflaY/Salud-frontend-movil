package com.eter.salud.data.adjuntos

import androidx.compose.runtime.Composable

/**
 * Lee y escribe los bytes de un adjunto en el almacenamiento privado de la
 * app, en la misma carpeta que ya usan [CopiadorDeAdjuntosAndroid] /
 * [CopiadorDeAdjuntosIos] al elegir un archivo (`adjuntos/<idConversacion>/`).
 *
 * Existe aparte del copiador porque lo necesita
 * `com.eter.salud.data.repository.ChatRepositorioRemoto` para DOS cosas que el
 * copiador no hace:
 *  - Leer los bytes de un adjunto propio ya guardado, para subirlos al enviar
 *    el mensaje (`multipart/form-data`).
 *  - Guardar los bytes de un adjunto ajeno recien descargado, para que la
 *    Vista lo pueda mostrar como cualquier otro (siempre lee [Adjunto.rutaLocal],
 *    nunca una URL remota).
 */
interface ArchivosAdjuntosLocales {

    /** Ruta local donde vive (o vivira) el adjunto [idAdjunto] de [idConversacion]. */
    fun rutaLocalDe(idConversacion: String, idAdjunto: String, nombreSugerido: String): String

    /** Si ya existe una copia local en [rutaLocal]; evita descargar dos veces el mismo archivo. */
    suspend fun existe(rutaLocal: String): Boolean

    /** `null` si el archivo no existe o no se pudo leer. */
    suspend fun leerBytes(rutaLocal: String): ByteArray?

    /** `false` si no se pudo crear la carpeta o escribir el archivo. */
    suspend fun guardarBytes(rutaLocal: String, bytes: ByteArray): Boolean
}

/**
 * Crea el lector/escritor de adjuntos de la plataforma. Es `expect` por la
 * misma razon que [com.eter.salud.data.local.recordarContenedorSalud]: el
 * directorio privado de la app se obtiene distinto en cada sistema (el
 * `Context` en Android, `NSFileManager` en iOS).
 */
@Composable
expect fun rememberArchivosAdjuntosLocales(): ArchivosAdjuntosLocales
