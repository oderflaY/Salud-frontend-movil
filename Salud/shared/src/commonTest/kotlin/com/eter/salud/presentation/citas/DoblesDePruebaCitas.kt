package com.eter.salud.presentation.citas

import com.eter.salud.domain.model.CanalNotificacion
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.ConfirmacionCita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.ReservaFranja
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.FalloCita
import com.eter.salud.domain.time.RelojSalud
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reloj que se puede mover a mano.
 *
 * `RelojFijo` no sirve para la agenda: la caducidad de una retencion solo se
 * puede probar avanzando el tiempo, y con un instante constante nunca vencería.
 */
class RelojAjustable(
    var fecha: String = "2026-09-08",
    var instante: String = "2026-09-08T15:00:00Z",
    private val hora: String = "15:00",
) : RelojSalud {
    override fun fechaHoy(): String = fecha
    override fun instanteActual(): String = instante
    override fun horaLocal(instanteIso: String): String = if (instanteIso.isBlank()) "" else hora
}

/** Agenda falsa: registra lo que se le pide y permite simular cada fallo tipado. */
class CitasRepositorioFalso(
    private val franjas: Result<List<FranjaAgenda>> = Result.success(FRANJAS_DEMO),
    private val resultadoReserva: Result<ReservaFranja>? = null,
    private val resultadoConfirmacion: Result<ConfirmacionCita>? = null,
    private val agendaInicial: Result<List<Cita>> = Result.success(emptyList()),
    private val resultadoCambioEstado: Result<Cita>? = null,
    private val resultadoBloqueo: Result<Cita>? = null,
    private val resultadoReprogramacion: Result<Cita>? = null,
) : CitasRepositorio {

    private val flujo = MutableStateFlow(agendaInicial.getOrNull().orEmpty())

    var franjasPedidas: Int = 0
        private set
    val reservasSolicitadas: MutableList<String> = mutableListOf()
    val reservasLiberadas: MutableList<String> = mutableListOf()
    var contactoConfirmado: DatosContactoCita? = null
        private set
    val estadosPedidos: MutableList<Pair<String, EstadoCita>> = mutableListOf()
    val bloqueosPedidos: MutableList<Triple<String, String, String>> = mutableListOf()
    val reprogramacionesPedidas: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun franjasLibres(
        idMedico: String,
        desde: String,
        ahora: String,
    ): Result<List<FranjaAgenda>> {
        franjasPedidas++
        return franjas
    }

    override suspend fun reservarTemporalmente(
        idFranja: String,
        ahora: String,
    ): Result<ReservaFranja> {
        reservasSolicitadas += idFranja
        return resultadoReserva ?: Result.success(
            ReservaFranja(
                idReserva = "res_$idFranja",
                franja = FRANJAS_DEMO.first { it.idFranja == idFranja },
                expiraEn = "2026-09-08T15:05:00Z",
            ),
        )
    }

    override suspend fun liberarReserva(idReserva: String): Result<Unit> {
        reservasLiberadas += idReserva
        return Result.success(Unit)
    }

    override suspend fun confirmarCita(
        idReserva: String,
        idPaciente: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<ConfirmacionCita> {
        contactoConfirmado = contacto
        return resultadoConfirmacion ?: Result.success(
            ConfirmacionCita(
                cita = CITA_DEMO.copy(idPaciente = idPaciente, contacto = contacto),
                canalesNotificados = listOf(CanalNotificacion.CORREO),
            ),
        )
    }

    override fun agendaDelMedico(idMedico: String): Flow<List<Cita>> = flujo.asStateFlow()

    override suspend fun cargarAgenda(idMedico: String): Result<List<Cita>> = agendaInicial

    override suspend fun cambiarEstado(idCita: String, nuevo: EstadoCita): Result<Cita> {
        estadosPedidos += idCita to nuevo
        val resultado = resultadoCambioEstado
            ?: Result.success(flujo.value.first { it.idCita == idCita }.copy(estado = nuevo))
        resultado.getOrNull()?.let { actualizada ->
            flujo.value = flujo.value.map { if (it.idCita == idCita) actualizada else it }
        }
        return resultado
    }

    override suspend fun bloquearHorario(
        idMedico: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        nota: String,
    ): Result<Cita> {
        bloqueosPedidos += Triple(fecha, horaInicio, horaFin)
        return resultadoBloqueo ?: Result.success(
            CITA_DEMO.copy(
                idCita = "bloqueo_1",
                fecha = fecha,
                horaInicio = horaInicio,
                horaFin = horaFin,
                estado = EstadoCita.BLOQUEADO,
                contacto = null,
                notaBloqueo = nota,
            ),
        )
    }

    override suspend fun reprogramar(
        idCita: String,
        idFranjaNueva: String,
        ahora: String,
    ): Result<Cita> {
        reprogramacionesPedidas += idCita to idFranjaNueva
        return resultadoReprogramacion ?: Result.success(CITA_DEMO)
    }

    /** Empuja una agenda nueva por el flujo, como haria el backend en tiempo real. */
    fun emitirAgenda(citas: List<Cita>) {
        flujo.value = citas
    }

    companion object {
        fun fallo(motivo: MotivoFalloCita): Result<Nothing> = Result.failure(FalloCita(motivo))

        val FRANJAS_DEMO = listOf(
            franja("2026-09-09", "09:00"),
            franja("2026-09-09", "09:30"),
            franja("2026-09-10", "09:00"),
        )

        val CITA_DEMO = Cita(
            idCita = "cita_1",
            folio = "CITA-4201",
            idMedico = "doc_889900A",
            nombreMedico = "Dra. Elena Ruiz Santos",
            idPaciente = "pac_01H8X9A",
            fecha = "2026-09-09",
            horaInicio = "09:00",
            horaFin = "09:30",
            estado = EstadoCita.PENDIENTE,
            contacto = DatosContactoCita(
                nombreCompleto = "Alfredo Valadez Gonzalez",
                telefono = "6181234567",
                correo = "alfredo@ejemplo.mx",
                motivo = "Dolor de cabeza",
            ),
        )

        private fun franja(fecha: String, hora: String) = FranjaAgenda(
            idFranja = "doc_889900A|$fecha|$hora",
            idMedico = "doc_889900A",
            fecha = fecha,
            horaInicio = hora,
            horaFin = if (hora.endsWith("00")) hora.take(2) + ":30" else "${hora.take(2).toInt() + 1}:00",
        )
    }
}
