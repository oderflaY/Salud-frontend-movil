package com.eter.salud.presentation.chatmedico

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.RiesgoPaciente

/**
 * Mensaje ya preparado para pintarse.
 *
 * El ViewModel resuelve aqui las dos cosas que la Vista no debe decidir: de que
 * lado va la burbuja ([esPropio]) y la hora local legible, convertida desde el
 * ISO 8601 UTC del dominio. La Vista solo coloca texto.
 */
data class MensajeVisibleChat(
    val idMensaje: String,
    val texto: String,
    val autor: AutorMensaje,
    val esPropio: Boolean,
    val horaLocal: String,
)

/**
 * Estado del historial de la conversacion. Jerarquia sellada y no banderas
 * sueltas: la Vista queda obligada por el compilador a resolver los tres
 * escenarios, sin poder dejar la pantalla muda en ninguno.
 */
sealed interface HistorialChatUiState {

    /** Consulta en vuelo: la Vista pinta el indicador de carga. */
    data object Cargando : HistorialChatUiState

    /**
     * Conversacion descargada. Puede venir sin mensajes (paciente recien
     * vinculado que aun no escribe), y ese caso tambien se pinta explicito.
     */
    data class ConMensajes(val mensajes: List<MensajeVisibleChat>) : HistorialChatUiState

    /** La consulta fallo. El motivo viaja tipado, nunca como texto. */
    data class Error(val motivo: ErrorChatMedico) : HistorialChatUiState
}

/**
 * Motivos de fallo del chat.
 *
 * Enum y no `String`: el ViewModel jamas produce texto para el usuario, emite
 * claves que la Vista traduce contra `strings.xml` (DM_Arquitectura_App.md,
 * seccion 5: cero hardcoding).
 */
enum class ErrorChatMedico {
    SIN_CONEXION,
}

/**
 * Estado unico de la pantalla de chat del medico. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 *
 * Lleva el contexto clinico del paciente (nombre y semaforo de riesgo) porque
 * la cabecera lo necesita a la vista mientras el medico responde: quien es y
 * cuanto urge, sin salir de la conversacion.
 */
data class ChatMedicoUiState(
    val idConversacion: String,
    val nombrePaciente: String = "",
    val riesgoPaciente: RiesgoPaciente = RiesgoPaciente.BAJO,
    val historial: HistorialChatUiState = HistorialChatUiState.Cargando,
    val textoEnCurso: String = "",
    val errorEnvio: Boolean = false,
) {
    val puedeEnviar: Boolean get() = textoEnCurso.isNotBlank()

    /** Mensajes ya descargados; vacio mientras carga o si fallo. */
    val mensajes: List<MensajeVisibleChat>
        get() = (historial as? HistorialChatUiState.ConMensajes)?.mensajes.orEmpty()
}
