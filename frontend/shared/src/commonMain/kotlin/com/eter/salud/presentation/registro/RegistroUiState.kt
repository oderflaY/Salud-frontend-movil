package com.eter.salud.presentation.registro

import com.eter.salud.domain.model.SesionPaciente

/**
 * Estado unico de la pantalla de alta de cuenta. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class RegistroUiState(
    val correo: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val avisoAceptado: Boolean = false,
    val contrasenaVisible: Boolean = false,
    val erroresCampo: List<ErrorCampoRegistro> = emptyList(),
    val errorRegistro: ErrorRegistro? = null,
    val creandoCuenta: Boolean = false,
    /** Sesion abierta con la cuenta recien creada. */
    val sesion: SesionPaciente? = null,
) {
    /**
     * El aviso de privacidad si bloquea el boton: es un consentimiento, no un
     * campo que se pueda corregir despues de intentarlo.
     */
    val puedeEnviar: Boolean
        get() = correo.isNotBlank() &&
            contrasena.isNotEmpty() &&
            confirmacion.isNotEmpty() &&
            avisoAceptado &&
            !creandoCuenta &&
            sesion == null
}
