package com.eter.salud.data.repository

import com.eter.salud.data.red.CanalTiempoReal
import com.eter.salud.data.red.JsonRed
import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.ConfirmacionCita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.ReservaFranja
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.FalloCita
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * `CitasRepositorio` contra el backend en Go (`docs/CONTRATOS_BACKEND.md`,
 * seccion 9). `agendaDelMedico` es el unico metodo que no es peticion/respuesta:
 * se alimenta del canal `agenda:{idMedico}` del socket compartido
 * [conexion], donde el backend emite la agenda completa (no un diff) en cada
 * alta, cambio o bloqueo -- asi el cliente nunca tiene que fusionar parches a
 * mano ni puede quedarse con una lista a medio actualizar.
 *
 * [proponerCita], [aceptarPropuesta] y [rechazarPropuesta] no estaban en el
 * mapeo original: son la contraparte en Go de la propuesta de cita desde el
 * medico (ver el addendum al final de `docs/CONTRATOS_BACKEND.md`).
 */
class CitasRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
    private val conexion: CanalTiempoReal,
) : CitasRepositorio {

    override suspend fun franjasLibres(idMedico: String, desde: String, ahora: String): Result<List<FranjaAgenda>> {
        val respuesta = cliente.get("$baseUrl/profesionales/$idMedico/franjas-libres") {
            parameter("desde", desde)
            parameter("ahora", ahora)
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun reservarTemporalmente(idFranja: String, ahora: String): Result<ReservaFranja> {
        val respuesta = cliente.post("$baseUrl/franjas/$idFranja/reserva") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoAhora(ahora))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun liberarReserva(idReserva: String): Result<Unit> {
        val respuesta = cliente.delete("$baseUrl/reservas/$idReserva")
        return if (respuesta.status.isSuccess()) {
            Result.success(Unit)
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun confirmarCita(
        idReserva: String,
        idPaciente: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<ConfirmacionCita> {
        val respuesta = cliente.post("$baseUrl/reservas/$idReserva/confirmacion") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoConfirmarCita(idPaciente, contacto, ahora))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override fun agendaDelMedico(idMedico: String): Flow<List<Cita>> =
        conexion.canal("agenda:$idMedico") { payload -> JsonRed.decodeFromJsonElement<List<Cita>>(payload) }

    override suspend fun cargarAgenda(idMedico: String): Result<List<Cita>> {
        val respuesta = cliente.get("$baseUrl/profesionales/$idMedico/agenda")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun cambiarEstado(idCita: String, nuevo: EstadoCita): Result<Cita> {
        val respuesta = cliente.patch("$baseUrl/citas/$idCita/estado") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoEstado(nuevo))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun bloquearHorario(
        idMedico: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        nota: String,
    ): Result<Cita> {
        val respuesta = cliente.post("$baseUrl/profesionales/$idMedico/bloqueos") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoBloqueo(fecha, horaInicio, horaFin, nota))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun reprogramar(idCita: String, idFranjaNueva: String, ahora: String): Result<Cita> {
        val respuesta = cliente.post("$baseUrl/citas/$idCita/reprogramacion") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoReprogramacion(idFranjaNueva, ahora))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun proponerCita(
        idMedico: String,
        idPaciente: String,
        idFranja: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<Cita> {
        val respuesta = cliente.post("$baseUrl/franjas/$idFranja/propuesta") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoPropuesta(idPaciente, contacto, ahora))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun aceptarPropuesta(idCita: String): Result<Cita> {
        val respuesta = cliente.post("$baseUrl/citas/$idCita/aceptar-propuesta")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    override suspend fun rechazarPropuesta(idCita: String): Result<Cita> {
        val respuesta = cliente.post("$baseUrl/citas/$idCita/rechazar-propuesta")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            Result.failure(falloDesde(respuesta.motivoDeErrorCita()))
        }
    }

    private suspend fun HttpResponse.motivoDeErrorCita(): MotivoFalloCita? = motivoDeError<MotivoFalloCita>()

    private fun falloDesde(motivo: MotivoFalloCita?): FalloCita = FalloCita(motivo ?: MotivoFalloCita.SIN_CONEXION)
}

@Serializable
private data class CuerpoAhora(val ahora: String)

@Serializable
private data class CuerpoConfirmarCita(val idPaciente: String, val contacto: DatosContactoCita, val ahora: String)

@Serializable
private data class CuerpoEstado(val estado: EstadoCita)

@Serializable
private data class CuerpoBloqueo(val fecha: String, val horaInicio: String, val horaFin: String, val nota: String)

@Serializable
private data class CuerpoReprogramacion(val idFranjaNueva: String, val ahora: String)

@Serializable
private data class CuerpoPropuesta(val idPaciente: String, val contacto: DatosContactoCita, val ahora: String)
