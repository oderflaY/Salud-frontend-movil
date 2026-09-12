package com.eter.salud.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bandeja de conversaciones del medico, ordenada por triage.
 *
 * Cruza tres fuentes que nadie juntaba: la cartera sabe QUIEN es paciente del
 * medico y su riesgo de expediente, el diario sabe COMO se siente hoy, y el chat
 * sabe QUE se dijo la ultima vez. Ese cruce es logica de presentacion -- no de
 * red --, asi que vive aqui y no en un repositorio.
 *
 * ## Por que el orden es por gravedad y no por fecha
 *
 * Una bandeja de mensajeria ordena por lo ultimo que llego porque todos los
 * mensajes valen lo mismo. En una consulta no: un paciente critico que escribio
 * hace tres horas va antes que uno estable que escribio hace un minuto. La fecha
 * solo desempata dentro de un mismo nivel.
 */
class InboxViewModel(
    private val pacientesVinculados: PacientesVinculadosRepositorio,
    private val chat: ChatRepositorio,
    private val diario: DiarioRepositorio,
    private val idMedico: String,
) : ViewModel() {

    private val _estado = MutableStateFlow<InboxUiState>(InboxUiState.Cargando)
    val estado: StateFlow<InboxUiState> = _estado.asStateFlow()

    private var consultaEnCurso = false

    init {
        // Atada al ciclo de vida del ViewModel: la bandeja se llena sola aunque
        // la Vista nunca lo pida.
        cargar()
    }

    /**
     * Recarga la bandeja entera.
     *
     * La Vista la llama al volver a la pestana: el medico acaba de contestar un
     * chat, y la fila tiene que decir lo que el escribio, no lo que habia antes.
     * Mientras una consulta viaja, las demas llamadas se ignoran.
     */
    fun cargar() {
        if (consultaEnCurso) return
        consultaEnCurso = true
        // Solo se vuelve a "Cargando" si no habia nada que mostrar: al refrescar
        // una bandeja ya pintada, sustituir la lista por un esqueleto la haria
        // parpadear cada vez que el medico vuelve de un chat.
        if (_estado.value !is InboxUiState.ConConversaciones) {
            _estado.value = InboxUiState.Cargando
        }

        viewModelScope.launch {
            try {
                val cartera = ejecutarSeguro { pacientesVinculados.obtenerPacientesVinculados(idMedico) }
                if (cartera.isFailure) {
                    _estado.value = InboxUiState.Error
                    return@launch
                }

                // Sin canal de chat no hay conversacion que listar: ese paciente
                // vive en el panel, no en la bandeja.
                val conCanal = cartera.getOrThrow().filter { it.idConversacion.isNotBlank() }
                if (conCanal.isEmpty()) {
                    _estado.value = InboxUiState.SinConversaciones
                    return@launch
                }

                val filas = conCanal.map { paciente -> resumir(paciente) }
                _estado.value = InboxUiState.ConConversaciones(ordenar(filas))
            } finally {
                consultaEnCurso = false
            }
        }
    }

    /**
     * Arma la fila de un paciente.
     *
     * Ni el diario ni el chat pueden tumbar la fila: si el diario falla, el
     * nivel se rige por el expediente; si el historial falla, la fila sale sin
     * vista previa. Un paciente que desaparece de la bandeja porque su historial
     * no cargo es peor que una fila sin su ultima frase.
     */
    private suspend fun resumir(paciente: PacienteVinculado): ConversacionClinica {
        val severidad = ejecutarSeguro { diario.ultimaEntradaDe(paciente.idPaciente) }
            .getOrNull()
            ?.severidad
        val ultimo = ejecutarSeguro { chat.obtenerHistorial(paciente.idConversacion) }
            .getOrNull()
            ?.lastOrNull()

        return ConversacionClinica(
            paciente = paciente,
            nivel = NivelTriage.combinar(paciente.riesgo, severidad),
            ultimoMensaje = ultimo?.texto,
            instanteUltimoMensaje = ultimo?.instante,
            autorUltimoMensaje = ultimo?.autor,
        )
    }

    companion object {

        /**
         * Gravedad descendente; dentro de un mismo nivel, lo mas reciente
         * primero, y las conversaciones sin mensajes al final de su nivel.
         *
         * Los instantes son ISO 8601 UTC con el mismo formato, asi que su orden
         * lexicografico ES su orden cronologico: no hace falta convertirlos.
         */
        internal fun ordenar(filas: List<ConversacionClinica>): List<ConversacionClinica> =
            filas.sortedWith(
                compareByDescending<ConversacionClinica> { it.nivel.ordinal }
                    .thenByDescending { it.instanteUltimoMensaje.orEmpty() },
            )
    }
}
