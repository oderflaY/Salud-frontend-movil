package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * `DiarioRepositorio` contra el backend en Go (`docs/CONTRATOS_BACKEND.md`,
 * seccion 10).
 *
 * ## Limitacion deliberada de [entradasDe]
 *
 * El contrato es explicito: el diario es local-first POR DISENO, no por falta
 * de backend, y no define un endpoint REST para listar todas las entradas (solo
 * `guardar`, `eliminar` y `ultimaEntradaDe`). Por eso [entradasDe] no lee de la
 * red: es una cache en memoria, por paciente, que solo esta paciente actualiza
 * con lo que ESTE MISMO repositorio escribe o borra en este proceso. No
 * refleja entradas escritas desde otro dispositivo ni sobrevive a reiniciar la
 * app.
 *
 * Por esa razon [com.eter.salud.data.local.ContenedorSalud] sigue usando
 * `DiarioRepositorioLocal` (SQLite) como fuente de verdad del diario incluso
 * cuando el resto de los repositorios pasan a la red -- ver
 * `ConfiguracionApi.USAR_BACKEND_REMOTO`. Esta clase existe para completar el
 * contrato y para poder probarlo, no para sustituir a la version local.
 */
class DiarioRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : DiarioRepositorio {

    private val entradasPorPaciente = mutableMapOf<String, MutableStateFlow<List<EntradaDiario>>>()

    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> = flujoDe(idPaciente)

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> {
        val respuesta = cliente.post("$baseUrl/pacientes/${entrada.idPaciente}/diario") {
            contentType(ContentType.Application.Json)
            setBody(entrada)
        }
        return if (respuesta.status.isSuccess()) {
            val flujo = flujoDe(entrada.idPaciente)
            flujo.value = (listOf(entrada) + flujo.value.filterNot { it.idEntrada == entrada.idEntrada })
                .sortedByDescending { it.instante }
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun eliminar(idEntrada: String): Result<Unit> {
        val respuesta = cliente.delete("$baseUrl/diario/$idEntrada")
        return if (respuesta.status.isSuccess()) {
            entradasPorPaciente.values.forEach { flujo ->
                flujo.value = flujo.value.filterNot { it.idEntrada == idEntrada }
            }
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> {
        val respuesta = cliente.get("$baseUrl/pacientes/$idPaciente/diario/ultima")
        return when {
            respuesta.status == HttpStatusCode.NotFound -> Result.success(null)
            respuesta.status.isSuccess() -> Result.success(respuesta.body())
            else -> Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    private fun flujoDe(idPaciente: String): MutableStateFlow<List<EntradaDiario>> =
        entradasPorPaciente.getOrPut(idPaciente) { MutableStateFlow(emptyList()) }
}
