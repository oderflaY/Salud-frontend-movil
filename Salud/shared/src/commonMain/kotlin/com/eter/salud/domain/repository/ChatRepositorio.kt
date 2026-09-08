package com.eter.salud.domain.repository

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat

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
     */
    suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
    ): Result<MensajeChat>

    suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat>
}
