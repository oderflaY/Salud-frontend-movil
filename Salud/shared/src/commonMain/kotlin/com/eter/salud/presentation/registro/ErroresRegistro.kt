package com.eter.salud.presentation.registro

/**
 * Claves de error del alta de cuenta. El ViewModel emite claves y la Vista las
 * traduce contra `strings.xml` (DM_Arquitectura_App.md, seccion 5).
 */
enum class ErrorCampoRegistro {
    CORREO_VACIO,
    CORREO_FORMATO,
    CONTRASENA_VACIA,
    CONTRASENA_CORTA,
    CONTRASENA_DEBIL,
    CONFIRMACION_NO_COINCIDE,
    AVISO_NO_ACEPTADO,
}

/** Resultado negativo devuelto por el backend, ya interpretado. */
enum class ErrorRegistro {
    CORREO_YA_REGISTRADO,
    SIN_CONEXION,
}
