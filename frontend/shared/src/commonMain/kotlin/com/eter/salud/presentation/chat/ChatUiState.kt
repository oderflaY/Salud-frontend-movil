package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.Adjunto
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
    /**
     * Adjunto ya elegido (foto, archivo o pagina escaneada), a la espera de que
     * el paciente pulse enviar. Vive aparte de [textoEnCurso] porque los dos
     * viajan juntos en el MISMO mensaje: adjuntar no envia solo, es el envio el
     * que decide cuando el archivo sale de verdad.
     */
    val adjuntoEnCurso: Adjunto? = null,
    val cargando: Boolean = false,
    val errorCarga: Boolean = false,
    val errorEnvio: Boolean = false,
    /** El medico esta "escribiendo" la respuesta automatica de cortesia. */
    val medicoEscribiendo: Boolean = false,
) {
    /** Hay algo que enviar: texto, adjunto, o los dos. Nunca ninguno. */
    val puedeEnviar: Boolean get() = textoEnCurso.isNotBlank() || adjuntoEnCurso != null
}
