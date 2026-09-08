package com.eter.salud.presentation.home

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
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
    /** Toma cuyo registro esta viajando; la Vista desactiva sus botones. */
    val tomaEnCurso: String? = null,
    val errorRegistroToma: Boolean = false,
) {
    val tomasPendientes: Int
        get() = tomasDelDia.count { it.estado == EstadoToma.PENDIENTE }

    val todasLasTomasRegistradas: Boolean
        get() = tomasDelDia.isNotEmpty() && tomasPendientes == 0

    /** Orden fijo: el paciente aprende donde esta cada cosa y no se remueve. */
    val accesosRapidos: List<AccesoRapido> get() = AccesoRapido.entries
}
