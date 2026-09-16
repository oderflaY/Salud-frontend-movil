package com.eter.salud.data.red

import com.eter.salud.data.seguridad.Sha256
import com.eter.salud.data.seguridad.aHex
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Una respuesta del servidor guardada en el telefono para verla sin conexion. */
@Serializable
data class RespuestaGuardada(
    val estado: Int,
    val tipoContenido: String,
    val cuerpo: String,
)

/** Donde se guardan las respuestas. En disco en la app; en memoria en las pruebas. */
interface AlmacenDeRespuestas {
    suspend fun leer(clave: String): RespuestaGuardada?
    suspend fun guardar(clave: String, respuesta: RespuestaGuardada)

    /** Al cerrar sesion: los datos clinicos de una cuenta no se quedan para la siguiente. */
    suspend fun borrarTodo()
}

class AlmacenDeRespuestasEnMemoria : AlmacenDeRespuestas {
    private val candado = Mutex()
    private val respuestas = mutableMapOf<String, RespuestaGuardada>()

    override suspend fun leer(clave: String) = candado.withLock { respuestas[clave] }
    override suspend fun guardar(clave: String, respuesta: RespuestaGuardada) = candado.withLock { respuestas[clave] = respuesta }
    override suspend fun borrarTodo() = candado.withLock { respuestas.clear() }
}

/** Encabezado que marca una respuesta servida desde el telefono y no desde el servidor. */
const val ENCABEZADO_SIN_CONEXION = "X-Salud-Sin-Conexion"

class ConfiguracionRespaldo {
    var almacen: AlmacenDeRespuestas? = null
    var monitor: MonitorDeConexion = EstadoDeConexion

    /** Quien tiene la sesion abierta: la copia de un paciente nunca se le sirve a otro. */
    var identidad: suspend () -> String? = { null }
}

/**
 * Copia local de las lecturas para que la app se vea igual sin conexion.
 *
 *  1. Cada lectura que el servidor responde bien (JSON) se guarda.
 *  2. Si una lectura no llega al servidor, se responde con la ultima copia.
 *  3. Si ya se sabe que no hay conexion, la copia se sirve al instante, sin
 *     esperar a que la peticion agote su tiempo: la pantalla no se queda
 *     cargando seis segundos para acabar mostrando lo mismo.
 *
 * Las escrituras (enviar un mensaje, registrar una toma) nunca se contestan
 * con una copia: fingir que se guardo algo que no llego seria peor que el
 * error. El diario ya se escribe primero en el telefono y se sube despues.
 *
 * Cuando vuelve la conexion, [MonitorDeConexion.reconexiones] hace que las
 * pantallas recarguen y la copia se reemplaza con los datos del servidor.
 */
val RespaldoSinConexion = createClientPlugin("RespaldoSinConexion", ::ConfiguracionRespaldo) {
    val almacen = pluginConfig.almacen
    val monitor = pluginConfig.monitor
    val identidad = pluginConfig.identidad

    on(Send) { peticion ->
        val clave = if (almacen != null) claveDeLectura(peticion, identidad()) else null

        if (clave != null && !monitor.enLinea.value) {
            almacen?.leer(clave)?.let { return@on llamadaDesdeCopia(client, peticion, it) }
        }

        val llamada = try {
            proceed(peticion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            monitor.registrarFalloDeRed()
            val copia = clave?.let { almacen?.leer(it) } ?: throw e
            return@on llamadaDesdeCopia(client, peticion, copia)
        }
        monitor.registrarRespuesta()

        val respuesta = llamada.response
        if (clave == null || almacen == null || !respuesta.status.isSuccess() ||
            respuesta.contentType()?.match(ContentType.Application.Json) != true
        ) {
            return@on llamada
        }
        val guardable = llamada.save()
        runCatching {
            almacen.guardar(
                clave,
                RespuestaGuardada(respuesta.status.value, respuesta.contentType().toString(), guardable.response.bodyAsText()),
            )
        }
        guardable
    }
}

/**
 * Llave de la copia, o `null` si la peticion no es una lectura.
 *
 * No incluye el host: en desarrollo la IP del servidor cambia y la copia debe
 * seguir sirviendo. Si incluye la cuenta y el cuerpo (en PostgREST las
 * lecturas `rpc/...` van por POST con los parametros en el cuerpo).
 */
internal fun claveDeLectura(peticion: HttpRequestBuilder, identidad: String?): String? {
    if (identidad.isNullOrBlank()) return null
    val ruta = peticion.url.encodedPath
    val esLectura = when (peticion.method) {
        HttpMethod.Get -> ruta !in RUTAS_SIN_COPIA && !ruta.startsWith("/realtime") && !ruta.startsWith("/tarjetas/")
        HttpMethod.Post -> ruta.substringAfterLast("/rpc/", "") in RPC_DE_LECTURA
        else -> false
    }
    if (!esLectura) return null
    val cuerpo = when (val contenido = peticion.body) {
        is TextContent -> contenido.text
        is OutgoingContent.NoContent -> ""
        else -> return null
    }
    val consulta = peticion.url.parameters.entries()
        .sortedBy { it.key }
        .joinToString("&") { (nombre, valores) -> "$nombre=${valores.joinToString(",")}" }
    val llave = "$identidad\n${peticion.method.value}\n$ruta?$consulta\n$cuerpo"
    return Sha256.resumen(llave.encodeToByteArray()).aHex()
}

/**
 * Las funciones de PostgREST que solo leen. Las demas escriben algo y no se
 * contestan con copias. `franjas_libres` y `obtener_respuesta_automatica` no
 * estan: dependen del momento en que se piden.
 */
private val RPC_DE_LECTURA = setOf(
    "obtener_historial",
    "mensajes_sin_leer",
    "medico_vinculado",
    "medicos_vinculados",
    "tomas_del_dia",
    "adherencia_semana",
    "adherencia_resumen",
    "cargar_agenda",
)

private val RUTAS_SIN_COPIA = setOf("/healthz", "/api/v1/auth/me")

@OptIn(InternalAPI::class)
private fun llamadaDesdeCopia(cliente: HttpClient, peticion: HttpRequestBuilder, copia: RespuestaGuardada): HttpClientCall {
    val datosPeticion = peticion.build()
    val datosRespuesta = HttpResponseData(
        statusCode = HttpStatusCode.fromValue(copia.estado),
        requestTime = GMTDate(),
        headers = Headers.build {
            append(HttpHeaders.ContentType, copia.tipoContenido)
            append(ENCABEZADO_SIN_CONEXION, "1")
        },
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(copia.cuerpo.encodeToByteArray()),
        callContext = datosPeticion.executionContext,
    )
    return HttpClientCall(cliente, datosPeticion, datosRespuesta)
}
