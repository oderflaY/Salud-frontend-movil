package com.eter.salud.data.red

/**
 * Unico punto de configuracion del backend en Go.
 *
 * ## Por que es mutable y no una constante
 *
 * `BASE_URL` cambia entre desarrollo (el backend en la misma red local, o el
 * emulador apuntando a `localhost` de la maquina anfitriona) y produccion (el
 * dominio real, detras de TLS). Fijarla en piedra habria obligado a recompilar
 * la app para apuntar a otro entorno. Se deja como `var` para que un punto de
 * arranque (por ejemplo, una pantalla de ajustes de desarrollador, o un valor
 * inyectado por sabor de compilacion) la pueda fijar una sola vez, temprano.
 *
 * ## Por que arranca apagado
 *
 * [USAR_BACKEND_REMOTO] nace en `false`: hasta que exista un backend real que
 * cumpla `docs/CONTRATOS_BACKEND.md` y alguien fije [BASE_URL] a proposito, la
 * app se queda con los repositorios locales (SQLite) que ya tiene probados.
 * Ningun cambio de este archivo puede romper una build existente por accidente.
 */
object ConfiguracionApi {

    /**
     * Raiz de la API, sin barra final -- el proxy Caddy que reparte hacia
     * PostgREST y el servicio Axum (`mapeo-endpoints.md`, seccion Base), no
     * cada servicio por separado. En la maquina de desarrollo del backend es
     * el puerto `8000` (`PROXY_PORT` de su `.env`).
     *
     * En Android NO se usa este valor por defecto: `MainActivity` lo
     * sobreescribe con `BuildConfig.URL_BACKEND`, que sale de
     * `salud.baseUrl.debug` / `salud.baseUrl.release` en `local.properties`
     * (ver `androidApp/build.gradle.kts`). Fijar aqui la IP de una red local
     * concreta significaba que un build de release salia apuntando a la maquina
     * de un desarrollador.
     *
     * `10.0.2.2` es como el emulador de Android ve el `localhost` de la maquina
     * anfitriona; queda como valor de arranque para iOS y para las pruebas.
     */
    @kotlin.concurrent.Volatile
    var BASE_URL: String = "http://10.0.2.2:8000"

    /** La URL con la que se compiló la app: otra candidata si la IP cambió. */
    var URL_COMPILADA: String? = null

    /**
     * Solo en desarrollo: si el backend (en una computadora de la red local)
     * cambia de IP, la app lo busca en su subred en vez de quedarse sin conexión.
     * Ver [VigiaDeServidor].
     */
    var DESCUBRIR_EN_RED: Boolean = false

    /**
     * Si el cliente HTTP escribe cada peticion en el log. Solo en desarrollo:
     * las URLs llevan ids de pacientes y en release el log lo lee cualquier
     * app con permisos de depuracion.
     */
    var REGISTRAR_PETICIONES: Boolean = false

    /** Para recordar la dirección encontrada entre ejecuciones (lo conecta cada plataforma). */
    var alCambiarServidor: (String) -> Unit = {}

    /**
     * Raiz del socket de tiempo real (seccion 11 de `CONTRATOS_BACKEND.md`).
     * Se deriva de [BASE_URL] por defecto (mismo host, esquema `ws`/`wss`,
     * ruta `/realtime`), pero se puede fijar aparte si el backend expone el
     * socket en otro host o balanceador.
     */
    var WS_URL: String? = null

    /** Mientras sea falso, [com.eter.salud.data.local.ContenedorSalud] sigue local-first. */
    var USAR_BACKEND_REMOTO: Boolean = true

    /** URL del socket de tiempo real, derivada de [BASE_URL] si no se fijo [WS_URL]. */
    fun urlTiempoReal(): String = WS_URL ?: BASE_URL
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://") + "/realtime"
}
