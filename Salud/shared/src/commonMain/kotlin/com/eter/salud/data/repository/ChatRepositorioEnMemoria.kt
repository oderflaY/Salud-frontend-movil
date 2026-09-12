package com.eter.salud.data.repository

import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.ResumenIaNoDisponible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
class ChatRepositorioEnMemoria(
    /**
     * Conversaciones de demostracion por canal. Un canal que no esta aqui arranca
     * con la conversacion semilla de siempre.
     */
    private val semillas: Map<String, List<MensajeChat>> = emptyMap(),
) : ChatRepositorio {

    private val conversaciones = mutableMapOf<String, MutableList<MensajeChat>>()
    private var siguienteId = 1

    /**
     * Cuantos mensajes del medico habia cuando el paciente miro por ultima vez.
     *
     * Se guarda el CONTEO y no el instante de lectura: comparar marcas de tiempo
     * exigiria un reloj fiable en el cliente, y el del telefono se puede cambiar
     * a mano. Un contador no se puede falsear moviendo la hora.
     */
    private val leidosPorConversacion = mutableMapOf<String, Int>()

    private val sinLeer = mutableMapOf<String, MutableStateFlow<Int>>()

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> {
        val historial = conversaciones.getOrPut(idConversacion) {
            conversacionSemilla(idConversacion)
        }
        return Result.success(historial.toList())
    }

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto?,
    ): Result<MensajeChat> {
        val mensaje = MensajeChat(
            idMensaje = "$PREFIJO_MENSAJE${siguienteId++}",
            autor = autor,
            texto = texto,
            instante = instante,
            adjunto = adjunto,
        )
        conversaciones.getOrPut(idConversacion) { conversacionSemilla(idConversacion) } += mensaje
        if (autor == AutorMensaje.MEDICO) refrescarSinLeer(idConversacion)
        return Result.success(mensaje)
    }

    override fun mensajesSinLeer(idConversacion: String): Flow<Int> =
        flujoSinLeer(idConversacion).asStateFlow()

    override suspend fun marcarConversacionLeida(idConversacion: String) {
        leidosPorConversacion[idConversacion] = mensajesDelMedico(idConversacion)
        flujoSinLeer(idConversacion).value = 0
    }

    private fun flujoSinLeer(idConversacion: String): MutableStateFlow<Int> =
        sinLeer.getOrPut(idConversacion) { MutableStateFlow(0) }

    private fun mensajesDelMedico(idConversacion: String): Int =
        conversaciones[idConversacion].orEmpty().count { it.autor == AutorMensaje.MEDICO }

    /** Recalcula el pendiente tras anadir un mensaje del medico. */
    private fun refrescarSinLeer(idConversacion: String) {
        val vistos = leidosPorConversacion[idConversacion] ?: 0
        flujoSinLeer(idConversacion).value =
            (mensajesDelMedico(idConversacion) - vistos).coerceAtLeast(0)
    }

    /**
     * Conversacion inicial: la orientacion del medico y tres mensajes previos.
     *
     * Existe para que la bandeja no arranque vacia. Una bandeja en blanco en la
     * primera ejecucion no permite juzgar el diseno de la fila -- ni el recorte
     * del ultimo mensaje, ni la hora, ni el disco -- que es justo lo que hay que
     * poder ensayar antes de conectar el backend.
     */
    private fun conversacionSemilla(idConversacion: String): MutableList<MensajeChat> {
        val propia = semillas[idConversacion]
        if (propia != null) return (listOf(mensajeOrientacionInicial()) + propia).toMutableList()
        return semillaGenerica()
    }

    private fun semillaGenerica(): MutableList<MensajeChat> = mutableListOf(
        mensajeOrientacionInicial(),
        MensajeChat(
            idMensaje = "${PREFIJO_MENSAJE}semilla_1",
            autor = AutorMensaje.PACIENTE,
            texto = "Buenas tardes doctora. Sigo con la presion alta por las mananas.",
            instante = "2026-09-07T15:10:00Z",
        ),
        MensajeChat(
            idMensaje = "${PREFIJO_MENSAJE}semilla_2",
            autor = AutorMensaje.MEDICO,
            texto = "Gracias por avisarme. Registra tus tomas esta semana y lo revisamos.",
            instante = "2026-09-07T15:22:00Z",
        ),
        MensajeChat(
            idMensaje = "${PREFIJO_MENSAJE}semilla_3",
            autor = AutorMensaje.PACIENTE,
            texto = "De acuerdo, lo hare.",
            instante = "2026-09-07T15:24:00Z",
        ),
    )

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
        conversaciones.getOrPut(idConversacion) { conversacionSemilla(idConversacion) } += respuesta
        // No incrementa el pendiente a proposito: esta cortesia se entrega
        // DENTRO de la conversacion que el paciente acaba de abrir para
        // escribir. Marcarla sin leer pondria un punto rojo sobre un mensaje que
        // el paciente esta viendo en ese mismo instante.
        return Result.success(respuesta)
    }

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> =
        Result.failure(ResumenIaNoDisponible())

    private fun mensajeOrientacionInicial() = MensajeChat(
        idMensaje = "$PREFIJO_MENSAJE${siguienteId++}",
        autor = AutorMensaje.MEDICO,
        texto = TEXTO_ORIENTACION_INICIAL,
        instante = INSTANTE_SEMILLA,
        tipo = TipoMensaje.ORIENTACION_INICIAL,
    )

    // Interno y no privado: [ChatRepositorioLocal] siembra con los mismos textos.
    internal companion object {
        const val PREFIJO_MENSAJE = "msg_local_"
        const val INSTANTE_SEMILLA = "2026-09-07T08:00:00Z"
        const val TEXTO_ORIENTACION_INICIAL =
            "Mientras llegas a consulta, manten reposo, bebe liquidos y no te automediques con antibioticos."
        const val TEXTO_RESPUESTA_AUTOMATICA =
            "Gracias por escribir. Un profesional revisara tu mensaje y te respondera en breve."
    }
}
