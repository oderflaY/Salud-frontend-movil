package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `AdherenciaRepositorio` contra el backend real (`mapeo-endpoints.md`,
 * seccion 4): las cuatro operaciones son funciones RPC de PostgREST, con
 * parametros en snake_case (a diferencia del resto del contrato, que es
 * camelCase) -- son nombres de parametro de la funcion SQL, no campos de un
 * DTO.
 */
class AdherenciaRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : AdherenciaRepositorio {

    override suspend fun obtenerTomasDelDia(idPaciente: String, fecha: String): Result<List<TomaDelDia>> {
        val respuesta = cliente.post("$baseUrl/rpc/tomas_del_dia") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoTomasDelDia(idPaciente, fecha))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit> {
        val respuesta = cliente.post("$baseUrl/rpc/registrar_toma") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoRegistrarToma(idToma, estado, instante))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun obtenerSemana(idPaciente: String, fechaFinal: String): Result<List<DiaDeAdherencia>> {
        val respuesta = cliente.post("$baseUrl/rpc/adherencia_semana") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoConFechaFinal(idPaciente, fechaFinal))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun obtenerResumenSemanal(idPaciente: String, fechaFinal: String): Result<ResumenAdherencia> {
        val respuesta = cliente.post("$baseUrl/rpc/adherencia_resumen") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoConFechaFinal(idPaciente, fechaFinal))
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        // Viene envuelto en un array de 1 elemento, no un objeto suelto.
        val resumen = respuesta.body<List<ResumenAdherencia>>().firstOrNull()
            ?: return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(resumen)
    }
}

@Serializable
private data class CuerpoTomasDelDia(
    @SerialName("id_paciente") val idPaciente: String,
    val fecha: String,
)

@Serializable
private data class CuerpoRegistrarToma(
    @SerialName("id_toma") val idToma: String,
    val estado: EstadoToma,
    val instante: String,
)

@Serializable
private data class CuerpoConFechaFinal(
    @SerialName("id_paciente") val idPaciente: String,
    @SerialName("fecha_final") val fechaFinal: String,
)
