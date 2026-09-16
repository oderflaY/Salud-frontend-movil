package com.eter.salud.data.repository

import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.red.CanalTiempoReal
import com.eter.salud.data.red.FalloDeRedGenerico
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `ChatRepositorio` contra el backend real (`docs/mapeo-endpoints.md`,
 * seccion 8): los mensajes son funciones RPC de PostgREST, con parametros en
 * snake_case (`texto_mensaje`, `autor_remitente`...: Postgres no deja que un
 * parametro se llame igual que una columna de la respuesta). Los adjuntos van
 * aparte, a Axum (`/chat/{id}/adjuntos`), porque hay que subir un binario.
 *
 * Este archivo seguia apuntando a las rutas REST del contrato original
 * (`/conversaciones/{id}/mensajes`), que el backend nunca expuso: el chat
 * entero respondia 404 contra el servidor de verdad.
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
    private val reconexiones: Flow<Unit> = emptyFlow(),
    private val intervaloRevisionMs: Long = INTERVALO_REVISION_MS,
) : ChatRepositorio {

    /**
     * Tres fuentes, porque ninguna sola basta en un telefono real:
     *  - el aviso `mensaje_nuevo` del WebSocket (al instante);
     *  - la vuelta de la conexion (el socket no repite lo que paso sin red);
     *  - una revision periodica mientras el chat esta abierto, por si el
     *    socket se cayo sin que nadie lo notara (wifi que cambia, router que
     *    corta conexiones inactivas).
     */
    override fun cambiosEn(idConversacion: String): Flow<Unit> = merge(
        conexion.canal("chat:$idConversacion") { },
        conexion.reconexiones,
        reconexiones,
        flow {
            while (true) {
                delay(intervaloRevisionMs)
                emit(Unit)
            }
        },
    )

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> {
        val respuesta = cliente.post("$baseUrl/rpc/obtener_historial") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoConversacion(idConversacion))
        }
        return if (respuesta.status.isSuccess()) {
            val mensajes: List<MensajeChatRed> = respuesta.body()
            Result.success(mensajes.map { it.aDominio(idConversacion, archivos, cliente, baseUrl) })
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
        val respuesta = cliente.post("$baseUrl/rpc/enviar_mensaje") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoEnviarMensaje(idConversacion, texto, instante, autor, adjuntoRed?.idAdjunto))
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))

        // La RPC devuelve un array con el unico mensaje creado.
        val red = respuesta.body<List<MensajeChatRed>>().firstOrNull()
            ?: return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        // Los bytes del adjunto ya estan en disco (los acabamos de subir desde
        // ahi): usar la copia local que ya tenemos evita descargar de vuelta lo
        // que este mismo dispositivo acaba de mandar.
        val mensaje = if (adjunto != null) red.aDominioConCopiaLocal(adjunto) else red.aDominioSinAdjunto()
        return Result.success(mensaje)
    }

    /**
     * Conteo al abrir, y otra vez con cada aviso del canal (mensaje nuevo) o al
     * marcar la conversacion como leida desde este telefono. El conteo lo da
     * `rpc/mensajes_sin_leer` (0015): depende de quien mira, asi que no puede
     * viajar dentro de un evento que reciben las dos partes.
     */
    override fun mensajesSinLeer(idConversacion: String): Flow<Int> =
        // El conteo inicial va DENTRO del merge y no en un onStart: asi la
        // suscripcion al canal arranca a la vez que la primera consulta, y un
        // mensaje que llegue mientras esa consulta viaja no se pierde.
        merge(
            flowOf(Unit),
            conexion.canal("chat:$idConversacion") { },
            lecturasPropias.filter { it == idConversacion }.map { },
        ).mapNotNull { contarSinLeer(idConversacion) }

    /** Conversaciones que este telefono acaba de marcar como leidas. */
    private val lecturasPropias = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private suspend fun contarSinLeer(idConversacion: String): Int? {
        val respuesta = runCatching {
            cliente.post("$baseUrl/rpc/mensajes_sin_leer") {
                contentType(ContentType.Application.Json)
                setBody(CuerpoConversacion(idConversacion))
            }
        }.getOrNull() ?: return null
        if (!respuesta.status.isSuccess()) return null
        return runCatching { respuesta.body<JsonElement>().jsonPrimitive.intOrNull }.getOrNull()
    }

    override suspend fun marcarConversacionLeida(idConversacion: String) {
        // Mejor esfuerzo: no marcar como leida no debe tumbar la pantalla de
        // chat, y no hay nada mas que el paciente pueda hacer ante ese fallo.
        val marcada = runCatching {
            cliente.post("$baseUrl/rpc/marcar_conversacion_leida") {
                contentType(ContentType.Application.Json)
                setBody(CuerpoConversacion(idConversacion))
            }
        }.getOrNull()?.status?.isSuccess() == true
        // El globo baja a cero ya, sin esperar al siguiente mensaje.
        if (marcada) lecturasPropias.tryEmit(idConversacion)
    }

    override suspend fun obtenerRespuestaAutomatica(idConversacion: String, instante: String): Result<MensajeChat> {
        val respuesta = cliente.post("$baseUrl/rpc/obtener_respuesta_automatica") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoAcuse(idConversacion, instante))
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        val red = respuesta.body<List<MensajeChatRed>>().firstOrNull()
            ?: return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(red.aDominio(idConversacion, archivos, cliente, baseUrl))
    }

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> {
        val respuesta = cliente.post("$baseUrl/chat/mensajes/$idMensaje/resumen-ia")
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
            url = "$baseUrl/chat/$idConversacion/adjuntos",
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

/**
 * Red de seguridad, no el camino principal: los mensajes llegan al instante por
 * el socket, que ademas detecta si murio (latido) y avisa al reconectar.
 */
private const val INTERVALO_REVISION_MS = 20_000L

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
private data class CuerpoConversacion(@SerialName("id_conversacion") val idConversacion: String)

@Serializable
private data class CuerpoEnviarMensaje(
    @SerialName("id_conversacion") val idConversacion: String,
    @SerialName("texto_mensaje") val texto: String,
    @SerialName("instante_enviado") val instante: String,
    @SerialName("autor_remitente") val autor: AutorMensaje,
    /** Ausente (no `null`: `explicitNulls = false`) cuando el mensaje no lleva adjunto. */
    @SerialName("id_adjunto") val idAdjunto: String? = null,
)

@Serializable
private data class CuerpoAcuse(
    @SerialName("id_conversacion") val idConversacion: String,
    @SerialName("instante_recibido") val instante: String,
)

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
    baseUrl: String,
): MensajeChat {
    val adjuntoDominio = adjunto?.let { remoto ->
        val ruta = archivos.rutaLocalDe(idConversacion, remoto.idAdjunto, remoto.nombre)
        if (!archivos.existe(ruta)) {
            // El backend devuelve la ruta relativa: no sabe bajo que dominio
            // lo publica Caddy. Se resuelve contra la misma BASE_URL de todo.
            val url = if (remoto.url.startsWith("http")) remoto.url else baseUrl + remoto.url
            val respuesta = cliente.get(url)
            if (respuesta.status.isSuccess()) {
                val bytes: ByteArray = respuesta.body()
                archivos.guardarBytes(ruta, bytes)
            }
        }
        Adjunto(idAdjunto = remoto.idAdjunto, tipo = remoto.tipo, nombre = remoto.nombre, rutaLocal = ruta, tipoMime = remoto.tipoMime)
    }
    return MensajeChat(idMensaje, autor, texto, instante, tipo, adjuntoDominio)
}
