package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess

/**
 * `PacientesVinculadosRepositorio` contra el backend real
 * (`mapeo-endpoints.md`, seccion 6): la vista `/pacientes_vinculados` no
 * acepta parametro -- el medico sale del JWT de la sesion, nunca de [idMedico]
 * -- asi que ese argumento se ignora aqui a proposito y no por descuido; se
 * conserva en la firma porque el contrato de dominio lo exige para todas las
 * implementaciones (la local y la en memoria si lo usan).
 */
class PacientesVinculadosRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : PacientesVinculadosRepositorio {

    override suspend fun obtenerPacientesVinculados(idMedico: String): Result<List<PacienteVinculado>> {
        val respuesta = cliente.get("$baseUrl/pacientes_vinculados")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }
}
