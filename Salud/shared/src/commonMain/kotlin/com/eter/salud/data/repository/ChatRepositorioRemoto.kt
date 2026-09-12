package com.eter.salud.data.repository

import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.red.CanalTiempoReal
import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.data.red.JsonRed
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * `ChatRepositorio` contra el backend en Go (`docs/CONTRATOS_BACKEND.md`,
 * seccion 8, mas el addendum de adjuntos al final del documento -- esta
 * seccion se escribio antes de que el chat pudiera adjuntar fotos/archivos).
 *
 * ## Por que [MensajeChatRed]/[AdjuntoRed] y no reusar los modelos de dominio
 *
 * [Adjunto.rutaLocal] apunta a un archivo del DISPOSITIVO; lo que viaja por la
 * red es una URL de descarga en el backend. Son dos cosas distintas con el
 * mismo proposito, y forzarlas al mismo campo confundiria "donde esta en este
 * telefono" con "donde esta en el servidor". Este repositorio es el unico
 * punto que conoce las dos formas y convierte entre ellas.
 */
class ChatRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
    private val conexion: CanalTiempoReal,
    private val archivos: ArchivosAdjuntosLocales,
) : ChatRepositorio {

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> {
        val respuesta = cliente.get("$baseUrl/conversaciones/$idConversacion/mensajes")
        return if (respuesta.status.isSuccess()) {
            val mensajes: List<MensajeChatRed> = respuesta.body()
            Result.success(mensajes.map { it.aDominio(idConversacion, archivos, cliente) })
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto?,
    ): Result<MensajeChat> {
        val adjuntoRed = if (adjunto != null) {
            subir(idConversacion, adjunto) ?: return Result.failure(FalloDeRedGenerico(0))
        } else {
            null
        }
        val respuesta = cliente.post("$baseUrl/conversaciones/$idConversacion/mensajes") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoEnviarMensaje(texto, instante, autor, adjuntoRed))
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))

        val red: MensajeChatRed = respuesta.body()
        // Los bytes del adjunto ya estan en disco (los acabamos de subir desde
        // ahi): usar la copia local que ya tenemos evita descargar de vuelta lo
        // que este mismo dispositivo acaba de mandar.
        val mensaje = if (adjunto != null) red.aDominioConCopiaLocal(adjunto) else red.aDominioSinAdjunto()
        return Result.success(mensaje)
    }

    override fun mensajesSinLeer(idConversacion: String): Flow<Int> =
        conexion.canal("chat:$idConversacion") { payload ->
            JsonRed.decodeFromJsonElement<SobrePendientes>(payload).noLeidos
        }

    override suspend fun marcarConversacionLeida(idConversacion: String) {
        // Mejor esfuerzo: no marcar como leida no debe tumbar la pantalla de
        // chat, y no hay nada mas que el paciente pueda hacer ante ese fallo.
        runCatching { cliente.post("$baseUrl/conversaciones/$idConversacion/leida") }
    }

    override suspend fun obtenerRespuestaAutomatica(idConversacion: String, instante: String): Result<MensajeChat> {
        val respuesta = cliente.post("$baseUrl/conversaciones/$idConversacion/acuse-recibo") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoInstante(instante))
        }
        return if (respuesta.status.isSuccess()) {
            val red: MensajeChatRed = respuesta.body()
            Result.success(red.aDominio(idConversacion, archivos, cliente))
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> {
        val respuesta = cliente.post("$baseUrl/mensajes/$idMensaje/resumen-ia")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    /** `null` si no se pudo leer el archivo local o si el backend rechazo la subida. */
    private suspend fun subir(idConversacion: String, adjunto: Adjunto): AdjuntoRed? {
        val bytes = archivos.leerBytes(adjunto.rutaLocal) ?: return null
        val respuesta = cliente.submitFormWithBinaryData(
            url = "$baseUrl/conversaciones/$idConversacion/adjuntos",
            formData = formData {
                append("tipo", adjunto.tipo.name)
                append(
                    "archivo",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, adjunto.tipoMime)
                        append(HttpHeaders.ContentDisposition, "filename=\"${adjunto.nombre}\"")
                    },
                )
            },
        )
        return if (respuesta.status.isSuccess()) respuesta.body() else null
    }
}

@Serializable
private data class SobrePendientes(val noLeidos: Int)

@Serializable
private data class AdjuntoRed(
    val idAdjunto: String,
    val tipo: TipoAdjunto,
    val nombre: String,
    val tipoMime: String,
    /** URL de descarga en el backend. Nunca una ruta de este dispositivo. */
    val url: String,
)

@Serializable
private data class MensajeChatRed(
    val idMensaje: String,
    val autor: AutorMensaje,
    val texto: String,
    val instante: String,
    val tipo: TipoMensaje = TipoMensaje.NORMAL,
    val adjunto: AdjuntoRed? = null,
)

@Serializable
private data class CuerpoEnviarMensaje(
    val texto: String,
    val instante: String,
    val autor: AutorMensaje,
    val adjunto: AdjuntoRed?,
)

@Serializable
private data class CuerpoInstante(val instante: String)

private fun MensajeChatRed.aDominioSinAdjunto() = MensajeChat(idMensaje, autor, texto, instante, tipo, adjunto = null)

private fun MensajeChatRed.aDominioConCopiaLocal(local: Adjunto) = MensajeChat(
    idMensaje = idMensaje,
    autor = autor,
    texto = texto,
    instante = instante,
    tipo = tipo,
    adjunto = local.copy(idAdjunto = adjunto?.idAdjunto ?: local.idAdjunto),
)

/**
 * Convierte el mensaje de red a dominio, descargando el adjunto una sola vez:
 * si ya existe una copia local con ese `idAdjunto` (por ejemplo, porque este
 * mismo dispositivo lo mando), no se vuelve a pedir por red.
 */
private suspend fun MensajeChatRed.aDominio(
    idConversacion: String,
    archivos: ArchivosAdjuntosLocales,
    cliente: HttpClient,
): MensajeChat {
    val adjuntoDominio = adjunto?.let { remoto ->
        val ruta = archivos.rutaLocalDe(idConversacion, remoto.idAdjunto, remoto.nombre)
        if (!archivos.existe(ruta)) {
            val respuesta = cliente.get(remoto.url)
            if (respuesta.status.isSuccess()) {
                val bytes: ByteArray = respuesta.body()
                archivos.guardarBytes(ruta, bytes)
            }
        }
        Adjunto(idAdjunto = remoto.idAdjunto, tipo = remoto.tipo, nombre = remoto.nombre, rutaLocal = ruta, tipoMime = remoto.tipoMime)
    }
    return MensajeChat(idMensaje, autor, texto, instante, tipo, adjuntoDominio)
}
