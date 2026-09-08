package com.eter.salud.presentation.registro

import com.eter.salud.presentation.login.ValidadorLogin

/**
 * Reglas del alta de cuenta. Objeto puro, sin dependencias de UI ni de red.
 *
 * Comparte con el acceso la forma del correo y el minimo de caracteres: si las
 * dos pantallas divergieran, seria posible crear una cuenta con la que despues
 * no se pudiera entrar.
 */
object ValidadorRegistro {

    /** Lista vacia significa "se puede crear la cuenta". */
    fun validar(
        correo: String,
        contrasena: String,
        confirmacion: String,
        avisoAceptado: Boolean,
    ): List<ErrorCampoRegistro> = buildList {
        val correoLimpio = ValidadorLogin.normalizarCorreo(correo)
        when {
            correoLimpio.isBlank() -> add(ErrorCampoRegistro.CORREO_VACIO)
            !ValidadorLogin.esCorreoValido(correoLimpio) ->
                add(ErrorCampoRegistro.CORREO_FORMATO)
        }

        val errorContrasena = errorDeContrasena(contrasena)
        if (errorContrasena != null) {
            add(errorContrasena)
        } else if (confirmacion != contrasena) {
            // La confirmacion solo se compara con una contrasena ya valida:
            // senalar las dos cosas a la vez confunde mas de lo que ayuda.
            add(ErrorCampoRegistro.CONFIRMACION_NO_COINCIDE)
        }

        if (!avisoAceptado) add(ErrorCampoRegistro.AVISO_NO_ACEPTADO)
    }

    /**
     * Politica de contrasena: longitud minima compartida con el acceso y mezcla
     * de letras y numeros, que es lo que exige el backend al dar de alta.
     */
    private fun errorDeContrasena(contrasena: String): ErrorCampoRegistro? = when {
        contrasena.isEmpty() -> ErrorCampoRegistro.CONTRASENA_VACIA
        contrasena.length < ValidadorLogin.MINIMO_CARACTERES_CONTRASENA ->
            ErrorCampoRegistro.CONTRASENA_CORTA

        !contrasena.any { it.isLetter() } || !contrasena.any { it.isDigit() } ->
            ErrorCampoRegistro.CONTRASENA_DEBIL

        else -> null
    }
}
