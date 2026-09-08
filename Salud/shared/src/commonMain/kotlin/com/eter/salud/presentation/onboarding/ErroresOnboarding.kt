package com.eter.salud.presentation.onboarding

/**
 * Claves de error de la capa de presentacion.
 *
 * El ViewModel jamas produce texto: emite estas claves y es la Vista quien las
 * traduce contra `strings.xml` (DM_Arquitectura_App.md, seccion 5: cero
 * hardcoding). Asi las pruebas verifican reglas, no cadenas traducibles.
 */
enum class ErrorCampoOnboarding {
    NOMBRE_VACIO,
    APELLIDOS_VACIO,
    NACIMIENTO_VACIO,
    NACIMIENTO_FORMATO,
    NACIMIENTO_FUTURA,
    GENERO_VACIO,
    TELEFONO_VACIO,
    TELEFONO_FORMATO,
    SANGRE_VACIO,
    ALERGIAS_SIN_CONFIRMAR,
    ALERGIA_ALERGENO_VACIO,
    ALERGIA_SEVERIDAD_VACIA,
    ALERGIA_REACCION_VACIA,
    CONDICIONES_SIN_CONFIRMAR,
    CONDICION_VACIA,
    RESCATE_SIN_CONFIRMAR,
    RESCATE_VACIO,
    TRATAMIENTOS_SIN_CONFIRMAR,
    TRATAMIENTO_MEDICAMENTO_VACIO,
    TRATAMIENTO_DOSIS_VACIA,
    TRATAMIENTO_FRECUENCIA_VACIA,
    TRATAMIENTO_FRECUENCIA_INVALIDA,
    TRATAMIENTO_INVENTARIO_INVALIDO,
    CONTACTOS_VACIO,
    CONTACTO_NOMBRE_VACIO,
    CONTACTO_RELACION_VACIA,
    CONTACTO_TELEFONO_VACIO,
    CONTACTO_TELEFONO_FORMATO,
}

/**
 * Datos que viajan en la tarjeta RFID y que un paramedico necesita leer sin
 * red. Si alguno falta, la interfaz debe alertar antes de cerrar el registro.
 */
enum class CampoCriticoSupervivencia {
    TIPO_SANGRE,
    ALERGIAS,
    CONDICIONES_CRITICAS,
    MEDICACION_RESCATE,
    CONTACTOS_EMERGENCIA,
}

/** Motivo por el que fallo el envio del perfil. */
enum class ErrorEnvio {
    SIN_CONEXION,
}
