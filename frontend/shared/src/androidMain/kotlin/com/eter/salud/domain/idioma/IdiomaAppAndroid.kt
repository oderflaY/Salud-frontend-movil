package com.eter.salud.domain.idioma

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Idioma de la app sobre las preferencias por aplicacion de Android.
 *
 * Desde API 33 el sistema guarda un idioma POR app y lo expone en sus propios
 * Ajustes. Usarlo en vez de una preferencia propia tiene dos ventajas: el
 * usuario puede cambiarlo tambien desde fuera de la app, y el sistema recrea la
 * Activity para que los recursos se resuelvan de nuevo.
 *
 * Por debajo de API 33 ese mecanismo no existe y el cambio no se aplica: se
 * devuelve [IdiomaApp.SISTEMA] y `cambiar` no hace nada. Es deliberado --
 * manipular la configuracion de la Activity a mano funciona a medias y deja la
 * app inconsistente tras una rotacion o un cambio de tema. Ahi la app sigue el
 * idioma del telefono, que es correcto, solo que menos flexible.
 */
private class SelectorDeIdiomaAndroid(private val contexto: Context) : SelectorDeIdioma {

    override val actual: IdiomaApp
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return IdiomaApp.SISTEMA
            val gestor = contexto.getSystemService(LocaleManager::class.java)
                ?: return IdiomaApp.SISTEMA
            val etiqueta = gestor.applicationLocales.toLanguageTags()
            return IdiomaApp.entries.firstOrNull {
                it.etiquetaBcp47.isNotEmpty() && etiqueta.startsWith(it.etiquetaBcp47)
            } ?: IdiomaApp.SISTEMA
        }

    override fun cambiar(idioma: IdiomaApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val gestor = contexto.getSystemService(LocaleManager::class.java) ?: return
        gestor.applicationLocales = if (idioma == IdiomaApp.SISTEMA) {
            // Lista vacia significa "vuelve a seguir al telefono".
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(idioma.etiquetaBcp47)
        }
    }
}

@Composable
actual fun recordarSelectorDeIdioma(): SelectorDeIdioma {
    val contexto = LocalContext.current
    return remember(contexto) { SelectorDeIdiomaAndroid(contexto.applicationContext) }
}
