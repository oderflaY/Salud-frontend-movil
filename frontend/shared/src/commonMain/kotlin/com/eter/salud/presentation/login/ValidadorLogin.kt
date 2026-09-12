package com.eter.salud.presentation.login

/**
 * Reglas del formulario de acceso. Objeto puro, sin dependencias de UI ni de
 * red, para poder probarlo aisladamente.
 */
object ValidadorLogin {

    /** Minimo alineado con la politica de contrasenas del backend. */
    const val MINIMO_CARACTERES_CONTRASENA: Int = 8

    private val FORMATO_CORREO =
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /** Lista vacia significa "se puede intentar el acceso". */
    fun validar(correo: String, contrasena: String): List<ErrorCampoLogin> = buildList {
        val correoLimpio = normalizarCorreo(correo)
        when {
            correoLimpio.isBlank() -> add(ErrorCampoLogin.CORREO_VACIO)
            !esCorreoValido(correoLimpio) -> add(ErrorCampoLogin.CORREO_FORMATO)
        }
        when {
            contrasena.isEmpty() -> add(ErrorCampoLogin.CONTRASENA_VACIA)
            contrasena.length < MINIMO_CARACTERES_CONTRASENA ->
                add(ErrorCampoLogin.CONTRASENA_CORTA)
        }
    }

    /**
     * El correo se recorta y se pasa a minusculas: los teclados moviles ponen
     * mayuscula inicial y el backend distingue mayusculas al buscar la cuenta.
     *
     * La contrasena, en cambio, jamas se toca: un espacio es un caracter valido.
     */
    fun normalizarCorreo(correo: String): String = correo.trim().lowercase()

    /**
     * Regla unica de forma del correo. La comparte el alta de cuenta para que
     * un correo aceptado al registrarse no sea rechazado al iniciar sesion.
     */
    fun esCorreoValido(correo: String): Boolean =
        FORMATO_CORREO.matches(normalizarCorreo(correo))
}
