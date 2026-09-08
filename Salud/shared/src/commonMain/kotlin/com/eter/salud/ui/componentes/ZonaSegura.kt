package com.eter.salud.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.eter.salud.ui.theme.LocalEspaciadoSalud

/**
 * Barra de acciones anclada al fondo de la pantalla.
 *
 * Corrige el problema de ergonomia detectado en la iteracion anterior: los
 * botones quedaban pegados al borde inferior y colisionaban con la barra de
 * gestos de Android y con el Home Indicator de iOS, provocando toques
 * accidentales en un flujo donde un toque equivocado registra un dato clinico.
 *
 * El margen no es un valor fijo: [WindowInsets.safeDrawing] restringido al lado
 * inferior devuelve lo que el sistema reserva en cada dispositivo, y se le suma
 * un respiro propio. [imePadding] eleva ademas la barra cuando el teclado esta
 * abierto, para que el boton nunca quede debajo de el.
 */
@Composable
fun BarraAccionInferior(
    modifier: Modifier = Modifier,
    respiroInferior: Dp = LocalEspaciadoSalud.current.amplio,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                )
                .padding(
                    start = espaciado.amplio,
                    end = espaciado.amplio,
                    top = espaciado.medio,
                    bottom = respiroInferior,
                ),
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
            content = contenido,
        )
    }
}

/**
 * Margen inferior de seguridad para contenido que scrollea hasta el fondo sin
 * una barra fija encima (pantallas de acceso, hojas modales).
 */
@Composable
fun Modifier.margenInferiorSeguro(respiro: Dp = LocalEspaciadoSalud.current.generoso): Modifier =
    this
        .imePadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .padding(bottom = respiro)
