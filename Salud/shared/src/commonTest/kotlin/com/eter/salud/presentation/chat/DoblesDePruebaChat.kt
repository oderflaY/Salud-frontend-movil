package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio

/** Chat falso: registra lo enviado y permite simular fallos de envio o de historial. */
class ChatRepositorioFalso(
    private val historial: Result<List<MensajeChat>> = Result.success(
        listOf(
            MensajeChat(
                idMensaje = "msg_semilla_1",
                autor = AutorMensaje.MEDICO,
                texto = "Mientras llegas a consulta, manten reposo, bebe liquidos y no te automediques con antibioticos.",
                instante = "2026-09-07T08:00:00Z",
                tipo = TipoMensaje.ORIENTACION_INICIAL,
            ),
        ),
    ),
    private val resultadoEnvio: Result<MensajeChat>? = null,
    private val resultadoRespuestaAutomatica: Result<MensajeChat>? = null,
) : ChatRepositorio {

    var mensajesEnviados: MutableList<String> = mutableListOf()
        private set

    var vecesRespuestaAutomatica: Int = 0
        private set

    private var siguienteId = 100

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> = historial

    var ultimoAutorEnviado: AutorMensaje? = null
        private set

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
    ): Result<MensajeChat> {
        mensajesEnviados += texto
        ultimoAutorEnviado = autor
        return resultadoEnvio ?: Result.success(
            MensajeChat(
                idMensaje = "msg_confirmado_${siguienteId++}",
                autor = autor,
                texto = texto,
                instante = instante,
            ),
        )
    }

    override suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat> {
        vecesRespuestaAutomatica++
        return resultadoRespuestaAutomatica ?: Result.success(
            MensajeChat(
                idMensaje = "msg_auto_${siguienteId++}",
                autor = AutorMensaje.MEDICO,
                texto = "Gracias por escribir. Un profesional revisara tu mensaje y te respondera en breve.",
                instante = instante,
            ),
        )
    }
}
