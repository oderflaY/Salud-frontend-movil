package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Sesion abierta tras un acceso correcto.
 *
 * El token JWT es el que exige DM_HistorialMedico.md (seccion 2) para bajar los
 * nodos protegidos del expediente. Se mantiene fuera del DTO del paciente: es
 * una credencial, no un dato clinico.
 */
@Serializable
data class SesionPaciente(
    val idPaciente: String,
    val token: String,
    /** Cuenta creada pero sin perfil de emergencia: hay que pasar por la Fase 1. */
    val requiereOnboarding: Boolean,
    /**
     * Entro con la contrasena temporal que le dio su medico al resetear la
     * cuenta desde su panel web. Antes que nada, tiene que elegir una nueva:
     * el backend no le deja hacer otra cosa hasta entonces.
     */
    val requiereCambioContrasena: Boolean = false,
)

/** Razones por las que el backend puede rechazar un acceso. */
enum class MotivoFalloAutenticacion {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    /** La contrasena temporal del medico paso de su plazo: hay que pedirle otra. */
    CONTRASENA_TEMPORAL_VENCIDA,
    SIN_CONEXION,
}

/** Razones por las que el backend puede rechazar la creacion de una cuenta. */
enum class MotivoFalloRegistro {
    CORREO_YA_REGISTRADO,
    SIN_CONEXION,
}
