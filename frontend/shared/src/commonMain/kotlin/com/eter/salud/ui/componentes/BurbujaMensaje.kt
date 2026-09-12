package com.eter.salud.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud

/**
 * Burbuja de un mensaje de chat, compartida por los dos portales.
 *
 * Dos ejes independientes, y conviene no confundirlos:
 *
 *  - **[esPropio] decide el LADO.** El mensaje de quien mira la pantalla va a la
 *    derecha. Es la convencion universal de mensajeria y no se toca.
 *  - **[esDelMedico] decide el COLOR.** El rediseno pide que la voz clinica se
 *    reconozca por si sola: la del medico va siempre sobre el color de marca y
 *    la del paciente sobre el gris de captura, mire quien mire la pantalla.
 *
 * El efecto es que el paciente ve rellena la burbuja del medico y no la suya
 * propia, al reves que en una app de mensajeria comun. Es deliberado -- aqui lo
 * que importa destacar es la indicacion medica, no quien habla -- pero conviene
 * saberlo: rompe una costumbre muy asentada.
 *
 * La hora llega ya convertida a hora local por el ViewModel: este componente no
 * sabe de zonas horarias ni de formatos.
 */
@Composable
fun BurbujaMensaje(
    texto: String,
    horaLocal: String,
    esPropio: Boolean,
    esDelMedico: Boolean,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val colorFondo = if (esDelMedico) {
        MaterialTheme.colorScheme.primary
    } else {
        colores.fondoBurbujaMedico
    }
    val colorTexto = if (esDelMedico) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        colores.sobreBurbujaMedico
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (esPropio) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = MedidaSalud.anchoBurbuja)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcionAccesible
                },
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
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorTexto,
                )
                if (horaLocal.isNotBlank()) {
                    Text(
                        text = horaLocal,
                        style = MaterialTheme.typography.labelSmall,
                        // Atenuado sobre el propio fondo de la burbuja: la hora
                        // acompana, no compite con el contenido clinico.
                        color = colorTexto.copy(alpha = ALFA_MARCA_DE_TIEMPO),
                        textAlign = if (esPropio) TextAlign.End else TextAlign.Start,
                    )
                }
            }
        }
    }
}

private const val ALFA_MARCA_DE_TIEMPO = 0.7f
