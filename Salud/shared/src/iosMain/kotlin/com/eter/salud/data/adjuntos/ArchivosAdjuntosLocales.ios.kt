package com.eter.salud.data.adjuntos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private class ArchivosAdjuntosLocalesIos : ArchivosAdjuntosLocales {

    private val gestorDeArchivos get() = NSFileManager.defaultManager

    override fun rutaLocalDe(idConversacion: String, idAdjunto: String, nombreSugerido: String): String {
        val documentos = gestorDeArchivos.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val extension = nombreSugerido.substringAfterLast('.', "")
        val nombreArchivo = if (extension.isNotEmpty()) "$idAdjunto.$extension" else idAdjunto
        val carpeta = documentos?.URLByAppendingPathComponent("adjuntos/$idConversacion")
        return carpeta?.URLByAppendingPathComponent(nombreArchivo)?.path
            ?: "adjuntos/$idConversacion/$nombreArchivo"
    }

    override suspend fun existe(rutaLocal: String): Boolean = gestorDeArchivos.fileExistsAtPath(rutaLocal)

    override suspend fun leerBytes(rutaLocal: String): ByteArray? =
        NSData.dataWithContentsOfFile(rutaLocal)?.aByteArray()

    override suspend fun guardarBytes(rutaLocal: String, bytes: ByteArray): Boolean {
        val carpeta = rutaLocal.substringBeforeLast('/', "")
        if (carpeta.isNotEmpty()) {
            gestorDeArchivos.createDirectoryAtPath(carpeta, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return gestorDeArchivos.createFileAtPath(rutaLocal, contents = bytes.aNSData(), attributes = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.aByteArray(): ByteArray {
    val arreglo = ByteArray(length.toInt())
    if (arreglo.isNotEmpty()) {
        arreglo.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return arreglo
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.aNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong()) }
}

@Composable
actual fun rememberArchivosAdjuntosLocales(): ArchivosAdjuntosLocales = remember { ArchivosAdjuntosLocalesIos() }
