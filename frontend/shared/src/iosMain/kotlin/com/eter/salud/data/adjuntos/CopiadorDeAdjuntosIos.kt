package com.eter.salud.data.adjuntos

import com.eter.salud.data.local.idLocal
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.TipoAdjunto
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Escribe los datos ya obtenidos (de la galeria, del selector de archivos o del
 * escaner) en `Documents/adjuntos/<idConversacion>/` del contenedor de la app.
 *
 * Analogo a [CopiadorDeAdjuntosAndroid], pero mas simple: en iOS el propio
 * sistema entrega los bytes de la foto o el `NSData` del archivo ya leido, asi
 * que aqui no hace falta abrir ningun flujo, solo elegir donde guardarlo.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CopiadorDeAdjuntosIos(
    private val idConversacion: String,
) {

    /** `null` si no se pudo crear la carpeta o escribir el archivo. */
    fun guardar(datos: NSData, nombreSugerido: String, mime: String, tipo: TipoAdjunto): Adjunto? {
        val gestorDeArchivos = NSFileManager.defaultManager
        val documentos = gestorDeArchivos.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        ) ?: return null

        val carpeta = documentos.URLByAppendingPathComponent("$CARPETA_ADJUNTOS/$idConversacion") ?: return null
        gestorDeArchivos.createDirectoryAtURL(
            carpeta,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val idAdjunto = idLocal(PREFIJO_ADJUNTO)
        val extension = nombreSugerido.substringAfterLast('.', "")
        val nombreArchivo = if (extension.isNotEmpty()) "$idAdjunto.$extension" else idAdjunto
        val destino = carpeta.URLByAppendingPathComponent(nombreArchivo) ?: return null
        val ruta = destino.path ?: return null

        val escrito = gestorDeArchivos.createFileAtPath(ruta, contents = datos, attributes = null)
        if (!escrito) return null

        return Adjunto(
            idAdjunto = idAdjunto,
            tipo = tipo,
            nombre = nombreSugerido,
            rutaLocal = ruta,
            tipoMime = mime,
        )
    }

    private companion object {
        const val PREFIJO_ADJUNTO = "adj_"
        const val CARPETA_ADJUNTOS = "adjuntos"
    }
}
