package com.eter.salud.domain.repository

import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del chat de orientacion de primera vista hacia la API en Go.
 *
 * [obtenerRespuestaAutomatica] es deliberadamente un metodo aparte y no un
 * "bot" generico: modela una unica cortesia del sistema (acuse de recibo tras
 * el primer mensaje del paciente), nunca una IA respondiendo en nombre del
 * medico. El resto de la conversacion la escribe el medico de verdad.
 */
interface ChatRepositorio {

    suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>>

    /**
     * @param autor quien escribe. La misma conversacion la usan las dos partes
     * (el paciente desde su portal y el medico desde el suyo), asi que el autor
     * viaja explicito: darlo por supuesto haria que los mensajes del medico se
     * pintaran como del paciente.
     * @param adjunto una foto, un archivo o una pagina escaneada, si el mensaje
     * lleva una. El backend solo necesita conservarla junto al mensaje; quien
     * decide COMO se consiguio (galeria, selector de archivos, camara del
     * escaner) es la capa de presentacion.
     */
    suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto? = null,
    ): Result<MensajeChat>

    /**
     * Mensajes del medico que el paciente aun no ha visto, en vivo.
     *
     * Es un [Flow] y no una consulta puntual porque el punto rojo de la barra
     * inferior tiene que aparecer en el momento en que llega el mensaje, con el
     * paciente mirando otra pestana. Hoy lo alimenta la memoria; manana, el
     * mismo canal que traiga los mensajes.
     */
    fun mensajesSinLeer(idConversacion: String): Flow<Int>

    /**
     * Marca la conversacion como vista. La llama la pantalla de chat al abrirse:
     * ver los mensajes ES leerlos, y pedir un gesto aparte para bajar el
     * contador seria inventarse un tramite.
     */
    suspend fun marcarConversacionLeida(idConversacion: String)

    suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat>

    /**
     * Pide el resumen clinico de un mensaje largo del paciente, para la vista
     * del medico ([com.eter.salud.presentation.chatmedico.ChatMedicoViewModel]).
     *
     * Nunca se calcula en el cliente: a diferencia del triage del diario (una
     * regla determinista, igual en cualquier plataforma), un resumen clinico es
     * una interpretacion del texto, y esa interpretacion la hace el modelo que
     * el backend decida usar -- el telefono no tiene con que producirla.
     */
    suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa>
}

/**
 * Fallo de [ChatRepositorio.obtenerResumenClinico] cuando no hay backend que
 * pueda generar el resumen (los repositorios locales/en memoria de
 * desarrollo). La tarjeta que lo consume ya sabe degradar a mostrar el
 * mensaje sin resumir ante cualquier fallo, asi que no hace falta un motivo
 * mas fino que este.
 */
class ResumenIaNoDisponible : Exception("El resumen de IA requiere un backend conectado")
