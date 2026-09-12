package com.eter.salud.data.red

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuerpo de error real del backend (`mapeo-endpoints.md`, seccion Base): tanto
 * Axum como PostgREST lo devuelven como `{"message": "MOTIVO"}` -- un motivo
 * tipado en el campo `message`, nunca prosa libre.
 */
@Serializable
data class CuerpoError(@SerialName("message") val motivo: String? = null)

/**
 * Traduce el cuerpo de una respuesta fallida al motivo tipado de este
 * repositorio, si el backend lo mando y coincide con algun valor conocido.
 *
 * `null` -- ya sea porque el cuerpo no vino, no es JSON valido, o trae un
 * motivo que esta version de la app no conoce -- deja que quien llama caiga a
 * su propio valor por defecto. Ese "no reconocido" NUNCA debe tratarse como
 * exito: quien llama a esta funcion es porque [HttpResponse.status] ya fallo.
 */
suspend inline fun <reified M : Enum<M>> HttpResponse.motivoDeError(): M? {
    val cuerpo = runCatching { body<CuerpoError>() }.getOrNull() ?: return null
    return enumValues<M>().firstOrNull { it.name == cuerpo.motivo }
}

/**
 * Fallo de red para los contratos que no llevan un motivo tipado propio
 * (`PacienteRepositorio`, `HistorialMedicoRepositorio`, `AdherenciaRepositorio`,
 * `PacientesVinculadosRepositorio`, `DirectorioMedicoRepositorio`,
 * `DiarioRepositorio`): la capa de presentacion de estos modulos ya distingue
 * solo exito de fallo, sin una causa que deba pintarse distinta, asi que un
 * codigo HTTP en el mensaje basta para depurar sin inventar un enum que nadie
 * va a leer desde la Vista.
 */
class FalloDeRedGenerico(val codigoHttp: Int) : Exception("HTTP $codigoHttp")
