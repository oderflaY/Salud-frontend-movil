package com.eter.salud.presentation.profesional

import com.eter.salud.domain.diario.SeveridadDiario
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
    /**
     * Severidad de la ULTIMA entrada del diario de cada paciente.
     *
     * Un mapa por identificador y no un campo dentro de [PacienteVinculado]
     * porque son dos cosas de vidas distintas: la cartera la sirve el backend y
     * cambia cuando alguien se vincula, mientras que el diario lo escribe el
     * paciente y cambia a cualquier hora. Meterlo en el modelo de la cartera
     * obligaria a recargarla entera cada vez que un paciente anota un sintoma.
     *
     * Un paciente ausente del mapa no ha escrito nada: no es lo mismo que estar
     * en verde, y la Vista los distingue.
     */
    val severidadDelDiario: Map<String, SeveridadDiario> = emptyMap(),
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
) {
    /** Indicador sutil de la cabecera: solo se enciende cuando el sistema ya validó la cedula. */
    val cedulaVerificada: Boolean
        get() = estadoVerificacion == EstadoVerificacionCedula.APROBADO
}
