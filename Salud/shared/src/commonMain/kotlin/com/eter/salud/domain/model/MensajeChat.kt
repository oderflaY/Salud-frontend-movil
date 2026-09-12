package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/** Quien escribio el mensaje: gobierna de que lado de la pantalla se pinta la burbuja. */
@Serializable
enum class AutorMensaje {
    PACIENTE,
    MEDICO,
}

/**
 * [ORIENTACION_INICIAL] es el unico tipo que se pinta destacado (fondo propio,
 * sin forma de burbuja): es soporte vital inmediato de parte del medico, no
 * una linea mas de conversacion casual.
 */
@Serializable
enum class TipoMensaje {
    NORMAL,
    ORIENTACION_INICIAL,
}

/**
 * Un mensaje del chat entre paciente y medico.
 *
 * [texto] puede ir vacio cuando el mensaje es solo un [adjunto]: no se obliga a
 * escribir una frase para mandar una foto, igual que en cualquier chat comun.
 * Lo que nunca puede pasar es que los dos esten vacios a la vez; eso lo impide
 * la capa de presentacion, no el modelo.
 */
/**
 * No lleva `@Serializable` directo: [adjunto] apunta a una ruta LOCAL
 * ([Adjunto.rutaLocal]), y lo que de verdad viaja por la red es una URL de
 * descarga. `data/red/ChatRepositorioRemoto.kt` mapea a/desde un DTO de red
 * aparte en vez de forzar ese dato ajeno dentro del modelo de dominio.
 */
data class MensajeChat(
    val idMensaje: String,
    val autor: AutorMensaje,
    val texto: String,
    /** ISO 8601 UTC. */
    val instante: String,
    val tipo: TipoMensaje = TipoMensaje.NORMAL,
    val adjunto: Adjunto? = null,
)
