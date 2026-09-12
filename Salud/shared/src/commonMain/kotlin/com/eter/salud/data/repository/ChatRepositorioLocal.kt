package com.eter.salud.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.data.local.idLocal
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.domain.model.TipoMensaje
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.ResumenIaNoDisponible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Chat paciente-medico sobre la base SQLite del dispositivo.
 *
 * Mismo comportamiento que [ChatRepositorioEnMemoria] -- conversacion semilla
 * al abrir un canal nuevo, contador de no leidos en vivo, acuse automatico que
 * no cuenta como pendiente --, pero los mensajes sobreviven a cerrar la app y a
 * cerrar sesion.
 */
class ChatRepositorioLocal(
    private val base: BaseSalud,
) : ChatRepositorio {

    private val consultas get() = base.mensajesQueries

    override suspend fun obtenerHistorial(idConversacion: String): Result<List<MensajeChat>> =
        withContext(ContextoDeBase) {
            sembrarSiVacia(idConversacion)
            Result.success(consultas.historial(idConversacion).executeAsList().map { it.aMensaje() })
        }

    override suspend fun enviarMensaje(
        idConversacion: String,
        texto: String,
        instante: String,
        autor: AutorMensaje,
        adjunto: Adjunto?,
    ): Result<MensajeChat> = withContext(ContextoDeBase) {
        sembrarSiVacia(idConversacion)
        val mensaje = MensajeChat(idLocal(PREFIJO_MENSAJE), autor, texto, instante, adjunto = adjunto)
        insertar(idConversacion, mensaje)
        Result.success(mensaje)
    }

    /** Se recalcula solo: la consulta se vuelve a emitir con cada mensaje o lectura. */
    override fun mensajesSinLeer(idConversacion: String): Flow<Int> =
        consultas.sinLeer(idConversacion)
            .asFlow()
            .mapToOne(ContextoDeBase)
            .map { it.toInt().coerceAtLeast(0) }

    override suspend fun marcarConversacionLeida(idConversacion: String) {
        withContext(ContextoDeBase) {
            consultas.marcarVistos(idConversacion, consultas.mensajesDelMedico(idConversacion).executeAsOne())
        }
    }

    override suspend fun obtenerRespuestaAutomatica(
        idConversacion: String,
        instante: String,
    ): Result<MensajeChat> = withContext(ContextoDeBase) {
        val respuesta = MensajeChat(
            idMensaje = idLocal(PREFIJO_MENSAJE),
            autor = AutorMensaje.MEDICO,
            texto = ChatRepositorioEnMemoria.TEXTO_RESPUESTA_AUTOMATICA,
            instante = instante,
        )
        base.transaction {
            val vistos = consultas.vistos(idConversacion).executeAsOneOrNull() ?: 0L
            insertar(idConversacion, respuesta)
            // Se da por vista en el acto: llega DENTRO de la conversacion que el
            // paciente tiene abierta, y un punto rojo sobre ella seria falso.
            consultas.marcarVistos(idConversacion, vistos + 1)
        }
        Result.success(respuesta)
    }

    override suspend fun obtenerResumenClinico(idMensaje: String): Result<ResumenClinicoIa> =
        Result.failure(ResumenIaNoDisponible())

    /**
     * Conversacion semilla de un canal nuevo: la orientacion del medico y un
     * intercambio previo, ya leidos. Los identificadores llevan el canal para
     * que dos conversaciones sembradas no choquen en la clave primaria.
     */
    private fun sembrarSiVacia(idConversacion: String) {
        if (consultas.contar(idConversacion).executeAsOne() > 0) return
        base.transaction {
            sembrarConversacion(base, idConversacion, semillaGenerica(idConversacion))
        }
    }

    private fun insertar(idConversacion: String, mensaje: MensajeChat) {
        with(mensaje) {
            consultas.insertar(
                idMensaje, idConversacion, autor.name, texto, instante, tipo.name,
                adjunto?.tipo?.name, adjunto?.nombre, adjunto?.rutaLocal, adjunto?.tipoMime,
            )
        }
    }

    private fun semillaGenerica(idConversacion: String) = listOf(
        MensajeChat(
            idMensaje = "${idConversacion}_semilla_1",
            autor = AutorMensaje.PACIENTE,
            texto = "Buenas tardes doctora. Sigo con la presion alta por las mananas.",
            instante = "2026-09-07T15:10:00Z",
        ),
        MensajeChat(
            idMensaje = "${idConversacion}_semilla_2",
            autor = AutorMensaje.MEDICO,
            texto = "Gracias por avisarme. Registra tus tomas esta semana y lo revisamos.",
            instante = "2026-09-07T15:22:00Z",
        ),
        MensajeChat(
            idMensaje = "${idConversacion}_semilla_3",
            autor = AutorMensaje.PACIENTE,
            texto = "De acuerdo, lo hare.",
            instante = "2026-09-07T15:24:00Z",
        ),
    )

    private fun com.eter.salud.data.db.Mensaje.aMensaje() = MensajeChat(
        idMensaje = id_mensaje,
        autor = AutorMensaje.entries.firstOrNull { it.name == autor } ?: AutorMensaje.MEDICO,
        texto = texto,
        instante = instante,
        tipo = TipoMensaje.entries.firstOrNull { it.name == tipo } ?: TipoMensaje.NORMAL,
        // Las cuatro columnas viajan juntas: si la ruta no esta, el mensaje no
        // tiene adjunto, sin importar que las demas si tuvieran algo escrito.
        adjunto = adjunto_ruta?.let { ruta ->
            Adjunto(
                idAdjunto = id_mensaje,
                tipo = TipoAdjunto.entries.firstOrNull { it.name == adjunto_tipo } ?: TipoAdjunto.ARCHIVO,
                nombre = adjunto_nombre.orEmpty(),
                rutaLocal = ruta,
                tipoMime = adjunto_mime.orEmpty(),
            )
        },
    )

    private companion object {
        const val PREFIJO_MENSAJE = "msg_"
    }
}

/**
 * Escribe una conversacion inicial precedida de la orientacion del medico y la
 * deja como leida. La usan la semilla de un canal nuevo y la siembra de demo.
 */
internal fun sembrarConversacion(base: BaseSalud, idConversacion: String, mensajes: List<MensajeChat>) {
    val orientacion = MensajeChat(
        idMensaje = "${idConversacion}_orientacion",
        autor = AutorMensaje.MEDICO,
        texto = ChatRepositorioEnMemoria.TEXTO_ORIENTACION_INICIAL,
        instante = ChatRepositorioEnMemoria.INSTANTE_SEMILLA,
        tipo = TipoMensaje.ORIENTACION_INICIAL,
    )
    (listOf(orientacion) + mensajes).forEach { m ->
        base.mensajesQueries.insertar(
            m.idMensaje, idConversacion, m.autor.name, m.texto, m.instante, m.tipo.name,
            m.adjunto?.tipo?.name, m.adjunto?.nombre, m.adjunto?.rutaLocal, m.adjunto?.tipoMime,
        )
    }
    base.mensajesQueries.marcarVistos(idConversacion, base.mensajesQueries.mensajesDelMedico(idConversacion).executeAsOne())
}
