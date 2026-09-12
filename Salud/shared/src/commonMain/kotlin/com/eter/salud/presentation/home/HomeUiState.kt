package com.eter.salud.presentation.home

import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia

/**
 * Destinos del grid de accesos rapidos. Es un enum y no una lista de textos
 * porque la Vista es quien traduce cada acceso contra `strings.xml`.
 */
enum class AccesoRapido {
    TARJETA_RFID,
    DIARIO_SINTOMAS,
    MIS_MEDICOS,
    HISTORIAL,
}

/**
 * Estado unico del panel principal. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class HomeUiState(
    val idPaciente: String,
    val nombrePaciente: String = "",
    val tarjetaRfidActiva: Boolean = false,
    val perfilEmergenciaPendiente: Boolean = false,
    val tomasDelDia: List<TomaDelDia> = emptyList(),
    val resumenSemanal: ResumenAdherencia? = null,
    /** Cumplimiento dia a dia de la semana, para la franja horizontal. */
    val semana: List<DiaDeAdherencia> = emptyList(),
    /** Dia que la franja resalta. Por defecto, hoy. */
    val fechaSeleccionada: String = "",
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
    /** Toma cuyo registro esta viajando; la Vista desactiva sus botones. */
    val tomaEnCurso: String? = null,
    val errorRegistroToma: Boolean = false,
) {
    val tomasPendientes: Int
        get() = tomasDelDia.count { it.estado == EstadoToma.PENDIENTE }

    /**
     * Adherencia de HOY en porcentaje entero.
     *
     * Es distinta del resumen semanal a proposito: el anillo del panel responde
     * "como voy hoy", que es la unica pregunta sobre la que el paciente todavia
     * puede actuar. La semana ya no se puede cambiar.
     *
     * Una omision cuenta como registrada pero NO como cumplida: el paciente
     * hizo su parte informando, pero la pastilla no se tomo, y un anillo que
     * subiera al omitir premiaria justo lo contrario de lo que persigue.
     */
    val adherenciaDeHoy: Int
        get() {
            if (tomasDelDia.isEmpty()) return 0
            val cumplidas = tomasDelDia.count {
                it.estado == EstadoToma.TOMADO || it.estado == EstadoToma.TOMADO_TARDE
            }
            return (cumplidas * 200 + tomasDelDia.size) / (tomasDelDia.size * 2)
        }

    /** Tomas todavia pendientes, que son las que merecen un recordatorio. */
    val tomasPorRecordar: List<TomaDelDia>
        get() = tomasDelDia.filter { it.estado == EstadoToma.PENDIENTE }

    val todasLasTomasRegistradas: Boolean
        get() = tomasDelDia.isNotEmpty() && tomasPendientes == 0

    /** Orden fijo: el paciente aprende donde esta cada cosa y no se remueve. */
    val accesosRapidos: List<AccesoRapido> get() = AccesoRapido.entries
}
