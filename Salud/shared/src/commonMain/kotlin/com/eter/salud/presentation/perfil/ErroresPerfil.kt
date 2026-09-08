package com.eter.salud.presentation.perfil

/**
 * Claves de error de la Fase 2. Igual que en la Fase 1, el ViewModel emite
 * claves y la Vista las traduce contra `strings.xml`
 * (DM_Arquitectura_App.md, seccion 5: cero hardcoding).
 */
enum class ErrorCampoPerfil {
    CURP_VACIA,
    CURP_FORMATO,
    NSS_VACIO,
    NSS_FORMATO,
    DONADOR_SIN_RESPUESTA,
    PESO_INVALIDO,
    ALTURA_INVALIDA,
    PRESION_INVALIDA,
    CIRUGIAS_SIN_CONFIRMAR,
    CIRUGIA_PROCEDIMIENTO_VACIO,
    CIRUGIA_FECHA_INVALIDA,
    ANTECEDENTES_SIN_CONFIRMAR,
    ANTECEDENTE_VACIO,
}

/** Motivo por el que no se pudo leer o guardar el expediente. */
enum class ErrorGuardado {
    SIN_CONEXION,

    /** Se intento guardar antes de tener el expediente en pantalla. */
    PERFIL_NO_CARGADO,
}
