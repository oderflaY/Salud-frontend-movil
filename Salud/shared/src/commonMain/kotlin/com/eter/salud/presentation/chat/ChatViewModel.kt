package com.eter.salud.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del chat de orientacion de primera vista.
 *
 * Dos decisiones de diseno responden al contexto clinico:
 *  - El envio es optimista: la burbuja del paciente aparece al instante y se
 *    reconcilia con el id definitivo del backend, o se retira si el envio
 *    falla. Una app de salud no debe sentirse mas lenta que un chat comun.
 *  - Solo el primer mensaje del paciente en la conversacion dispara la
 *    respuesta automatica de cortesia. No es un bot conversando: es un unico
 *    acuse de recibo del sistema, y fingir mas seria enganoso.
 *
 * No conoce Compose, ni recursos, ni el hardware: solo el contrato
 * [ChatRepositorio].
 */
class ChatViewModel(
    private val repositorio: ChatRepositorio,
    private val idConversacion: String,
    nombreMedico: String,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ChatUiState(idConversacion = idConversacion, nombreMedico = nombreMedico),
    )
    val estado: StateFlow<ChatUiState> = _estado.asStateFlow()

    init {
        // Atada al ciclo de vida del ViewModel, no a un `LaunchedEffect`: ver
        // la nota en `DescubrimientoMedicoViewModel`.
        cargar()
    }

    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { mensajes -> previo.copy(cargando = false, mensajes = mensajes) },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
        }
    }

    fun actualizarTexto(valor: String) {
        _estado.update { it.copy(textoEnCurso = valor, errorEnvio = false) }
    }

    /**
     * Envia el mensaje en curso. La burbuja aparece de inmediato con un id
     * provisional; cuando el backend confirma, ese id se reemplaza por el
     * definitivo, y si falla, el mensaje se retira y el texto vuelve al campo.
     */
    fun enviarMensaje() {
        val actual = _estado.value
        val texto = actual.textoEnCurso.trim()
        if (texto.isBlank()) return

        val esPrimerMensajeDelPaciente = actual.mensajes.none { it.autor == AutorMensaje.PACIENTE }
        val instante = reloj.instanteActual()
        val provisional = MensajeChat(
            idMensaje = "$PREFIJO_ID_PROVISIONAL${actual.mensajes.size}_$instante",
            autor = AutorMensaje.PACIENTE,
            texto = texto,
            instante = instante,
        )

        _estado.update {
            it.copy(mensajes = it.mensajes + provisional, textoEnCurso = "", errorEnvio = false)
        }

        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.enviarMensaje(
                    idConversacion = idConversacion,
                    texto = texto,
                    instante = instante,
                    autor = AutorMensaje.PACIENTE,
                )
            }
            resultado.fold(
                onSuccess = { confirmado ->
                    _estado.update { previo ->
                        previo.copy(mensajes = previo.mensajes.reemplazarPor(provisional.idMensaje, confirmado))
                    }
                    if (esPrimerMensajeDelPaciente) enviarRespuestaAutomatica()
                },
                onFailure = {
                    _estado.update { previo ->
                        previo.copy(
                            mensajes = previo.mensajes.filterNot { m -> m.idMensaje == provisional.idMensaje },
                            errorEnvio = true,
                            textoEnCurso = texto,
                        )
                    }
                },
            )
        }
    }

    fun descartarErrorEnvio() {
        _estado.update { it.copy(errorEnvio = false) }
    }

    private suspend fun enviarRespuestaAutomatica() {
        _estado.update { it.copy(medicoEscribiendo = true) }
        val resultado = ejecutarSeguro { repositorio.obtenerRespuestaAutomatica(idConversacion, reloj.instanteActual()) }
        _estado.update { previo ->
            resultado.fold(
                onSuccess = { respuesta ->
                    previo.copy(medicoEscribiendo = false, mensajes = previo.mensajes + respuesta)
                },
                onFailure = { previo.copy(medicoEscribiendo = false) },
            )
        }
    }

    private fun List<MensajeChat>.reemplazarPor(idProvisional: String, definitivo: MensajeChat) =
        map { if (it.idMensaje == idProvisional) definitivo else it }

    private companion object {
        const val PREFIJO_ID_PROVISIONAL = "local_"
    }
}
