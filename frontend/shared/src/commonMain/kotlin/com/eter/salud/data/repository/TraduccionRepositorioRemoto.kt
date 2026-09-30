package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.repository.TraduccionRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /chat/traducir` del backend (`mapeo-endpoints.md`, seccion IA).
 *
 * ## Por que espera mucho mas que el resto de la app
 *
 * El tope general del cliente son 15 s, pensado para consultas a la base. Un
 * modelo de traduccion tarda unos diez segundos por mensaje -- y mas si estaba
 * dormido --, asi que esta llamada lleva su propio tope; con el general, toda
 * traduccion fallaba justo antes de llegar.
 *
 * ## Memoria de lo ya traducido
 *
 * El mismo mensaje se vuelve a pedir cada vez que se abre el chat. Guardar la
 * traduccion mientras la app vive evita repetir una espera de diez segundos
 * (y el gasto del modelo) por algo que no cambia.
 */
class TraduccionRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : TraduccionRepositorio {

    private val candado = Mutex()
    private val memoria = mutableMapOf<Pair<String, String>, String>()

    override suspend fun traducir(texto: String, idiomaDestino: String): Result<String> {
        val recortado = texto.trim()
        if (recortado.isEmpty()) return Result.success("")
        val llave = recortado to idiomaDestino
        candado.withLock { memoria[llave] }?.let { return Result.success(it) }

        val respuesta = cliente.post("$baseUrl/chat/traducir") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoTraduccion(recortado, idiomaDestino))
            timeout {
                requestTimeoutMillis = ESPERA_MAXIMA_MS
                // Tambien el de lectura: el motor de Android (OkHttp) corta a
                // los 10 s por su cuenta si no se le dice otra cosa, y el
                // modelo tarda mas que eso en contestar.
                socketTimeoutMillis = ESPERA_MAXIMA_MS
            }
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))

        val traduccion = respuesta.body<RespuestaTraduccion>().traduccion.trim()
        if (traduccion.isEmpty()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        candado.withLock { memoria[llave] = traduccion }
        return Result.success(traduccion)
    }

    private companion object {
        const val ESPERA_MAXIMA_MS = 90_000L
    }
}

@Serializable
private data class CuerpoTraduccion(
    val texto: String,
    @SerialName("idiomaDestino") val idiomaDestino: String,
)

@Serializable
private data class RespuestaTraduccion(val traduccion: String)
