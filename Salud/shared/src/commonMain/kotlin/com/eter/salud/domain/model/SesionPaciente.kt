package com.eter.salud.domain.model

/**
 * Sesion abierta tras un acceso correcto.
 *
 * El token JWT es el que exige DM_HistorialMedico.md (seccion 2) para bajar los
 * nodos protegidos del expediente. Se mantiene fuera del DTO del paciente: es
 * una credencial, no un dato clinico.
 */
data class SesionPaciente(
    val idPaciente: String,
    val token: String,
    /** Cuenta creada pero sin perfil de emergencia: hay que pasar por la Fase 1. */
    val requiereOnboarding: Boolean,
)

/** Razones por las que el backend puede rechazar un acceso. */
enum class MotivoFalloAutenticacion {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    SIN_CONEXION,
}

/** Razones por las que el backend puede rechazar la creacion de una cuenta. */
enum class MotivoFalloRegistro {
    CORREO_YA_REGISTRADO,
    SIN_CONEXION,
}
