package com.eter.salud.presentation.profesional

import com.eter.salud.presentation.login.ValidadorLogin

/**
 * Reglas del acceso de personal medico. Objeto puro, sin dependencias de UI ni
 * de red, para poder probarlo aisladamente.
 *
 * Reutiliza [ValidadorLogin] para la forma del correo y el minimo de la
 * contrasena: son reglas genericas de formulario, no propias de un portal.
 */
object ValidadorLoginProfesional {

    /** Lista vacia significa "se puede intentar el acceso". */
    fun validar(correo: String, contrasena: String): List<ErrorCampoLoginProfesional> =
        buildList {
            val correoLimpio = ValidadorLogin.normalizarCorreo(correo)
            when {
                correoLimpio.isBlank() -> add(ErrorCampoLoginProfesional.CORREO_VACIO)
                !ValidadorLogin.esCorreoValido(correoLimpio) ->
                    add(ErrorCampoLoginProfesional.CORREO_FORMATO)
            }
            when {
                contrasena.isEmpty() -> add(ErrorCampoLoginProfesional.CONTRASENA_VACIA)
                contrasena.length < ValidadorLogin.MINIMO_CARACTERES_CONTRASENA ->
                    add(ErrorCampoLoginProfesional.CONTRASENA_CORTA)
            }
        }
}

/** Reglas del alta de personal medico. */
object ValidadorRegistroProfesional {

    /** Cedula profesional mexicana: solo digitos, entre seis y ocho caracteres. */
    private val LARGO_CEDULA = 6..8

    /** Lista vacia significa "se puede crear la cuenta". */
    fun validar(
        correo: String,
        contrasena: String,
        confirmacion: String,
        nombre: String,
        apellidos: String,
        tratamiento: String,
        cedulaProfesional: String,
        avisoAceptado: Boolean,
    ): List<ErrorCampoRegistroProfesional> = buildList {
        val correoLimpio = ValidadorLogin.normalizarCorreo(correo)
        when {
            correoLimpio.isBlank() -> add(ErrorCampoRegistroProfesional.CORREO_VACIO)
            !ValidadorLogin.esCorreoValido(correoLimpio) ->
                add(ErrorCampoRegistroProfesional.CORREO_FORMATO)
        }

        val errorContrasena = errorDeContrasena(contrasena)
        if (errorContrasena != null) {
            add(errorContrasena)
        } else if (confirmacion != contrasena) {
            add(ErrorCampoRegistroProfesional.CONFIRMACION_NO_COINCIDE)
        }

        if (nombre.isBlank()) add(ErrorCampoRegistroProfesional.NOMBRE_VACIO)
        if (apellidos.isBlank()) add(ErrorCampoRegistroProfesional.APELLIDOS_VACIO)
        if (tratamiento.isBlank()) add(ErrorCampoRegistroProfesional.TRATAMIENTO_VACIO)

        val cedulaLimpia = cedulaProfesional.trim()
        when {
            cedulaLimpia.isBlank() -> add(ErrorCampoRegistroProfesional.CEDULA_VACIA)
            cedulaLimpia.length !in LARGO_CEDULA || !cedulaLimpia.all { it.isDigit() } ->
                add(ErrorCampoRegistroProfesional.CEDULA_FORMATO)
        }

        if (!avisoAceptado) add(ErrorCampoRegistroProfesional.AVISO_NO_ACEPTADO)
    }

    private fun errorDeContrasena(contrasena: String): ErrorCampoRegistroProfesional? = when {
        contrasena.isEmpty() -> ErrorCampoRegistroProfesional.CONTRASENA_VACIA
        contrasena.length < ValidadorLogin.MINIMO_CARACTERES_CONTRASENA ->
            ErrorCampoRegistroProfesional.CONTRASENA_CORTA

        !contrasena.any { it.isLetter() } || !contrasena.any { it.isDigit() } ->
            ErrorCampoRegistroProfesional.CONTRASENA_DEBIL

        else -> null
    }
}
