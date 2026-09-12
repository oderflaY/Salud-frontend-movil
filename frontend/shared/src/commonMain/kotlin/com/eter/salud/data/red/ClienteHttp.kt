package com.eter.salud.data.red

import com.eter.salud.data.sesion.FuenteDeSesion
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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
 */
val JsonRed: Json = Json {
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
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
): HttpClient = HttpClient(engine) {
    configurarPluginsRed(fuenteDeSesion)
}

/**
 * Los complementos del cliente, separados de [crearClienteHttp] para que las
 * pruebas puedan montar el mismo cliente sobre un motor falso
 * (`MockEngine`) sin duplicar la configuracion real.
 */
fun HttpClientConfig<*>.configurarPluginsRed(fuenteDeSesion: FuenteDeSesion) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(JsonRed)
    }
    install(WebSockets)
    install(HttpTimeout) {
        requestTimeoutMillis = TIEMPO_LIMITE_MS
        connectTimeoutMillis = TIEMPO_LIMITE_MS
    }
    install(Logging) {
        level = LogLevel.INFO
    }
    install(AutorizacionDeSesion) {
        this.fuenteDeSesion = fuenteDeSesion
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

private const val TIEMPO_LIMITE_MS = 15_000L
