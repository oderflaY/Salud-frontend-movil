package com.eter.salud.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud

/**
 * Burbuja de un mensaje de chat, compartida por los dos portales.
 *
 * [esPropio] es lo unico que cambia entre el lado del paciente y el del medico:
 * el mensaje de quien mira la pantalla va a la derecha sobre el azul
 * institucional, y el de la otra parte a la izquierda sobre el gris de
 * conversacion. Asi la misma pieza sirve en ambos sin duplicar estilos.
 *
 * La hora llega ya convertida a hora local por el ViewModel: este componente no
 * sabe de zonas horarias ni de formatos.
 */
@Composable
fun BurbujaMensaje(
    texto: String,
    horaLocal: String,
    esPropio: Boolean,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val colorFondo = if (esPropio) {
        MaterialTheme.colorScheme.primary
    } else {
        colores.fondoBurbujaMedico
    }
    val colorTexto = if (esPropio) {
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
                .widthIn(max = ANCHO_MAXIMO_BURBUJA)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcionAccesible
                },
            color = colorFondo,
            shape = RoundedCornerShape(espaciado.medio),
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

private val ANCHO_MAXIMO_BURBUJA = 280.dp
private const val ALFA_MARCA_DE_TIEMPO = 0.7f
