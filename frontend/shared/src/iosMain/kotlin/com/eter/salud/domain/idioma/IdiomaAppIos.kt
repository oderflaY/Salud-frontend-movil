package com.eter.salud.domain.idioma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

/**
 * Idioma de la app sobre `AppleLanguages`.
 *
 * iOS resuelve los recursos leyendo esa lista de las preferencias del
 * contenedor. Escribirla cambia el idioma, pero SOLO surte efecto en el
 * siguiente arranque: el sistema fija el paquete de recursos al lanzar el
 * proceso y no admite recargarlo en caliente.
 *
 * Por eso la pantalla de ajustes avisa de que el cambio se aplica al reabrir.
 * Forzar un reinicio desde la app esta prohibido por las normas de la App Store
 * (guideline 2.5.4), asi que la alternativa seria mentir sobre lo que ocurre.
 */
private class SelectorDeIdiomaIos : SelectorDeIdioma {

    private val preferencias = NSUserDefaults.standardUserDefaults

    override val actual: IdiomaApp
        get() {
            val guardadas = preferencias.stringArrayForKey(CLAVE_IDIOMAS) ?: return IdiomaApp.SISTEMA
            val primero = guardadas.firstOrNull() as? String ?: return IdiomaApp.SISTEMA
            return IdiomaApp.entries.firstOrNull {
                it.etiquetaBcp47.isNotEmpty() && primero.startsWith(it.etiquetaBcp47)
            } ?: IdiomaApp.SISTEMA
        }

    override fun cambiar(idioma: IdiomaApp) {
        if (idioma == IdiomaApp.SISTEMA) {
            preferencias.removeObjectForKey(CLAVE_IDIOMAS)
        } else {
            preferencias.setObject(listOf(idioma.etiquetaBcp47), CLAVE_IDIOMAS)
        }
        preferencias.synchronize()
    }

    private companion object {
        const val CLAVE_IDIOMAS = "AppleLanguages"
    }
}

@Composable
actual fun recordarSelectorDeIdioma(): SelectorDeIdioma = remember { SelectorDeIdiomaIos() }
