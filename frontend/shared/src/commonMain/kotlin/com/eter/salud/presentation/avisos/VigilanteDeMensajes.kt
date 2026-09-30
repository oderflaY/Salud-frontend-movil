package com.eter.salud.presentation.avisos

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.MensajeEntrante
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Una conversacion de la que avisar, con el nombre de quien escribe en ella. */
data class ConversacionVigilada(val idConversacion: String, val nombre: String)

/** Un mensaje que merece aviso del sistema. */
data class MensajeParaAvisar(val conversacion: ConversacionVigilada, val mensaje: MensajeEntrante)

/**
 * Decide que mensajes nuevos avisar con una notificacion del telefono.
 *
 * Solo los de la otra parte (el paciente no necesita enterarse de lo que el
 * mismo escribio) y nunca los de la conversacion que la persona esta leyendo en
 * ese momento: sonar por un mensaje que ya se ve en pantalla es ruido.
 *
 * Cada mensaje se avisa una sola vez aunque el aviso llegue repetido (el socket
 * reconecta y el servidor puede reenviar).
 */
class VigilanteDeMensajes(
    private val chat: ChatRepositorio,
    /** Quien usa el telefono: sus propios mensajes no se avisan. */
    private val yo: AutorMensaje,
) {
    private val avisados = mutableSetOf<String>()

    fun mensajesParaAvisar(
        conversaciones: List<ConversacionVigilada>,
        /** La conversacion a la vista ahora mismo, o null (app en segundo plano, otra pantalla). */
        conversacionALaVista: () -> String?,
    ): Flow<MensajeParaAvisar> =
        conversaciones
            .map { conversacion ->
                chat.mensajesEntrantes(conversacion.idConversacion).map { MensajeParaAvisar(conversacion, it) }
            }
            .merge()
            .filter { (conversacion, mensaje) ->
                mensaje.autor != yo &&
                    conversacion.idConversacion != conversacionALaVista() &&
                    avisados.add(mensaje.idMensaje)
            }
}

/**
 * La conversacion que pidio abrir un toque sobre una notificacion. La escribe
 * la plataforma (la Activity en Android) y la consume la navegacion de `App()`,
 * que es la unica que sabe como llegar a un chat.
 */
object AperturaDesdeAviso {
    private val _conversacion = MutableStateFlow<String?>(null)
    val conversacion: StateFlow<String?> = _conversacion.asStateFlow()

    fun abrir(idConversacion: String) {
        _conversacion.value = idConversacion
    }

    /** Ya se navego: que no se vuelva a abrir al recomponer. */
    fun atendida() {
        _conversacion.value = null
    }
}
