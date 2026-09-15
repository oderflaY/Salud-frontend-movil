package com.eter.salud.data.repository

import com.eter.salud.data.red.CanalTiempoReal
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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `CitasRepositorio` contra el backend real (`docs/mapeo-endpoints.md`,
 * seccion 9): cada operacion es una funcion RPC de PostgREST, con parametros
 * en snake_case. Este archivo apuntaba a las rutas REST del contrato original
 * (`/profesionales/{id}/agenda`, `/franjas/{id}/reserva`...), que el backend
 * nunca expuso: agendar, ver la agenda y bloquear horarios respondian 404.
 *
 * [agendaDelMedico] es el unico metodo en vivo. El canal `agenda:{idMedico}`
 * avisa QUE cambio algo (`cita_creada`, `cita_actualizada`, `agenda_bloqueada`,
 * con apenas el id), no manda la agenda completa: cada aviso dispara una
 * lectura nueva con [cargarAgenda]. Asi la lista nunca queda a medio fusionar.
 *
 * [proponerCita], [aceptarPropuesta] y [rechazarPropuesta] son la propuesta de
 * cita desde el medico (0014_propuestas_de_cita.sql).
 */
class CitasRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
    private val conexion: CanalTiempoReal,
) : CitasRepositorio {

    override suspend fun franjasLibres(idMedico: String, desde: String, ahora: String): Result<List<FranjaAgenda>> =
        rpc("franjas_libres", CuerpoFranjasLibres(idMedico, desde, ahora)) { it.body() }

    override suspend fun reservarTemporalmente(idFranja: String, ahora: String): Result<ReservaFranja> =
        rpc("reservar_temporalmente", CuerpoReserva(idFranja, ahora)) { it.body() }

    override suspend fun liberarReserva(idReserva: String): Result<Unit> =
        rpc("liberar_reserva", CuerpoIdReserva(idReserva)) { }

    override suspend fun confirmarCita(
        idReserva: String,
        idPaciente: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<ConfirmacionCita> =
        rpc("confirmar_cita", CuerpoConfirmarCita(idReserva, idPaciente, contacto, ahora)) { it.body() }

    override fun agendaDelMedico(idMedico: String): Flow<List<Cita>> =
        conexion.canal("agenda:$idMedico") { it }
            .mapNotNull { cargarAgenda(idMedico).getOrNull() }

    override suspend fun cargarAgenda(idMedico: String): Result<List<Cita>> =
        rpc("cargar_agenda", CuerpoAgendaDeMedico(idMedico)) { it.body() }

    override suspend fun cambiarEstado(idCita: String, nuevo: EstadoCita): Result<Cita> =
        rpc("cambiar_estado", CuerpoCambiarEstado(idCita, nuevo)) { it.body() }

    override suspend fun bloquearHorario(
        idMedico: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        nota: String,
    ): Result<Cita> {
        // El backend bloquea franja por franja y devuelve un bloqueo por cada
        // una; la agenda se repinta sola por el canal en vivo. Se devuelve el
        // primero como representante. Un rango sin franjas no bloquea nada, y
        // se reporta como fallo para que el medico no crea que quedo apartado.
        val bloqueos = rpc("bloquear_horario", CuerpoBloqueo(idMedico, fecha, horaInicio, horaFin, nota)) {
            it.body<List<Cita>>()
        }
        return bloqueos.mapCatching { it.firstOrNull() ?: throw FalloCita(MotivoFalloCita.SIN_CONEXION) }
    }

    override suspend fun reprogramar(idCita: String, idFranjaNueva: String, ahora: String): Result<Cita> =
        rpc("reprogramar", CuerpoReprogramar(idCita, idFranjaNueva, ahora)) { it.body() }

    /** [idMedico] no viaja: el backend lo toma del JWT y exige que la franja sea suya. */
    override suspend fun proponerCita(
        idMedico: String,
        idPaciente: String,
        idFranja: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<Cita> =
        rpc("proponer_cita", CuerpoPropuesta(idFranja, idPaciente, contacto, ahora)) { it.body() }

    override suspend fun aceptarPropuesta(idCita: String): Result<Cita> =
        rpc("aceptar_propuesta", CuerpoIdCita(idCita)) { it.body() }

    override suspend fun rechazarPropuesta(idCita: String): Result<Cita> =
        rpc("rechazar_propuesta", CuerpoIdCita(idCita)) { it.body() }

    /** Una llamada RPC: el error del backend se traduce al motivo tipado de citas. */
    private suspend inline fun <reified C : Any, T> rpc(
        funcion: String,
        cuerpo: C,
        leer: (HttpResponse) -> T,
    ): Result<T> {
        val respuesta = cliente.post("$baseUrl/rpc/$funcion") {
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(leer(respuesta))
        } else {
            Result.failure(FalloCita(respuesta.motivoDeError<MotivoFalloCita>() ?: MotivoFalloCita.SIN_CONEXION))
        }
    }
}

@Serializable
private data class CuerpoFranjasLibres(
    @SerialName("id_medico") val idMedico: String,
    val desde: String,
    val ahora: String,
)

@Serializable
private data class CuerpoReserva(@SerialName("id_franja") val idFranja: String, val ahora: String)

@Serializable
private data class CuerpoIdReserva(@SerialName("id_reserva") val idReserva: String)

@Serializable
private data class CuerpoConfirmarCita(
    @SerialName("id_reserva") val idReserva: String,
    @SerialName("id_paciente") val idPaciente: String,
    val contacto: DatosContactoCita,
    val ahora: String,
)

@Serializable
private data class CuerpoAgendaDeMedico(@SerialName("id_medico") val idMedico: String)

@Serializable
private data class CuerpoIdCita(@SerialName("id_cita") val idCita: String)

@Serializable
private data class CuerpoCambiarEstado(
    @SerialName("id_cita") val idCita: String,
    @SerialName("nuevo_estado") val nuevoEstado: EstadoCita,
)

@Serializable
private data class CuerpoBloqueo(
    @SerialName("id_medico") val idMedico: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val nota: String,
)

@Serializable
private data class CuerpoReprogramar(
    @SerialName("id_cita") val idCita: String,
    @SerialName("id_franja_nueva") val idFranjaNueva: String,
    val ahora: String,
)

@Serializable
private data class CuerpoPropuesta(
    @SerialName("id_franja") val idFranja: String,
    @SerialName("id_paciente") val idPaciente: String,
    val contacto: DatosContactoCita,
    val ahora: String,
)
