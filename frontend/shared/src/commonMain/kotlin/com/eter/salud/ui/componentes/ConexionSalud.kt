package com.eter.salud.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.eter.salud.data.red.EstadoDeConexion
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.conexion_sin_red

/**
 * Corre [bloque] solo mientras la pantalla esta a la vista: se cancela al
 * salir de ella o al mandar la app a segundo plano, y vuelve a empezar al
 * regresar. Para mantener algo al dia sin gastar bateria ni datos a escondidas.
 */
@Composable
fun MientrasSeVe(clave: Any?, bloque: suspend () -> Unit) {
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val actual by rememberUpdatedState(bloque)
    LaunchedEffect(clave, ciclo) {
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { actual() }
    }
}

/** Ejecuta [accion] cada vez que vuelve la conexion con el servidor. */
@Composable
fun AlReconectar(accion: () -> Unit) {
    val actual by rememberUpdatedState(accion)
    LaunchedEffect(Unit) {
        EstadoDeConexion.reconexiones.collect { actual() }
    }
}

/**
 * Franja que avisa que no hay conexion y que lo que se ve es lo ultimo
 * guardado en el telefono. Desaparece sola al volver la red.
 */
@Composable
fun AvisoSinConexion(modifier: Modifier = Modifier) {
    val enLinea by EstadoDeConexion.enLinea.collectAsStateWithLifecycle()
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    AnimatedVisibility(visible = !enLinea, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        Surface(color = colores.fondoAdvertencia, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.conexion_sin_red),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoAdvertencia,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = espaciado.amplio, vertical = espaciado.compacto)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
