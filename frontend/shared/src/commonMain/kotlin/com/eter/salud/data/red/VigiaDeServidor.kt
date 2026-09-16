package com.eter.salud.data.red

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Mantiene [ConfiguracionApi.BASE_URL] apuntando al backend aunque cambie la IP
 * de la computadora que lo corre. Solo actúa en builds de desarrollo
 * ([ConfiguracionApi.DESCUBRIR_EN_RED]) y con URLs de IP privada.
 *
 *  - Al arrancar, [asegurar] confirma la dirección (o busca otra) antes de
 *    pintar la app.
 *  - Si con la app abierta una petición falla por red, [reportarFallo] lanza una
 *    búsqueda en segundo plano; las siguientes peticiones ya salen a la
 *    dirección nueva (ver el complemento `ServidorActual` del cliente HTTP).
 */
object VigiaDeServidor {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val candado = Mutex()
    private var ultimaBusqueda: TimeSource.Monotonic.ValueTimeMark? = null
    private val ESPERA_ENTRE_BUSQUEDAS = 15.seconds

    val habilitado: Boolean
        get() = ConfiguracionApi.DESCUBRIR_EN_RED &&
            DescubridorDeServidor.subredDe(ConfiguracionApi.BASE_URL) != null

    private val sonda by lazy {
        HttpClient(crearMotorHttp()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 700
                requestTimeoutMillis = 1_500
                socketTimeoutMillis = 1_500
            }
        }
    }

    private val descubridor by lazy { DescubridorDeServidor(::esServidorSalud) }

    /** Confirma el servidor actual o encuentra otro. Devuelve si hay servidor. */
    suspend fun asegurar(): Boolean {
        if (!habilitado) return true
        return candado.withLock {
            ultimaBusqueda = TimeSource.Monotonic.markNow()
            val conocidas = listOfNotNull(ConfiguracionApi.BASE_URL, ConfiguracionApi.URL_COMPILADA)
            val encontrado = descubridor.encontrar(conocidas) ?: return@withLock false
            if (encontrado != ConfiguracionApi.BASE_URL) {
                ConfiguracionApi.BASE_URL = encontrado
                ConfiguracionApi.alCambiarServidor(encontrado)
            }
            true
        }
    }

    /**
     * Si el backend contesta ahora. En desarrollo tambien lo busca en la red
     * local por si cambio de IP; en produccion solo pregunta a la URL fija.
     */
    suspend fun servidorResponde(): Boolean =
        if (habilitado) asegurar() else esServidorSalud(ConfiguracionApi.BASE_URL)

    /** Una petición no llegó al servidor: quizá cambió de IP. */
    fun reportarFallo() {
        if (!habilitado || candado.isLocked) return
        val ultima = ultimaBusqueda
        if (ultima != null && ultima.elapsedNow() < ESPERA_ENTRE_BUSQUEDAS) return
        ultimaBusqueda = TimeSource.Monotonic.markNow()
        alcance.launch { asegurar() }
    }

    /**
     * `/healthz` responde "ok" en muchos servidores; `/api/v1/auth/me` sin token
     * con `NO_AUTORIZADO` solo lo responde el backend de Salud.
     */
    private suspend fun esServidorSalud(base: String): Boolean = runCatching {
        val salud = sonda.get("$base/healthz")
        if (salud.status.value != 200 || salud.bodyAsText().trim() != "ok") return@runCatching false
        val identidad = sonda.get("$base/api/v1/auth/me")
        identidad.status.value == 401 && identidad.bodyAsText().contains("NO_AUTORIZADO")
    }.getOrDefault(false)
}
