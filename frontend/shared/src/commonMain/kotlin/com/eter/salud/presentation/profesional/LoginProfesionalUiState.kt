package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.SesionProfesional

/**
 * Estado unico de la pantalla de acceso de personal medico. La Vista solo
 * pinta esto (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class LoginProfesionalUiState(
    val correo: String = "",
    val contrasena: String = "",
    val contrasenaVisible: Boolean = false,
    val erroresCampo: List<ErrorCampoLoginProfesional> = emptyList(),
    val errorAutenticacion: ErrorAutenticacionProfesional? = null,
    val autenticando: Boolean = false,
    /** Sesion abierta; mientras sea nula la pantalla sigue en primer plano. */
    val sesion: SesionProfesional? = null,
) {
    val puedeEnviar: Boolean
        get() = correo.isNotBlank() && contrasena.isNotEmpty() && !autenticando && sesion == null
}
