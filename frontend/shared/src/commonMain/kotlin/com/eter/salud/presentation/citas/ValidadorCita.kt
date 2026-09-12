package com.eter.salud.presentation.citas

import com.eter.salud.presentation.login.ValidadorLogin

/**
 * Reglas de los datos que el paciente captura para su cita. Objeto puro, sin
 * dependencias de UI ni de red.
 *
 * Comparte la forma del correo con el acceso y el alta de cuenta: si divergieran,
 * seria posible agendar con un correo al que despues el comprobante no llegaria.
 */
object ValidadorCita {

    /** Numero nacional a diez digitos, sin lada internacional. */
    const val DIGITOS_TELEFONO = 10

    /** Lista vacia significa "se puede pasar al resumen". */
    fun validar(
        nombreCompleto: String,
        telefono: String,
        correo: String,
        motivo: String,
    ): List<ErrorCampoCita> = buildList {
        if (nombreCompleto.isBlank()) add(ErrorCampoCita.NOMBRE_VACIO)

        val digitos = soloDigitos(telefono)
        when {
            digitos.isEmpty() -> add(ErrorCampoCita.TELEFONO_VACIO)
            digitos.length != DIGITOS_TELEFONO -> add(ErrorCampoCita.TELEFONO_FORMATO)
        }

        val correoLimpio = ValidadorLogin.normalizarCorreo(correo)
        when {
            correoLimpio.isBlank() -> add(ErrorCampoCita.CORREO_VACIO)
            !ValidadorLogin.esCorreoValido(correoLimpio) -> add(ErrorCampoCita.CORREO_FORMATO)
        }

        // El motivo no es un tramite: es lo primero que el medico lee en la
        // tarjeta de la cita para saber a que se enfrenta antes de recibir al
        // paciente. Una cita sin motivo llega a su agenda como una incognita.
        if (motivo.isBlank()) add(ErrorCampoCita.MOTIVO_VACIO)
    }

    /**
     * Se queda solo con los digitos para que espacios, guiones y parentesis
     * ("618 123 45 67") no hagan fallar un telefono que si es valido.
     */
    fun soloDigitos(telefono: String): String = telefono.filter { it.isDigit() }
}
