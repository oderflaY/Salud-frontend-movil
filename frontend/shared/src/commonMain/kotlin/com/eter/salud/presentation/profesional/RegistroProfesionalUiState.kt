package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.SesionProfesional

/**
 * Estado unico de la pantalla de alta de personal medico. La Vista solo pinta
 * esto (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class RegistroProfesionalUiState(
    val correo: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    /** "Dr.", "Dra." o "Dr(a).": lo elige el profesional, nunca se infiere. */
    val tratamiento: String = "",
    val cedulaProfesional: String = "",
    val avisoAceptado: Boolean = false,
    val contrasenaVisible: Boolean = false,
    val erroresCampo: List<ErrorCampoRegistroProfesional> = emptyList(),
    val errorRegistro: ErrorRegistroProfesional? = null,
    val creandoCuenta: Boolean = false,
    /** Sesion abierta con la cuenta recien creada. */
    val sesion: SesionProfesional? = null,
) {
    val puedeEnviar: Boolean
        get() = correo.isNotBlank() &&
            contrasena.isNotEmpty() &&
            confirmacion.isNotEmpty() &&
            nombre.isNotBlank() &&
            apellidos.isNotBlank() &&
            tratamiento.isNotBlank() &&
            cedulaProfesional.isNotBlank() &&
            avisoAceptado &&
            !creandoCuenta &&
            sesion == null
}
