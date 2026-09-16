package com.eter.salud.data.repository

import com.eter.salud.data.red.FalloDeRedGenerico
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * `DiarioRepositorio` contra la vista `/entradas_diario` de PostgREST
 * (`docs/mapeo-endpoints.md`, seccion 10). Antes apuntaba a rutas REST del
 * contrato original (`/pacientes/{id}/diario`) que el backend nunca expuso.
 *
 * Es la fuente del MEDICO: el diario del paciente vive en el telefono del
 * paciente, y el medico solo puede verlo aqui, una vez subido. El paciente no
 * usa esta clase directo sino [DiarioRepositorioSincronizado], que escribe
 * primero en el telefono (el diario tiene que poder escribirse sin cobertura)
 * y sube despues.
 *
 * El id de cada entrada lo genera el telefono y viaja tal cual: asi subir la
 * misma entrada dos veces choca con la llave primaria (409) en vez de
 * duplicarla, y ese 409 se trata como "ya estaba".
 */
class DiarioRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : DiarioRepositorio {

    /** Lectura unica: el backend aun no emite eventos de diario en vivo. */
    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> = flow {
        listar(idPaciente).getOrNull()?.let { emit(it) }
    }

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> {
        val respuesta = cliente.post("$baseUrl/entradas_diario") {
            contentType(ContentType.Application.Json)
            header("Prefer", "return=minimal")
            setBody(entrada)
        }
        return if (respuesta.status.isSuccess() || respuesta.status == HttpStatusCode.Conflict) {
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun eliminar(idEntrada: String): Result<Unit> {
        val respuesta = cliente.delete("$baseUrl/entradas_diario") {
            parameter("idEntrada", "eq.$idEntrada")
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(Unit)
        } else {
            Result.failure(FalloDeRedGenerico(respuesta.status.value))
        }
    }

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> {
        val respuesta = cliente.get("$baseUrl/entradas_diario") {
            parameter("idPaciente", "eq.$idPaciente")
            parameter("order", "instante.desc")
            parameter("limit", "1")
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(respuesta.body<List<EntradaDiarioRed>>().firstOrNull()?.aDominio())
    }

    /** Ids que el servidor ya tiene, para subir solo lo que falta. */
    suspend fun idsEnServidor(idPaciente: String): Result<Set<String>> {
        val respuesta = cliente.get("$baseUrl/entradas_diario") {
            parameter("idPaciente", "eq.$idPaciente")
            parameter("select", "idEntrada")
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(respuesta.body<List<SoloId>>().mapTo(mutableSetOf()) { it.idEntrada })
    }

    private suspend fun listar(idPaciente: String): Result<List<EntradaDiario>> {
        val respuesta = cliente.get("$baseUrl/entradas_diario") {
            parameter("idPaciente", "eq.$idPaciente")
            parameter("order", "instante.desc")
        }
        if (!respuesta.status.isSuccess()) return Result.failure(FalloDeRedGenerico(respuesta.status.value))
        return Result.success(respuesta.body<List<EntradaDiarioRed>>().map { it.aDominio() })
    }
}

@Serializable
private data class SoloId(val idEntrada: String)

/**
 * La entrada como la devuelve el servidor: ademas del color que calculo la app
 * del paciente, el que calculo el backend al guardarla (0019), con lo que
 * detecto. El medico ve el MAS GRAVE de los dos: un telefono con el
 * diccionario viejo ya no esconde una senal de alarma.
 */
@Serializable
private data class EntradaDiarioRed(
    val idEntrada: String,
    val idPaciente: String,
    val instante: String,
    val fecha: String,
    val texto: String,
    val severidad: SeveridadDiario,
    val terminosDetectados: List<String> = emptyList(),
    val severidadServidor: SeveridadDiario? = null,
    val analisisRiesgo: AnalisisRiesgoRed? = null,
) {
    fun aDominio() = EntradaDiario(
        idEntrada = idEntrada,
        idPaciente = idPaciente,
        instante = instante,
        fecha = fecha,
        texto = texto,
        severidad = listOfNotNull(severidad, severidadServidor).maxBy { it.codigo },
        terminosDetectados = (terminosDetectados + analisisRiesgo?.hallazgos.orEmpty().map { it.termino }).distinct(),
    )
}

@Serializable
private data class AnalisisRiesgoRed(val hallazgos: List<HallazgoRed> = emptyList())

@Serializable
private data class HallazgoRed(val termino: String)
