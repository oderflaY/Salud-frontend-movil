package com.eter.salud.presentation.login

import com.eter.salud.domain.model.SesionPaciente

/**
 * Estado unico de la pantalla de acceso. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class LoginUiState(
    val correo: String = "",
    val contrasena: String = "",
    val contrasenaVisible: Boolean = false,
    val erroresCampo: List<ErrorCampoLogin> = emptyList(),
    val errorAutenticacion: ErrorAutenticacion? = null,
    val autenticando: Boolean = false,
    /** Sesion abierta; mientras sea nula la pantalla sigue en primer plano. */
    val sesion: SesionPaciente? = null,
) {
    /**
     * Habilita la accion principal. Solo mira que haya algo escrito: el detalle
     * del formato se le dice al paciente al pulsar, no bloqueandole el boton.
     */
    val puedeEnviar: Boolean
        get() = correo.isNotBlank() && contrasena.isNotEmpty() && !autenticando && sesion == null
}
