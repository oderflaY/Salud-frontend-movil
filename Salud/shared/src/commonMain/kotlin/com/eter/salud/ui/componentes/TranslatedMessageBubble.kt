package com.eter.salud.ui.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_ia_traduccion_accion_ver_original
import salud.shared.generated.resources.a11y_ia_traduccion_accion_ver_traduccion
import salud.shared.generated.resources.a11y_ia_traduccion_burbuja_original
import salud.shared.generated.resources.a11y_ia_traduccion_burbuja_traducida
import salud.shared.generated.resources.ia_traduccion_accion_ver_original
import salud.shared.generated.resources.ia_traduccion_accion_ver_traduccion
import salud.shared.generated.resources.ia_traduccion_pie

/**
 * Estado de una burbuja con traduccion automatica.
 *
 * No modela un ciclo de carga en red -- la traduccion llega resuelta desde el
 * ViewModel -- sino QUE VERSION del mensaje se le esta mostrando al usuario en
 * este instante. Ese es el eje real del componente: alternar entre lo que se
 * lee (siempre en el idioma propio) y lo que se escribio (el idioma original,
 * para quien quiera verificarlo).
 */
sealed interface EstadoBurbujaTraducida {

    /** La traduccion aun no esta lista: la burbuja no tiene nada que mostrar. */
    data object Cargando : EstadoBurbujaTraducida

    /** Mostrando la version traducida (el estado por defecto). */
    data class Traducido(val mensaje: MensajeTraducido) : EstadoBurbujaTraducida

    /** El usuario pidio ver el mensaje tal como lo escribio su autor. */
    data class Original(val mensaje: MensajeTraducido) : EstadoBurbujaTraducida
}

/**
 * Las dos versiones de un mismo mensaje, mas el idioma en el que se escribio
 * originalmente (para el pie "Traducido de [idioma]").
 */
data class MensajeTraducido(
    val textoTraducido: String,
    val textoOriginal: String,
    val idiomaOriginal: String,
)

/**
 * Burbuja de chat con traduccion automatica.
 *
 * Comparte el trazado de [BurbujaMensaje] (lado por [esPropio], color por
 * [esDelMedico], tope de ancho de [MedidaSalud.anchoBurbuja]) y le anade lo que
 * aquella no necesita: el pie "Traducido de [idioma]" y el cruce entre las dos
 * versiones del texto.
 *
 * El toggle lo decide quien llama ([alAlternar]): este componente es tonto y no
 * guarda que version se esta viendo, solo la dibuja segun [estado].
 */
@Composable
fun TranslatedMessageBubble(
    estado: EstadoBurbujaTraducida,
    horaLocal: String,
    esPropio: Boolean,
    esDelMedico: Boolean,
    alAlternar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mensaje = when (estado) {
        is EstadoBurbujaTraducida.Traducido -> estado.mensaje
        is EstadoBurbujaTraducida.Original -> estado.mensaje
        EstadoBurbujaTraducida.Cargando -> null
    } ?: return
    val mostrandoOriginal = estado is EstadoBurbujaTraducida.Original

    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val colorFondo = if (esDelMedico) MaterialTheme.colorScheme.primary else colores.fondoBurbujaMedico
    val colorTexto = if (esDelMedico) MaterialTheme.colorScheme.onPrimary else colores.sobreBurbujaMedico

    val descripcion = if (mostrandoOriginal) {
        stringResource(
            Res.string.a11y_ia_traduccion_burbuja_original,
            mensaje.idiomaOriginal,
            mensaje.textoOriginal,
        )
    } else {
        stringResource(
            Res.string.a11y_ia_traduccion_burbuja_traducida,
            mensaje.idiomaOriginal,
            mensaje.textoTraducido,
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (esPropio) Arrangement.End else Arrangement.Start,
    ) {
        // Plana, sin sombra: la senal de "esto paso por traduccion" la da el
        // pie de texto, no un adorno de superficie.
        Surface(
            modifier = Modifier
                .widthIn(max = MedidaSalud.anchoBurbuja)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
            color = colorFondo,
            shape = FormaSalud.media,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = espaciado.medio,
                    vertical = espaciado.compacto,
                ),
                horizontalAlignment = if (esPropio) Alignment.End else Alignment.Start,
            ) {
                AnimatedContent(
                    targetState = mostrandoOriginal,
                    transitionSpec = {
                        // MEDIO: el contenido cambia de significado pero la
                        // pieza que lo contiene no se mueve de sitio.
                        val fundido = tween<Float>(MovimientoSalud.MEDIO)
                        fadeIn(fundido) togetherWith fadeOut(fundido)
                    },
                    label = "cruceTraduccion",
                ) { original ->
                    Text(
                        text = if (original) mensaje.textoOriginal else mensaje.textoTraducido,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorTexto,
                    )
                }

                if (horaLocal.isNotBlank()) {
                    Text(
                        text = horaLocal,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorTexto.copy(alpha = ALFA_MARCA_DE_TIEMPO),
                    )
                }

                PieDeTraduccion(
                    idioma = mensaje.idiomaOriginal,
                    mostrandoOriginal = mostrandoOriginal,
                    colorTexto = colorTexto,
                    alAlternar = alAlternar,
                )
            }
        }
    }
}

/** "Traducido de [idioma]" en `Ash`, mas la accion para alternar la version. */
@Composable
private fun PieDeTraduccion(
    idioma: String,
    mostrandoOriginal: Boolean,
    colorTexto: Color,
    alAlternar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val etiquetaAccion = if (mostrandoOriginal) {
        stringResource(Res.string.ia_traduccion_accion_ver_traduccion)
    } else {
        stringResource(Res.string.ia_traduccion_accion_ver_original)
    }
    val descripcionAccion = if (mostrandoOriginal) {
        stringResource(Res.string.a11y_ia_traduccion_accion_ver_traduccion)
    } else {
        stringResource(Res.string.a11y_ia_traduccion_accion_ver_original)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.minimo),
    ) {
        Text(
            text = stringResource(Res.string.ia_traduccion_pie, idioma),
            style = MaterialTheme.typography.labelSmall,
            color = colorTexto.copy(alpha = ALFA_PIE_TRADUCCION),
        )
        val fuenteDeInteraccion = remember { MutableInteractionSource() }
        TextButton(
            onClick = alAlternar,
            interactionSource = fuenteDeInteraccion,
            contentPadding = PaddingValues(
                horizontal = espaciado.compacto,
                vertical = espaciado.minimo,
            ),
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionAccion },
        ) {
            Text(
                text = etiquetaAccion,
                style = MaterialTheme.typography.labelSmall,
                color = colorTexto,
            )
        }
    }
}

private const val ALFA_MARCA_DE_TIEMPO = 0.7f
private const val ALFA_PIE_TRADUCCION = 0.6f
