package com.eter.salud.presentation.agenda

import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.HorarioConsultorio
import com.eter.salud.domain.time.CalendarioSalud

/** Alcance temporal que el medico esta mirando. */
enum class VistaCalendario {
    DIARIA,
    SEMANAL,
    MENSUAL,
}

/** Rango de fechas visible, ambos extremos incluidos. */
data class RangoAgenda(val desde: String, val hasta: String) {
    fun contiene(fecha: String): Boolean = fecha in desde..hasta

    /** Las fechas del rango, en orden. Vacio si el rango no es coherente. */
    val fechas: List<String>
        get() {
            val dias = CalendarioSalud.diasEntre(desde, hasta) ?: return emptyList()
            if (dias < 0) return emptyList()
            return (0..dias).map { CalendarioSalud.sumarDias(desde, it) }
        }
}

/** Una hora de la jornada con lo que la ocupa, para dibujar la rejilla del dia. */
data class RenglonDelDia(
    val horaInicio: String,
    val horaFin: String,
    val cita: Cita?,
) {
    val libre: Boolean get() = cita == null
}

/** Un dia de la rejilla mensual, con el recuento que decide su punto de color. */
data class CeldaDelMes(
    val fecha: String,
    val citas: List<Cita>,
) {
    val vacio: Boolean get() = citas.isEmpty()
}

/**
 * Estado unico del calendario del medico. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 *
 * [citas] llega entero desde el repositorio y el recorte por rango se calcula
 * aqui: la agenda viva se recibe por un `Flow` unico y filtrar en la fuente
 * obligaria a volver a pedirla en cada cambio de vista.
 */
data class AgendaMedicoUiState(
    val idMedico: String = "",
    val vista: VistaCalendario = VistaCalendario.DIARIA,
    /** Dia de referencia; en vista semanal y mensual, el que fija el periodo. */
    val fechaAncla: String = "",
    val citas: List<Cita> = emptyList(),
    /** Cita abierta en la ventana de detalle, o nulo si no hay ninguna. */
    val citaSeleccionada: Cita? = null,
    /** Primera hora tocada al marcar un bloqueo; la segunda cierra el rango. */
    val inicioDeBloqueo: String? = null,
    /** Texto que el medico escribe para explicar el bloqueo en curso. */
    val notaDeBloqueo: String = "",
    /** Horarios libres ofrecidos al reprogramar la cita abierta. */
    val franjasParaReprogramar: List<FranjaAgenda> = emptyList(),
    val reprogramando: Boolean = false,
    val cargando: Boolean = true,
    val errorCarga: Boolean = false,
    /** Una accion sobre una cita (confirmar, cancelar, mover) no se pudo aplicar. */
    val errorAccion: Boolean = false,
) {

    val rango: RangoAgenda
        get() = when (vista) {
            VistaCalendario.DIARIA -> RangoAgenda(fechaAncla, fechaAncla)

            VistaCalendario.SEMANAL -> {
                // Semana de lunes a domingo, no "siete dias desde hoy": el medico
                // razona su agenda en semanas del calendario, y una ventana movil
                // haria que el mismo dia cayera en columnas distintas al avanzar.
                val desplazamiento = CalendarioSalud.diaDeLaSemana(fechaAncla) ?: 0
                val lunes = CalendarioSalud.restarDias(fechaAncla, desplazamiento)
                RangoAgenda(lunes, CalendarioSalud.sumarDias(lunes, DIAS_DE_LA_SEMANA - 1))
            }

            VistaCalendario.MENSUAL -> RangoAgenda(
                desde = CalendarioSalud.primerDiaDelMes(fechaAncla) ?: fechaAncla,
                hasta = CalendarioSalud.ultimoDiaDelMes(fechaAncla) ?: fechaAncla,
            )
        }

    /** Citas y bloqueos del periodo visible, en orden cronologico. */
    val citasVisibles: List<Cita>
        get() = citas.filter { rango.contiene(it.fecha) }.sortedBy { it.claveOrden }

    /**
     * Rejilla del dia ancla: una fila por hora de consulta, con la cita que la
     * ocupa o vacia si el hueco esta libre.
     *
     * Una cita que abarca varias horas (un bloqueo de comida, por ejemplo)
     * aparece en todas las filas que cubre: es lo que hace legible de un vistazo
     * cuanto tiempo se lleva.
     */
    val renglonesDelDia: List<RenglonDelDia>
        get() = HorarioConsultorio.horasDeInicio().map { hora ->
            val fin = HorarioConsultorio.finDe(hora)
            RenglonDelDia(
                horaInicio = hora,
                horaFin = fin,
                cita = citas.firstOrNull { cita ->
                    cita.fecha == fechaAncla &&
                        cita.estado.ocupaLaFranja &&
                        cita.horaInicio < fin &&
                        hora < cita.horaFin
                },
            )
        }

    /** Celdas del mes ancla, para la rejilla mensual. */
    val celdasDelMes: List<CeldaDelMes>
        get() = rango.fechas.map { fecha ->
            CeldaDelMes(fecha, citas.filter { it.fecha == fecha }.sortedBy { it.claveOrden })
        }

    /**
     * Columna (lunes = 0) en la que cae el dia 1 del mes. La rejilla mensual la
     * usa para dejar los huecos de delante y que cada dia caiga bajo su columna.
     */
    val desplazamientoDelMes: Int
        get() = CalendarioSalud.diaDeLaSemana(rango.desde) ?: 0

    /** Recuento por estado del periodo visible, para la leyenda de color. */
    val resumenPorEstado: Map<EstadoCita, Int>
        get() = citasVisibles.groupingBy { it.estado }.eachCount()

    val hayBloqueoEnCurso: Boolean get() = inicioDeBloqueo != null

    private companion object {
        const val DIAS_DE_LA_SEMANA = 7
    }
}
