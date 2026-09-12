package com.eter.salud.ui.plataforma

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ManejadorDeRetroceso(habilitado: Boolean, alRetroceder: () -> Unit) {
    BackHandler(enabled = habilitado, onBack = alRetroceder)
}

@Composable
actual fun recordarMinimizadorDeApp(): () -> Unit {
    val contexto = LocalContext.current
    val actividad = remember(contexto) { contexto.actividadContenedora() }
    return remember(actividad) {
        {
            // `moveTaskToBack(true)` es lo que hace el boton de inicio: la app
            // queda viva en segundo plano con su estado intacto. `finish()`
            // destruiria el proceso y con el la sesion.
            actividad?.moveTaskToBack(true)
            Unit
        }
    }
}

/**
 * Desenvuelve la Activity del `Context` de Compose.
 *
 * El `LocalContext` puede ser un `ContextWrapper` (el de tema, por ejemplo) en
 * vez de la Activity directa, asi que se recorre la cadena. Devuelve nulo si no
 * hay ninguna -- en una vista previa, por ejemplo -- y el minimizador no hace
 * nada, que es preferible a reventar.
 */
private fun Context.actividadContenedora(): Activity? {
    var actual: Context? = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}
