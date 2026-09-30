package com.eter.salud.presentation.chatmedico

import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.comun.EstadoTraduccion

/**
 * Estado del resumen clinico que la IA del backend extrae de un mensaje largo
 * del paciente.
 *
 * Vive en tres momentos, no en dos: mientras el resumen se genera la tarjeta no
 * puede mostrar nada ([Cargando]), y un resumen puede fallar sin que eso impida
 * seguir leyendo el mensaje tal cual llego ([Fallido] conserva el texto
 * original para ese caso).
 */
sealed interface EstadoResumenIa {

    /** El resumen se esta generando. */
    data object Cargando : EstadoResumenIa

    /** Resumen listo para mostrarse. */
    data class Disponible(val resumen: ResumenClinicoIa) : EstadoResumenIa

    /** El backend no pudo resumir el mensaje; se conserva el original para leerlo igual. */
    data class Fallido(val mensajeOriginal: String) : EstadoResumenIa
}

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
    val adjunto: Adjunto? = null,
    /** `YYYY-MM-DD` local, para separar la conversacion por dias. */
    val fechaLocal: String = "",
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
    /** Archivo, foto o escaneo elegido y aun sin enviar. */
    val adjuntoEnCurso: Adjunto? = null,
    val errorEnvio: Boolean = false,
    /** Resumen de IA por `idMensaje`, solo para los mensajes del paciente que lo ameritan. */
    val resumenesIa: Map<String, EstadoResumenIa> = emptyMap(),
    /** `idMensaje` cuyo mensaje original se muestra expandido bajo su resumen. */
    val originalExpandido: Set<String> = emptySet(),
    /** Traduccion por mensaje, solo de los que el medico pidio traducir. */
    val traducciones: Map<String, EstadoTraduccion> = emptyMap(),
    /** El backend tiene traduccion configurada; si no, no se ofrece la accion. */
    val puedeTraducir: Boolean = false,
    /** Traducir sin preguntar todo lo que escribe el paciente (preferencia guardada). */
    val traduccionAutomatica: Boolean = false,
) {
    val puedeEnviar: Boolean get() = textoEnCurso.isNotBlank() || adjuntoEnCurso != null

    /** Mensajes ya descargados; vacio mientras carga o si fallo. */
    val mensajes: List<MensajeVisibleChat>
        get() = (historial as? HistorialChatUiState.ConMensajes)?.mensajes.orEmpty()
}
