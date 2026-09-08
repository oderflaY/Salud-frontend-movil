package com.eter.salud.domain.model

/**
 * Sesion abierta tras el acceso de un profesional de la salud (medico o
 * paramedico). Espejo minimo de DM_PerfilMedico.md: solo lo que la Home y la
 * cabecera necesitan pintar, no el expediente completo del profesional.
 *
 * Vive separada de [SesionPaciente] a proposito: son credenciales de dos
 * portales distintos y nunca coexisten en la misma pantalla.
 */
data class SesionProfesional(
    val idMedico: String,
    val token: String,
    val nombre: String,
    val apellidos: String,
    /** Como se dirige la app al profesional: "Dr.", "Dra." o "Dr(a).". Lo elige el propio profesional al registrarse, nunca se infiere. */
    val tratamiento: String,
    val estadoVerificacion: EstadoVerificacionCedula,
) {
    val nombreCompleto: String get() = "$nombre $apellidos"
}

/**
 * Estado de la Cedula Profesional (DM_PerfilMedico.md, seccion 2: Validacion
 * Legal). Un registro nuevo arranca en [PENDIENTE]; el backend lo sube a
 * [APROBADO] cuando el sistema o un administrador valida el numero.
 */
enum class EstadoVerificacionCedula {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
}

/** Razones por las que el backend puede rechazar el acceso de un profesional. */
enum class MotivoFalloAutenticacionProfesional {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    SIN_CONEXION,
}

/** Razones por las que el backend puede rechazar el alta de un profesional. */
enum class MotivoFalloRegistroProfesional {
    CORREO_YA_REGISTRADO,
    /** La cedula profesional debe ser unica: no puede haber dos cuentas con la misma. */
    CEDULA_YA_REGISTRADA,
    SIN_CONEXION,
}
