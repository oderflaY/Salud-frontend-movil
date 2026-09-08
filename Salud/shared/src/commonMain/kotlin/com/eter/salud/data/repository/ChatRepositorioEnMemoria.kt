package com.eter.salud.data.repository

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio

/**
 * Chat simulado para desarrollo y pruebas de interfaz.
 *
 * La primera vez que se abre una conversacion ya trae el bloque de Orientacion
 * Inicial de parte del medico: en un backend real ese mensaje lo escribe el
 * sistema en cuanto se crea la vinculacion, antes de que el paciente entre al
 * chat, asi que aqui se siembra en el primer `obtenerHistorial` en lugar de
 * exigir un paso de configuracion aparte.
 *
 * Se sustituira por el cliente HTTP (o un socket) contra el backend en Go sin
 * tocar el ViewModel, que solo depende de [ChatRepositorio].
 */
class ChatRepositorioEnMemoria : ChatRepositorio {

    private val conversaciones = mutableMapOf<String, MutableList<MensajeChat>>()
    private var siguienteId = 1

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> {
        val historial = conversaciones.getOrPut(idConversacion) {
            mutableListOf(mensajeOrientacionInicial())
        }
        return Result.success(historial.toList())
    }

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
    ): Result<MensajeChat> {
        val mensaje = MensajeChat(
            idMensaje = "$PREFIJO_MENSAJE${siguienteId++}",
            autor = autor,
            texto = texto,
            instante = instante,
        )
        conversaciones.getOrPut(idConversacion) { mutableListOf(mensajeOrientacionInicial()) } += mensaje
        return Result.success(mensaje)
    }

    override suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat> {
        val respuesta = MensajeChat(
            idMensaje = "$PREFIJO_MENSAJE${siguienteId++}",
            autor = AutorMensaje.MEDICO,
            texto = TEXTO_RESPUESTA_AUTOMATICA,
            instante = instante,
        )
        conversaciones.getOrPut(idConversacion) { mutableListOf(mensajeOrientacionInicial()) } += respuesta
        return Result.success(respuesta)
    }

    private fun mensajeOrientacionInicial() = MensajeChat(
        idMensaje = "$PREFIJO_MENSAJE${siguienteId++}",
        autor = AutorMensaje.MEDICO,
        texto = TEXTO_ORIENTACION_INICIAL,
        instante = INSTANTE_SEMILLA,
        tipo = TipoMensaje.ORIENTACION_INICIAL,
    )

    private companion object {
        const val PREFIJO_MENSAJE = "msg_local_"
        const val INSTANTE_SEMILLA = "2026-09-07T08:00:00Z"
        const val TEXTO_ORIENTACION_INICIAL =
            "Mientras llegas a consulta, manten reposo, bebe liquidos y no te automediques con antibioticos."
        const val TEXTO_RESPUESTA_AUTOMATICA =
            "Gracias por escribir. Un profesional revisara tu mensaje y te respondera en breve."
    }
}
