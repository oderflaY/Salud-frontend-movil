package com.eter.salud.presentation.chatmedico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.RiesgoPaciente
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
 * ViewModel del chat visto desde el perfil del medico.
 *
 * Comparte [ChatRepositorio] y conversacion con el chat del paciente: son dos
 * caras del mismo canal, no dos historiales distintos. Por eso el autor viaja
 * explicito en cada envio ([AutorMensaje.MEDICO] aqui) en lugar de darse por
 * supuesto en el repositorio.
 *
 * El envio es optimista: la burbuja aparece al instante y se reconcilia con el
 * id definitivo del backend, o se retira si falla, devolviendo el texto al
 * campo para que el medico no lo pierda.
 *
 * A diferencia del lado del paciente, aqui NO hay respuesta automatica: el
 * medico escribe de verdad, y simular un interlocutor seria enganoso.
 *
 * No conoce Compose ni recursos: solo el contrato [ChatRepositorio].
 */
class ChatMedicoViewModel(
    private val repositorio: ChatRepositorio,
    private val idConversacion: String,
    nombrePaciente: String,
    riesgoPaciente: RiesgoPaciente,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ChatMedicoUiState(
            idConversacion = idConversacion,
            nombrePaciente = nombrePaciente,
            riesgoPaciente = riesgoPaciente,
        ),
    )
    val estado: StateFlow<ChatMedicoUiState> = _estado.asStateFlow()

    /**
     * Guardia de reentrada propio y no `historial == Cargando`: `Cargando` es
     * tambien el estado inicial, asi que mirarlo bloquearia la primera carga.
     */
    private var consultaEnCurso = false

    init {
        // Atada al ciclo de vida del ViewModel, no a un `LaunchedEffect`: ver
        // la nota en `DescubrimientoMedicoViewModel`.
        cargar()
    }

    fun cargar() {
        if (consultaEnCurso) return
        consultaEnCurso = true
        _estado.update { it.copy(historial = HistorialChatUiState.Cargando) }
        viewModelScope.launch {
            try {
                val resultado = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }
                _estado.update { previo ->
                    resultado.fold(
                        onSuccess = { mensajes ->
                            previo.copy(
                                historial = HistorialChatUiState.ConMensajes(
                                    mensajes.map { it.aVisible() },
                                ),
                            )
                        },
                        onFailure = {
                            previo.copy(
                                historial = HistorialChatUiState.Error(ErrorChatMedico.SIN_CONEXION),
                            )
                        },
                    )
                }
            } finally {
                consultaEnCurso = false
            }
        }
    }

    fun actualizarTexto(valor: String) {
        _estado.update { it.copy(textoEnCurso = valor, errorEnvio = false) }
    }

    /** Una respuesta rapida no se envia sola: llena el campo para poder editarla antes de mandarla. */
    fun usarRespuestaRapida(texto: String) {
        _estado.update { it.copy(textoEnCurso = texto, errorEnvio = false) }
    }

    fun enviarMensaje() {
        val actual = _estado.value
        val texto = actual.textoEnCurso.trim()
        if (texto.isBlank()) return
        // Sin historial descargado no hay a que anexar: se evita perder el texto.
        val mensajesActuales = (actual.historial as? HistorialChatUiState.ConMensajes)?.mensajes
            ?: return

        val instante = reloj.instanteActual()
        val provisional = MensajeChat(
            idMensaje = "$PREFIJO_ID_PROVISIONAL${mensajesActuales.size}_$instante",
            autor = AutorMensaje.MEDICO,
            texto = texto,
            instante = instante,
        ).aVisible()

        _estado.update {
            it.copy(
                historial = HistorialChatUiState.ConMensajes(mensajesActuales + provisional),
                textoEnCurso = "",
                errorEnvio = false,
            )
        }

        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.enviarMensaje(
                    idConversacion = idConversacion,
                    texto = texto,
                    instante = instante,
                    autor = AutorMensaje.MEDICO,
                )
            }
            resultado.fold(
                onSuccess = { confirmado ->
                    _estado.update { previo ->
                        previo.conMensajes { mensajes ->
                            mensajes.map {
                                if (it.idMensaje == provisional.idMensaje) confirmado.aVisible() else it
                            }
                        }
                    }
                },
                onFailure = {
                    _estado.update { previo ->
                        previo
                            .conMensajes { mensajes ->
                                mensajes.filterNot { it.idMensaje == provisional.idMensaje }
                            }
                            .copy(errorEnvio = true, textoEnCurso = texto)
                    }
                },
            )
        }
    }

    fun descartarErrorEnvio() {
        _estado.update { it.copy(errorEnvio = false) }
    }

    /**
     * Traduce el mensaje de dominio al que pinta la Vista: resuelve el lado de
     * la burbuja y la hora local, para que la Vista no toque zonas horarias.
     */
    private fun MensajeChat.aVisible() = MensajeVisibleChat(
        idMensaje = idMensaje,
        texto = texto,
        autor = autor,
        esPropio = autor == AutorMensaje.MEDICO,
        horaLocal = reloj.horaLocal(instante),
    )

    /** Reescribe la lista solo si el historial sigue descargado. */
    private fun ChatMedicoUiState.conMensajes(
        bloque: (List<MensajeVisibleChat>) -> List<MensajeVisibleChat>,
    ): ChatMedicoUiState {
        val actuales = (historial as? HistorialChatUiState.ConMensajes)?.mensajes ?: return this
        return copy(historial = HistorialChatUiState.ConMensajes(bloque(actuales)))
    }

    private companion object {
        const val PREFIJO_ID_PROVISIONAL = "local_medico_"
    }
}
