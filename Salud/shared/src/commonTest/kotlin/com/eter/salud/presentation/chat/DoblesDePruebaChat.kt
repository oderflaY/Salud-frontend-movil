package com.eter.salud.presentation.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.ResumenIaNoDisponible

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
    private val resultadoResumenIa: Result<ResumenClinicoIa>? = null,
) : ChatRepositorio {

    var mensajesEnviados: MutableList<String> = mutableListOf()
        private set

    var vecesRespuestaAutomatica: Int = 0
        private set

    private var siguienteId = 100

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> = historial

    var ultimoAutorEnviado: AutorMensaje? = null
        private set

    var ultimoAdjuntoEnviado: Adjunto? = null
        private set

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto?,
    ): Result<MensajeChat> {
        mensajesEnviados += texto
        ultimoAutorEnviado = autor
        ultimoAdjuntoEnviado = adjunto
        return resultadoEnvio ?: Result.success(
            MensajeChat(
                idMensaje = "msg_confirmado_${siguienteId++}",
                autor = autor,
                texto = texto,
                instante = instante,
                adjunto = adjunto,
            ),
        )
    }

    private val pendientes = MutableStateFlow(0)

    var vecesMarcadaLeida: Int = 0
        private set

    override fun mensajesSinLeer(idConversacion: String): Flow<Int> = pendientes.asStateFlow()

    override suspend fun marcarConversacionLeida(idConversacion: String) {
        vecesMarcadaLeida++
        pendientes.value = 0
    }

    /** Simula la llegada de mensajes del medico con el paciente en otra pantalla. */
    fun simularSinLeer(cantidad: Int) {
        pendientes.value = cantidad
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

    var vecesResumenPedido: Int = 0
        private set

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> {
        vecesResumenPedido++
        return resultadoResumenIa ?: Result.failure(ResumenIaNoDisponible())
    }
}
