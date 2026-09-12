package com.eter.salud.presentation.citas

/**
 * Claves de error de la captura de datos de la cita. El ViewModel emite claves
 * y la Vista las traduce contra `strings.xml` (DM_Arquitectura_App.md, seccion 5).
 */
enum class ErrorCampoCita {
    NOMBRE_VACIO,
    TELEFONO_VACIO,
    TELEFONO_FORMATO,
    CORREO_VACIO,
    CORREO_FORMATO,
    MOTIVO_VACIO,
}

/**
 * Fallo del flujo de agendamiento, ya interpretado.
 *
 * [FRANJA_OCUPADA] y [RESERVA_EXPIRADA] son estados distintos y no "un error"
 * generico porque la salida del paciente cambia: ante una franja ocupada hay que
 * devolverlo a la lista de horarios, y ante una retencion caducada hay que
 * explicarle por que perdio la que ya habia elegido.
 */
enum class ErrorAgendaCita {
    SIN_CONEXION,
    FRANJA_OCUPADA,
    RESERVA_EXPIRADA,
    SIN_HORARIOS,
}
