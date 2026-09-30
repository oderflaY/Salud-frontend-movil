package com.eter.salud.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.eter.salud.presentation.comun.EstadoTraduccion
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_chat_accion_traducir
import salud.shared.generated.resources.chat_accion_reintentar_traduccion
import salud.shared.generated.resources.chat_accion_traducir
import salud.shared.generated.resources.chat_traduccion_fallida
import salud.shared.generated.resources.chat_traduccion_sin_cambios
import salud.shared.generated.resources.chat_traduciendo

/**
 * "Traducir" junto al mensaje, y lo que pasa despues.
 *
 * No traduce sola: una traduccion automatica de un sintoma puede cambiarle el
 * matiz, asi que la pide quien la quiere leer. Mientras llega se dice que esta
 * trabajando -- son unos segundos, no un parpadeo -- y si falla se puede
 * reintentar sin perder nada: el mensaje sigue ahi tal como se escribio.
 *
 * Lo usan los dos chats (paciente y medico), que traducen en direcciones
 * opuestas pero muestran exactamente lo mismo.
 */
@Composable
fun AccionTraducir(
    estado: EstadoTraduccion?,
    colorTexto: Color,
    alTraducir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    if (estado == EstadoTraduccion.Cargando) {
        Text(
            text = stringResource(Res.string.chat_traduciendo),
            style = MaterialTheme.typography.labelSmall,
            color = colorTexto.copy(alpha = ALFA_TEXTO_DE_APOYO),
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }

    if (estado == EstadoTraduccion.SinCambios) {
        Text(
            text = stringResource(Res.string.chat_traduccion_sin_cambios),
            style = MaterialTheme.typography.labelSmall,
            color = colorTexto.copy(alpha = ALFA_TEXTO_DE_APOYO),
            modifier = modifier,
        )
        return
    }

    val fallo = estado == EstadoTraduccion.Fallida
    val etiqueta = stringResource(
        if (fallo) Res.string.chat_accion_reintentar_traduccion else Res.string.chat_accion_traducir,
    )
    val descripcion = stringResource(Res.string.a11y_chat_accion_traducir)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(espaciado.minimo)) {
        if (fallo) {
            Text(
                text = stringResource(Res.string.chat_traduccion_fallida),
                style = MaterialTheme.typography.labelSmall,
                color = colorTexto.copy(alpha = ALFA_TEXTO_DE_APOYO),
            )
        }
        TextButton(
            onClick = alTraducir,
            contentPadding = PaddingValues(horizontal = espaciado.compacto, vertical = espaciado.minimo),
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcion },
        ) {
            Text(text = etiqueta, style = MaterialTheme.typography.labelMedium, color = colorTexto)
        }
    }
}

/** Mismo peso visual que el pie de la burbuja traducida: es informacion de apoyo. */
private const val ALFA_TEXTO_DE_APOYO = 0.7f
