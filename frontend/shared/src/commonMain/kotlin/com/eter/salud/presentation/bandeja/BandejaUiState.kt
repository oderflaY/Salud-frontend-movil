package com.eter.salud.presentation.bandeja

import com.eter.salud.domain.model.ConversacionResumen

/**
 * Estado de la bandeja de conversaciones.
 *
 * Los tres escenarios son excluyentes y van sellados: la Vista esta obligada a
 * resolverlos todos y ninguno se puede quedar sin pintar por olvido.
 */
sealed interface BandejaUiState {

    data object Cargando : BandejaUiState

    /**
     * Sin ninguna conversacion abierta.
     *
     * Es un estado propio y no una lista vacia porque lo que se muestra es
     * distinto: una invitacion a buscar su primer especialista, no una pantalla
     * en blanco que parece un fallo de carga.
     */
    data object SinConversaciones : BandejaUiState

    data class ConConversaciones(val conversaciones: List<ConversacionResumen>) : BandejaUiState

    data object Error : BandejaUiState
}
