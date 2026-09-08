package com.eter.salud.presentation.profesional

/**
 * Claves de error del acceso de personal medico. El ViewModel emite claves y
 * la Vista las traduce contra `strings.xml` (DM_Arquitectura_App.md,
 * seccion 5: cero hardcoding).
 */
enum class ErrorCampoLoginProfesional {
    CORREO_VACIO,
    CORREO_FORMATO,
    CONTRASENA_VACIA,
    CONTRASENA_CORTA,
}

/** Resultado negativo devuelto por el backend al iniciar sesion. */
enum class ErrorAutenticacionProfesional {
    CREDENCIALES_INVALIDAS,
    CUENTA_BLOQUEADA,
    SIN_CONEXION,
}

/** Claves de error del alta de personal medico. */
enum class ErrorCampoRegistroProfesional {
    CORREO_VACIO,
    CORREO_FORMATO,
    CONTRASENA_VACIA,
    CONTRASENA_CORTA,
    CONTRASENA_DEBIL,
    CONFIRMACION_NO_COINCIDE,
    NOMBRE_VACIO,
    APELLIDOS_VACIO,
    TRATAMIENTO_VACIO,
    CEDULA_VACIA,
    CEDULA_FORMATO,
    AVISO_NO_ACEPTADO,
}

/** Resultado negativo devuelto por el backend al dar de alta la cuenta. */
enum class ErrorRegistroProfesional {
    CORREO_YA_REGISTRADO,
    CEDULA_YA_REGISTRADA,
    SIN_CONEXION,
}
