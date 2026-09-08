package com.eter.salud.domain.model

/** Quien escribio el mensaje: gobierna de que lado de la pantalla se pinta la burbuja. */
enum class AutorMensaje {
    PACIENTE,
    MEDICO,
}

/**
 * [ORIENTACION_INICIAL] es el unico tipo que se pinta destacado (fondo propio,
 * sin forma de burbuja): es soporte vital inmediato de parte del medico, no
 * una linea mas de conversacion casual.
 */
enum class TipoMensaje {
    NORMAL,
    ORIENTACION_INICIAL,
}

/** Un mensaje del chat entre paciente y medico. */
data class MensajeChat(
    val idMensaje: String,
    val autor: AutorMensaje,
    val texto: String,
    /** ISO 8601 UTC. */
    val instante: String,
    val tipo: TipoMensaje = TipoMensaje.NORMAL,
)
