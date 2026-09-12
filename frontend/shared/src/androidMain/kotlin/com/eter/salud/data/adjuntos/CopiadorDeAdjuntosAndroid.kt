package com.eter.salud.data.adjuntos

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.eter.salud.data.local.idLocal
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.TipoAdjunto
import java.io.File

/**
 * Copia el contenido de un `content://` (galeria, selector de archivos, o una
 * pagina que entrego el escaner de documentos) al almacenamiento privado de la
 * app.
 *
 * La copia no es un detalle de implementacion: el permiso de lectura que
 * concede un selector de Android sobre su `Uri` dura solo mientras la Activity
 * que lo pidio siga viva. Guardar ese `Uri` tal cual y volver a abrirlo manana,
 * al releer la conversacion, fallaria con `SecurityException`. Copiar los
 * bytes una sola vez, aqui, es lo que hace que el adjunto sobreviva.
 */
internal class CopiadorDeAdjuntosAndroid(
    private val contexto: Context,
    private val idConversacion: String,
) {

    /** `null` si no se pudo leer el origen (permiso revocado, archivo borrado). */
    fun copiar(uri: Uri, tipo: TipoAdjunto): Adjunto? {
        val resolvedor = contexto.contentResolver
        val nombreOriginal = nombreDe(uri) ?: NOMBRE_POR_DEFECTO
        val mime = resolvedor.getType(uri) ?: mimeDesdeNombre(nombreOriginal)
        val extension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val idAdjunto = idLocal(PREFIJO_ADJUNTO)
        val carpeta = File(contexto.filesDir, "$CARPETA_ADJUNTOS/$idConversacion").apply { mkdirs() }
        val destino = File(carpeta, if (extension != null) "$idAdjunto.$extension" else idAdjunto)

        val leido = try {
            resolvedor.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            true
        } catch (_: Exception) {
            false
        }
        if (!leido) return null

        return Adjunto(
            idAdjunto = idAdjunto,
            tipo = tipo,
            nombre = nombreOriginal,
            rutaLocal = destino.absolutePath,
            tipoMime = mime ?: MIME_POR_DEFECTO,
        )
    }

    /** El nombre para mostrar viene de `OpenableColumns`; el `Uri` en si es un identificador opaco. */
    private fun nombreDe(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        contexto.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) return cursor.getString(indice)
            }
        return uri.lastPathSegment
    }

    private fun mimeDesdeNombre(nombre: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(nombre.substringAfterLast('.', ""))

    private companion object {
        const val PREFIJO_ADJUNTO = "adj_"
        const val CARPETA_ADJUNTOS = "adjuntos"
        const val NOMBRE_POR_DEFECTO = "adjunto"
        const val MIME_POR_DEFECTO = "application/octet-stream"
    }
}
