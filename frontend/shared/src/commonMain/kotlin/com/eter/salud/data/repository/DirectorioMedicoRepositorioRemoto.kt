package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `DirectorioMedicoRepositorio` contra el backend real (`mapeo-endpoints.md`,
 * seccion 7): las busquedas van sobre la vista `/directorio_medico` de
 * PostgREST (filtros con el prefijo `eq.` de su sintaxis), y las
 * vinculaciones son funciones RPC.
 */
class DirectorioMedicoRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : DirectorioMedicoRepositorio {

    override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> {
        val respuesta = cliente.post("$baseUrl/rpc/medico_vinculado") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoIdPaciente(idPaciente))
        }
        return if (respuesta.status.isSuccess()) {
            // Objeto con TODOS los campos null cuando no tiene vinculo, no un
            // 404: la fila existe siempre, vacia o llena.
            Result.success(respuesta.body<MedicoVinculadoNulable>().aDominioONull())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun buscarDirectorio(especialidad: Especialidad?): Result<List<PerfilDoctorDirectorio>> {
        val respuesta = cliente.get("$baseUrl/directorio_medico") {
            especialidad?.let { parameter("especialidad", "eq.${it.name}") }
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun obtenerMedicosVinculados(idPaciente: String): Result<List<MedicoVinculado>> {
        val respuesta = cliente.post("$baseUrl/rpc/medicos_vinculados") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoIdPaciente(idPaciente))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun obtenerPerfilDeMedico(idMedico: String): Result<PerfilDoctorDirectorio?> {
        val respuesta = cliente.get("$baseUrl/directorio_medico") {
            parameter("idMedico", "eq.$idMedico")
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(respuesta.body<List<PerfilDoctorDirectorio>>().firstOrNull())
    }

    override suspend fun solicitarVinculacion(idPaciente: String, idMedico: String): Result<MedicoVinculado> {
        val respuesta = cliente.post("$baseUrl/rpc/solicitar_vinculacion") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoIdMedico(idMedico))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }
}

@Serializable
private data class CuerpoIdPaciente(@SerialName("id_paciente") val idPaciente: String)

@Serializable
private data class CuerpoIdMedico(@SerialName("id_medico") val idMedico: String)

/** Espejo de `MedicoVinculado` con todos los campos opcionales: la fila existe siempre, vacia o llena. */
@Serializable
private data class MedicoVinculadoNulable(
    val idMedico: String? = null,
    val nombreCompleto: String? = null,
    val especialidad: Especialidad? = null,
    val idConversacion: String? = null,
) {
    fun aDominioONull(): MedicoVinculado? {
        if (idMedico.isNullOrBlank()) return null
        return MedicoVinculado(
            idMedico = idMedico,
            nombreCompleto = nombreCompleto.orEmpty(),
            // Defensivo: si el backend alguna vez manda solo idMedico sin
            // especialidad, no tiene sentido fallar la pantalla entera por eso.
            especialidad = especialidad ?: Especialidad.MEDICINA_GENERAL,
            idConversacion = idConversacion.orEmpty(),
        )
    }
}
