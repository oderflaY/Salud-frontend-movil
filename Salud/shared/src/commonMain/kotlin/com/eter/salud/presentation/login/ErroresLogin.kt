package com.eter.salud.presentation.login

/**
 * Claves de error del acceso. El ViewModel emite claves y la Vista las traduce
 * contra `strings.xml` (DM_Arquitectura_App.md, seccion 5: cero hardcoding).
 */
enum class ErrorCampoLogin {
    CORREO_VACIO,
    CORREO_FORMATO,
    CONTRASENA_VACIA,
    CONTRASENA_CORTA,
}

/** Resultado negativo devuelto por el backend, ya interpretado. */
enum class ErrorAutenticacion {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    SIN_CONEXION,
}
