package com.eter.salud.data.red

import com.eter.salud.data.sesion.AvisoDeSesion
import com.eter.salud.data.sesion.FuenteDeSesion
import com.eter.salud.data.sesion.TipoAvisoDeSesion
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * El motor HTTP real de cada plataforma (OkHttp en Android, `NSURLSession` a
 * traves de Darwin en iOS). Es `expect` porque Ktor no trae un motor comun:
 * cada sistema operativo ya tiene su propia pila de red y usarla es mas barato
 * -- en bateria y en tamano del binario -- que empaquetar una implementacion
 * propia (como haria el motor `CIO`, pensado para JVM puro).
 */
expect fun crearMotorHttp(): HttpClientEngineFactory<*>

/**
 * Configuracion de serializacion unica del cliente HTTP.
 *
 * Mismas reglas que [com.eter.salud.domain.model.PacienteJson]: el backend en
 * Go recibe solo lo que existe (`explicitNulls = false`), las listas vacias
 * viajan igual (`encodeDefaults = true`) para distinguir "sin datos" de "no
 * preguntado", y un nodo nuevo del backend no rompe una app ya instalada
 * (`ignoreUnknownKeys = true`).
 *
 * `coerceInputValues` extiende esa misma regla a los valores: un `null` en un
 * campo con valor por defecto toma el defecto, y un valor de enum que esta
 * version no conoce (una especialidad agregada despues) cae al defecto en vez
 * de tumbar la respuesta completa. Sin esto, un solo medico recien registrado
 * -- universidad y especialidad en null -- vaciaba el directorio y la bandeja
 * de todos sus pacientes.
 */
val JsonRed: Json = Json {
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    prettyPrint = false
}

/**
 * Cliente HTTP compartido por todos los repositorios `*Remoto`.
 *
 * ## Por que el token se lee en cada peticion y no se cachea una vez
 *
 * El complemento `Auth` de Ktor cachea el token la primera vez que lo pide y
 * solo lo vuelve a pedir tras un 401. Con una sola instancia de [HttpClient]
 * por proceso (igual que [com.eter.salud.data.local.ContenedorSalud]), eso
 * significaria que cerrar sesion y entrar con otra cuenta seguiria mandando el
 * token de la cuenta anterior hasta el primer rechazo. Un interceptor propio
 * que relee [FuenteDeSesion.sesion] en cada peticion no tiene ese hueco: la
 * sesion vigente es siempre la que se manda.
 */
fun crearClienteHttp(
    fuenteDeSesion: FuenteDeSesion,
    engine: HttpClientEngineFactory<*> = crearMotorHttp(),
    almacenSinConexion: AlmacenDeRespuestas? = null,
    alRechazarSesion: (AvisoDeSesion) -> Unit = {},
): HttpClient = HttpClient(engine) {
    configurarPluginsRed(fuenteDeSesion, alRechazarSesion, almacenSinConexion)
}

/**
 * Los complementos del cliente, separados de [crearClienteHttp] para que las
 * pruebas puedan montar el mismo cliente sobre un motor falso
 * (`MockEngine`) sin duplicar la configuracion real.
 */
fun HttpClientConfig<*>.configurarPluginsRed(
    fuenteDeSesion: FuenteDeSesion,
    alRechazarSesion: (AvisoDeSesion) -> Unit = {},
    almacenSinConexion: AlmacenDeRespuestas? = null,
    monitorDeConexion: MonitorDeConexion = EstadoDeConexion,
) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(JsonRed)
    }
    install(WebSockets)
    install(HttpTimeout) {
        requestTimeoutMillis = TIEMPO_LIMITE_MS
        // Si el servidor no esta, se sabe pronto: con 15 s cada pantalla se
        // quedaba cargando un cuarto de minuto antes de decir "sin conexion".
        connectTimeoutMillis = TIEMPO_LIMITE_CONEXION_MS
    }
    install(RespaldoSinConexion) {
        almacen = almacenSinConexion
        monitor = monitorDeConexion
        identidad = {
            val sesion = fuenteDeSesion.sesion.first()
            sesion.paciente?.idPaciente ?: sesion.profesional?.idMedico
        }
    }
    install(ServidorActual)
    if (ConfiguracionApi.REGISTRAR_PETICIONES) {
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    install(AutorizacionDeSesion) {
        this.fuenteDeSesion = fuenteDeSesion
    }
    install(VigilanciaDeSesion) {
        this.alRechazarSesion = alRechazarSesion
    }
}

private class ConfiguracionAutorizacion {
    var fuenteDeSesion: FuenteDeSesion? = null
}

/**
 * Adjunta `Authorization: Bearer <token>` con la sesion vigente, paciente o
 * profesional. Sin sesion abierta no adjunta nada: los dos endpoints de
 * acceso y la consulta de emergencia por tarjeta RFID no lo necesitan, y
 * mandar un header vacio no aporta nada que el backend deba validar.
 */
private val AutorizacionDeSesion = createClientPlugin("AutorizacionDeSesion", ::ConfiguracionAutorizacion) {
    val fuenteDeSesion = requireNotNull(pluginConfig.fuenteDeSesion) {
        "AutorizacionDeSesion requiere una FuenteDeSesion"
    }
    onRequest { request, _ ->
        val sesion = fuenteDeSesion.sesion.first()
        val token = sesion.paciente?.token ?: sesion.profesional?.token
        if (!token.isNullOrBlank()) {
            request.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

private class ConfiguracionVigilancia {
    var alRechazarSesion: (AvisoDeSesion) -> Unit = {}
}

/**
 * Detecta, en cualquier respuesta, que el backend rechazo la SESION (y no
 * solo esa peticion) y lo avisa una vez, en un solo sitio, en lugar de que
 * cada repositorio lo traduzca a "sin conexion".
 *
 * Mira el motivo y no solo el codigo: `NO_AUTORIZADO` (401) tambien sale de
 * reglas normales -- abrir una conversacion ajena, por ejemplo -- y eso no debe
 * sacar a nadie de la app. Leer el cuerpo aqui no se lo quita al repositorio:
 * Ktor 3 guarda la respuesta y deja leerla otra vez.
 */
private val VigilanciaDeSesion = createClientPlugin("VigilanciaDeSesion", ::ConfiguracionVigilancia) {
    val avisar = pluginConfig.alRechazarSesion
    onResponse { respuesta ->
        val codigo = respuesta.status
        if (codigo != HttpStatusCode.Unauthorized && codigo != HttpStatusCode.Forbidden) return@onResponse
        val token = respuesta.request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }
            ?: return@onResponse
        val tipo = when (runCatching { respuesta.body<CuerpoError>().motivo }.getOrNull()) {
            "SESION_REVOCADA", "CUENTA_INACTIVA" -> TipoAvisoDeSesion.CERRADA
            "CAMBIO_CONTRASENA_REQUERIDO" -> TipoAvisoDeSesion.CAMBIO_DE_CONTRASENA_REQUERIDO
            else -> return@onResponse
        }
        avisar(AvisoDeSesion(tipo, token))
    }
}

/**
 * En desarrollo, lleva cada peticion al servidor vigente aunque el repositorio
 * se haya creado con la IP anterior, y si una peticion no llega al servidor
 * pide a [VigiaDeServidor] que lo busque (quiza cambio de IP).
 *
 * Solo reescribe destinos de IP privada: en produccion (con dominio y HTTPS) y
 * en las pruebas no hace nada.
 */
private val ServidorActual = createClientPlugin("ServidorActual") {
    onRequest { peticion, _ ->
        if (!ConfiguracionApi.DESCUBRIR_EN_RED) return@onRequest
        val vigente = runCatching { Url(ConfiguracionApi.BASE_URL) }.getOrNull() ?: return@onRequest
        val destino = "${peticion.url.protocol.name}://${peticion.url.host}:${peticion.url.port}"
        if (peticion.url.host != vigente.host && DescubridorDeServidor.subredDe(destino) != null) {
            peticion.url.host = vigente.host
            peticion.url.port = vigente.port
        }
    }
    on(Send) { peticion ->
        try {
            proceed(peticion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            VigiaDeServidor.reportarFallo()
            throw e
        }
    }
}

private const val TIEMPO_LIMITE_MS = 15_000L
private const val TIEMPO_LIMITE_CONEXION_MS = 6_000L
