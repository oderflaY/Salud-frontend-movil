package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Ficha de un doctor tal como la ve un paciente buscando en el Directorio
 * Medico. Es distinta de [SesionProfesional] (la sesion del propio doctor) y
 * de [PacienteVinculado] (la cartera del doctor): esta es la vista publica y
 * ligera que un paciente usa para elegir con quien vincularse.
 */
@Serializable
data class PerfilDoctorDirectorio(
    val idMedico: String,
    /** Ya con tratamiento incluido, por ejemplo "Dra. Elena Ruiz Santos". */
    val nombreCompleto: String,
    val especialidad: Especialidad,
    /** Espejo de `credenciales.estadoVerificacion == "aprobado"` (DM_PerfilMedico.md). */
    val cedulaVerificada: Boolean,
    /** DM_PerfilMedico.md, nodo `credenciales.universidad`. Dato de confianza, no traducible. */
    val universidad: String = "",
    /** Texto libre del backend, por ejemplo "Inmediata para chat" o "Disponible manana". */
    val disponibilidad: String = "",
)

/**
 * Doctor ya vinculado al paciente. Trae el identificador de la conversacion
 * porque vincularse es lo que abre el canal de chat.
 */
@Serializable
data class MedicoVinculado(
    val idMedico: String,
    val nombreCompleto: String,
    val especialidad: Especialidad,
    val idConversacion: String,
)
