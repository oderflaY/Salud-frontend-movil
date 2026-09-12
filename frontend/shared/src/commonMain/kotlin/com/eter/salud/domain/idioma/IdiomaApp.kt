package com.eter.salud.domain.idioma

import androidx.compose.runtime.Composable

/**
 * Idiomas que la app ofrece.
 *
 * [SISTEMA] es el valor por defecto y no una opcion mas: la mayoria de la gente
 * quiere que la app hable como su telefono, y forzar una eleccion en la primera
 * ejecucion seria una pregunta que nadie pidio.
 */
enum class IdiomaApp(val etiquetaBcp47: String) {
    SISTEMA(""),
    ESPANOL("es"),
    INGLES("en"),
}

/**
 * Aplica el idioma elegido a toda la app.
 *
 * Es `expect` porque el mecanismo no tiene equivalente comun: Android tiene
 * preferencias de idioma POR aplicacion desde API 33 (y configuracion de la
 * Activity por debajo), y iOS lo resuelve con la lista `AppleLanguages` del
 * contenedor. Compose Resources 1.11 no permite forzar la region desde codigo
 * comun -- su `ComposeEnvironment` es interno -- asi que se delega al sistema,
 * que ademas es lo correcto: el ajuste queda tambien en los Ajustes del
 * telefono, donde el usuario espera encontrarlo.
 */
@Composable
expect fun recordarSelectorDeIdioma(): SelectorDeIdioma

/** Lee y cambia el idioma de la aplicacion. */
interface SelectorDeIdioma {

    /** Idioma activo. [IdiomaApp.SISTEMA] si nunca se forzo ninguno. */
    val actual: IdiomaApp

    /**
     * Cambia el idioma.
     *
     * En Android por debajo de API 33 esto RECREA la Activity, que es la unica
     * forma de que los recursos se vuelvan a resolver. Se avisa en la pantalla
     * de ajustes en vez de dejar que parpadee sin explicacion.
     */
    fun cambiar(idioma: IdiomaApp)
}
