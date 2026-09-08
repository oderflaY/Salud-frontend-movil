package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.PacienteVinculado

/**
 * Estado unico del panel principal del profesional de la salud. La Vista solo
 * pinta esto (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class HomeProfesionalUiState(
    val idMedico: String,
    /** "Dr.", "Dra." o "Dr(a).": se guarda por separado de los apellidos porque la cabecera saluda con "Dr. / Dra. [Apellido]", no con el nombre completo. */
    val tratamiento: String = "",
    val apellidos: String = "",
    val estadoVerificacion: EstadoVerificacionCedula = EstadoVerificacionCedula.PENDIENTE,
    val pacientesVinculados: List<PacienteVinculado> = emptyList(),
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
) {
    /** Indicador sutil de la cabecera: solo se enciende cuando el sistema ya validó la cedula. */
    val cedulaVerificada: Boolean
        get() = estadoVerificacion == EstadoVerificacionCedula.APROBADO
}
