package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * `HistorialMedicoRepositorio` contra el backend real (`mapeo-endpoints.md`,
 * seccion 3): PostgREST sobre la vista `paciente_expediente` para leer, y la
 * funcion `actualizar_historial` para escribir.
 */
class HistorialMedicoRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : HistorialMedicoRepositorio {

    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> {
        val respuesta = cliente.get("$baseUrl/paciente_expediente") {
            parameter("idPaciente", "eq.$idPaciente")
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        // La vista siempre devuelve un array (0 o 1 elemento), nunca un objeto suelto.
        val paciente = respuesta.body<List<PacienteDto>>().firstOrNull()
            ?: return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(paciente)
    }

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> {
        requireNotNull(paciente.idPaciente) { "actualizarHistorial requiere idPaciente" }
        val respuesta = cliente.post("$baseUrl/rpc/actualizar_historial") {
            contentType(ContentType.Application.Json)
            // Un solo parametro jsonb: el body es el DTO completo, en camelCase.
            header("Prefer", "params=single-object")
            setBody(paciente)
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }
}
