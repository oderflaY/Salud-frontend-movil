package com.eter.salud.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.SaludTheme

/**
 * Muestra de referencia: como se consume el tema "Biotech Premium" sin nombrar
 * un solo color.
 *
 * No la usa ninguna pantalla; existe para las dos vistas previas de abajo y
 * como plantilla. En produccion, una tarjeta se escribe con [TarjetaSalud], que
 * hace exactamente esto por dentro.
 *
 * Todo sale de [LocalColoresSalud]: cuando cambia el modo (el sistema o el
 * interruptor de Configuracion), [SaludTheme] provee el otro juego de roles y
 * esta tarjeta se recompone con el, sin un solo `if (oscuro)`.
 */
@Composable
internal fun TarjetaMuestraTema(
    nombre: String,
    detalle: String,
    estado: String,
    critico: Boolean,
    modifier: Modifier = Modifier,
) {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    val senal = if (critico) colores.senalCritico else colores.senalEstable

    Surface(
        modifier = modifier.fillMaxWidth(),
        // White en claro, Graphite en oscuro.
        color = colores.fondoTarjeta,
        // Todo `Text` sin color propio hereda Slate / IceWhite.
        contentColor = colores.textoPrincipal,
        shape = FormaSalud.grande,
        border = BorderStroke(1.dp, colores.separador),
        // Se despega por tono, nunca por sombra: sobre negro OLED no se ve.
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(espaciado.amplio),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            PuntoLuminoso(
                color = senal,
                // El aro toma el color de la superficie en la que se posa.
                colorAro = colores.fondoTarjeta,
                diametro = 14.dp,
                alcance = 3.4f,
                intensidad = 0.55f,
                late = critico,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(espaciado.minimo),
            ) {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
            }
            Text(
                text = estado,
                style = MaterialTheme.typography.labelMedium,
                color = colores.acentoAccion,
                modifier = Modifier
                    .background(colores.acentoSuave, FormaSalud.pastilla)
                    .padding(horizontal = espaciado.compacto, vertical = espaciado.minimo),
            )
        }
    }
}

@Composable
private fun MuestraSobreFondo(modoOscuro: Boolean) {
    SaludTheme(modoOscuro = modoOscuro) {
        val colores = LocalColoresSalud.current
        Column(
            modifier = Modifier
                .background(colores.fondo)
                .padding(LocalEspaciadoSalud.current.medio),
            verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.compacto),
        ) {
            TarjetaMuestraTema("Rosa Martinez", "Presion 168/102 esta manana", "Revisar", critico = true)
            TarjetaMuestraTema("Jorge Ibarra", "Tomo 4 de 4 dosis", "Al dia", critico = false)
        }
    }
}

@Preview
@Composable
private fun MuestraTemaClaro() = MuestraSobreFondo(modoOscuro = false)

@Preview
@Composable
private fun MuestraTemaOscuroOled() = MuestraSobreFondo(modoOscuro = true)
