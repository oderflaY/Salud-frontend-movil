package com.eter.salud.presentation.expediente

import com.eter.salud.domain.model.PacienteDto

/**
 * Estado unico de la vista de solo lectura del expediente, desde el portal del
 * medico.
 *
 * Es deliberadamente mas simple que [com.eter.salud.presentation.perfil.PerfilUiState]:
 * aquel edita un borrador seccion por seccion porque el paciente corrige su
 * propio historial; esta pantalla solo lo muestra, asi que no necesita
 * secciones abiertas, validaciones ni guardado -- el DTO completo tal cual llega
 * del backend es el unico dato que hace falta.
 */
data class ExpedienteMedicoUiState(
    val paciente: PacienteDto? = null,
    val cargando: Boolean = true,
    val errorCarga: Boolean = false,
)
