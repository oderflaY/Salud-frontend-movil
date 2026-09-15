package com.eter.salud.data.sesion

/**
 * El backend rechazo una peticion porque la SESION ya no sirve, no por algo de
 * esa peticion en concreto.
 *
 * Pasa cuando el medico resetea la cuenta del paciente desde su panel web (se
 * cierran todas sus sesiones), cuando una cuenta se bloquea o se da de baja, y
 * cuando el paciente entro con una contrasena temporal y aun no elige otra.
 * Sin esto, cada pantalla mostraba "sin conexion" para siempre y la unica
 * salida era adivinar que habia que cerrar sesion.
 */
enum class TipoAvisoDeSesion {
    /** `SESION_REVOCADA` o `CUENTA_INACTIVA` (401): hay que volver a entrar. */
    CERRADA,

    /** `CAMBIO_CONTRASENA_REQUERIDO` (403): la sesion vale, pero falta elegir contrasena. */
    CAMBIO_DE_CONTRASENA_REQUERIDO,
}

/**
 * [token] es el que llevaba la peticion rechazada. Solo cuenta si sigue siendo
 * el de la sesion abierta: una peticion que salio con el token anterior y
 * vuelve rechazada despues de cambiar la contrasena no debe cerrar la sesion
 * nueva.
 */
data class AvisoDeSesion(val tipo: TipoAvisoDeSesion, val token: String)
