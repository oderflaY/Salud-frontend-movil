package com.eter.salud.ui.agenda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringResource

/**
 * Semaforo de una cita: punto de color, nombre del estado y fondo lavado.
 *
 * Es la unica pieza autorizada a pintar el estado de una cita, y existe para que
 * el semaforo no se reinvente en cada pantalla: antes el calendario y la hoja de
 * detalle tenian cada uno su propio recuadro, con paddings distintos, y bastaba
 * tocar uno para que dejaran de parecer el mismo sistema.
 *
 * ## Por que el estado se dice tres veces
 *
 * Punto, color de fondo y PALABRA. Parece redundante y es deliberado: el codigo
 * de colores es inutil para quien no distingue el ambar del verde, y en una
 * agenda clinica ahi se juega si el medico ve que un paciente no asistio. La
 * palabra es el canal que nunca falla; el color solo acelera la lectura de quien
 * si lo percibe.
 *
 * Por eso el contenido se marca con [clearAndSetSemantics] vacio y la etiqueta
 * accesible la pone quien lo contiene: TalkBack ya lee el estado dentro de la
 * frase de la cita completa ("Juan Perez, 09:00, Pendiente"), y anunciarlo
 * ademas suelto lo repetiria dos veces seguidas.
 *
 * El color se anima porque el estado CAMBIA en vivo bajo el dedo del medico: al
 * confirmar una cita, un salto seco de ambar a verde se lee como un parpadeo,
 * no como una consecuencia de lo que acaba de pulsar.
 */
@Composable
fun InsigniaEstado(
    estado: EstadoCita,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val (tintaObjetivo, fondoObjetivo) = colores.parDeEstado(estado)

    val tinta by animateColorAsState(
        targetValue = tintaObjetivo,
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "tintaEstado",
    )
    val fondo by animateColorAsState(
        targetValue = fondoObjetivo,
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "fondoEstado",
    )

    Surface(
        modifier = modifier.clearAndSetSemantics { },
        color = fondo,
        shape = FormaSalud.pastilla,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = espaciado.compacto + espaciado.minimo,
                vertical = espaciado.compacto,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(MedidaSalud.punto).background(tinta, CircleShape))
            Spacer(Modifier.width(espaciado.compacto))
            Text(
                text = stringResource(estado.recurso()),
                style = MaterialTheme.typography.labelMedium,
                color = tinta,
            )
        }
    }
}

