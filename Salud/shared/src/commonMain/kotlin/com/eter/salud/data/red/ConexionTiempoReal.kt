package com.eter.salud.data.red

import com.eter.salud.data.sesion.FuenteDeSesion
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException

/**
 * Un evento del socket multiplexado (`mapeo-endpoints.md`, seccion 11):
 * `{"canal": "agenda:doc_xxx", "evento": "cita_actualizada", "payload": {...}}`.
 *
 * No es el unico mensaje que manda el servidor -- tambien confirma o rechaza
 * una suscripcion con `{"tipo": "suscrito"/"error", ...}` -- pero esta clase
 * modela solo la forma de evento: un mensaje de confirmacion, al no traer
 * `evento`/`payload`, simplemente falla al decodificarse contra ella y se
 * descarta en silencio (ver [ConexionTiempoReal.bucleDeConexion]).
 */
@Serializable
data class SobreTiempoReal(
    val canal: String,
    val evento: String,
    val payload: JsonElement,
)

/** Mensaje que el cliente manda para entrar o salir de un canal. */
@Serializable
private data class SolicitudDeCanal(val suscribir: String? = null, val desuscribir: String? = null)

/**
 * Lo unico que un repositorio `*Remoto` necesita del socket de tiempo real:
 * suscribirse a un canal. Separado de [ConexionTiempoReal] para que
 * [com.eter.salud.data.repository.ChatRepositorioRemoto] y
 * [com.eter.salud.data.repository.CitasRepositorioRemoto] se puedan probar con
 * un doble de prueba que emite canales a mano, sin simular un WebSocket real.
 */
interface CanalTiempoReal {
    /** Suscribe al [nombre] de canal exacto; decodifica el `payload` con [decodificar]. */
    fun <T> canal(nombre: String, decodificar: (JsonElement) -> T): Flow<T>
}

/**
 * Una sola conexion de WebSocket para toda la app, compartida por
 * [com.eter.salud.data.repository.ChatRepositorioRemoto] y
 * [com.eter.salud.data.repository.CitasRepositorioRemoto].
 *
 * ## Por que un socket y no uno por contrato
 *
 * `CONTRATOS_BACKEND.md` sugiere un unico `WS /realtime?token=jwt` que
 * multiplexa canales (`chat:{idConversacion}`, `agenda:{idMedico}`) en vez de
 * abrir un socket por cada `Flow` en vivo del dominio. Aqui se modela igual:
 * una sola conexion de fondo, y cada [canal] es un filtro sobre el mismo flujo
 * de entrada.
 *
 * ## Suscripcion explicita por canal
 *
 * El backend no manda de oficio todo lo que pasa: hay que pedir cada canal con
 * `{"suscribir": "chat:conv_xxx"}` antes de recibir nada de el (y puede
 * rechazarlo si el JWT no tiene derecho a ese canal). [canal] recuerda cada
 * nombre pedido en [canalesSuscritos] y lo vuelve a suscribir el mismo al
 * reconectar: una reconexion es un socket nuevo para el servidor, que no
 * recuerda nada de la conexion anterior.
 *
 * ## Reconexion, sin desconexion por falta de suscriptores
 *
 * La conexion se abre con el primer canal que alguien pida y se reintenta sola
 * (con una pausa fija) si se cae. NO se cierra cuando el ultimo suscriptor
 * cancela: para una app clinica, mantener el socket abierto mientras la app
 * este en primer plano es mas simple y mas barato de razonar que un contador
 * de suscriptores con apagado diferido, y el costo -- una conexion ociosa -- es
 * bajo comparado con perder una cita o un mensaje mientras se reabre.
 */
class ConexionTiempoReal(
    private val cliente: HttpClient,
    private val fuenteDeSesion: FuenteDeSesion,
    private val alcance: CoroutineScope,
    private val urlBase: () -> String = { ConfiguracionApi.urlTiempoReal() },
) : CanalTiempoReal {
    private val entrante = MutableSharedFlow<SobreTiempoReal>(extraBufferCapacity = TAMANO_MEMORIA_INTERMEDIA)
    private var trabajo: Job? = null
    private var sesionActual: DefaultClientWebSocketSession? = null
    private val canalesSuscritos = mutableSetOf<String>()
    private val mutex = Mutex()

    override fun <T> canal(nombre: String, decodificar: (JsonElement) -> T): Flow<T> =
        entrante
            .onSubscription { suscribir(nombre) }
            .filter { it.canal == nombre }
            .mapNotNull { sobre -> runCatching { decodificar(sobre.payload) }.getOrNull() }

    /** Registra el canal y lo pide al servidor si ya hay una conexion viva. */
    private suspend fun suscribir(nombre: String) {
        mutex.withLock {
            val yaPedido = nombre in canalesSuscritos
            canalesSuscritos += nombre
            val sesion = sesionActual
            if (!yaPedido && sesion != null) {
                enviarSolicitud(sesion, SolicitudDeCanal(suscribir = nombre))
            }
        }
        asegurarConexion()
    }

    private suspend fun asegurarConexion() {
        mutex.withLock {
            if (trabajo?.isActive == true) return
            trabajo = alcance.launch { bucleDeConexion() }
        }
    }

    private suspend fun bucleDeConexion() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                val sesion = fuenteDeSesion.sesion.first()
                val token = sesion.paciente?.token ?: sesion.profesional?.token
                val url = urlBase() + if (!token.isNullOrBlank()) "?token=$token" else ""
                cliente.webSocket(url) {
                    mutex.withLock {
                        sesionActual = this
                        canalesSuscritos.forEach { nombre -> enviarSolicitud(this, SolicitudDeCanal(suscribir = nombre)) }
                    }
                    try {
                        for (marco in incoming) {
                            if (marco is Frame.Text) {
                                val sobre = runCatching {
                                    JsonRed.decodeFromString(SobreTiempoReal.serializer(), marco.readText())
                                }.getOrNull()
                                if (sobre != null) entrante.emit(sobre)
                            }
                        }
                    } finally {
                        mutex.withLock { sesionActual = null }
                    }
                }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: Throwable) {
                // Se reintenta tras la pausa de abajo: una caida de red es una
                // reconexion, nunca un fallo que deba propagarse a la Vista.
            }
            delay(RETARDO_RECONEXION_MS)
        }
    }

    private suspend fun enviarSolicitud(sesion: DefaultClientWebSocketSession, solicitud: SolicitudDeCanal) {
        runCatching { sesion.send(JsonRed.encodeToString(SolicitudDeCanal.serializer(), solicitud)) }
    }

    private companion object {
        const val TAMANO_MEMORIA_INTERMEDIA = 64
        const val RETARDO_RECONEXION_MS = 3_000L
    }
}
