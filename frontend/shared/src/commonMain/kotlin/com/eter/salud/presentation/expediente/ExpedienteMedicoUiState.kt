package com.eter.salud.presentation.expediente

import com.eter.salud.domain.model.BloqueDeExpediente
import com.eter.salud.domain.model.DocumentoClinico

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
    /** El expediente YA filtrado por lo que el paciente decidio compartir. */
    val paciente: PacienteDto? = null,
    val cargando: Boolean = true,
    val errorCarga: Boolean = false,
    /** Secciones que el paciente oculto: se dice asi, no se pintan vacias. */
    val bloquesOcultos: Set<BloqueDeExpediente> = emptySet(),
    /** Estudios que el paciente adjunto y dejo ver. */
    val documentos: List<DocumentoClinico> = emptyList(),
)
