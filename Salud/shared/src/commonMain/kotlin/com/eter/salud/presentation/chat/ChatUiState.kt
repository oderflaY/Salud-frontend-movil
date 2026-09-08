package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.MensajeChat

/**
 * Estado unico de la pantalla de chat. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class ChatUiState(
    val idConversacion: String,
    val nombreMedico: String = "",
    val mensajes: List<MensajeChat> = emptyList(),
    val textoEnCurso: String = "",
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
    val errorEnvio: Boolean = false,
    /** El medico esta "escribiendo" la respuesta automatica de cortesia. */
    val medicoEscribiendo: Boolean = false,
) {
    val puedeEnviar: Boolean get() = textoEnCurso.isNotBlank()
}
