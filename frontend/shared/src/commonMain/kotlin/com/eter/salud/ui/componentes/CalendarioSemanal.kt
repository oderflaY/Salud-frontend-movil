package com.eter.salud.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoDia
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_semana_dia_completo
import salud.shared.generated.resources.a11y_semana_dia_en_curso
import salud.shared.generated.resources.a11y_semana_dia_incompleto
import salud.shared.generated.resources.a11y_semana_dia_seleccionado
import salud.shared.generated.resources.a11y_semana_dia_sin_tomas
import salud.shared.generated.resources.opciones_iniciales_dias

/**
 * Franja horizontal de los siete dias de la semana, con el cumplimiento de cada
 * uno en un punto de color.
 *
 * ## Por que una franja y no un calendario de mes
 *
 * Una rejilla mensual ocupa media pantalla de movil para mostrar treinta dias de
 * los que al paciente solo le importan los ultimos siete. La franja cabe en el
 * alto de dos lineas de texto, se recorre con el pulgar y deja el resto del
 * panel para lo que de verdad se hace aqui: registrar las tomas de hoy. El
 * calendario completo sigue existiendo donde si hace falta -- la agenda del
 * medico -- porque alli se planifica a un mes vista.
 *
 * ## El punto de color, y por que no basta
 *
 * Verde si el dia se cumplio entero, rojo si falto alguna, gris si no habia
 * tomas o el dia sigue en curso. El color acelera la lectura, pero NO es el
 * unico canal: cada dia lleva su recuento exacto en la etiqueta accesible
 * ("3 de 4 tomas cumplidas"), porque un historial de adherencia que solo se
 * distingue por tono es ilegible para quien no percibe el rojo y el verde.
 *
 * Un dia en curso nunca se pinta de rojo. Marcar como incumplido un dia que
 * todavia no ha terminado culpa al paciente de algo que aun puede hacer, y esa
 * culpa mal puesta es lo que hace que la gente deje de abrir la pantalla.
 */
@Composable
fun CalendarioSemanal(
    dias: List<DiaDeAdherencia>,
    fechaSeleccionada: String,
    alElegirDia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val iniciales = stringArrayResource(Res.array.opciones_iniciales_dias)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = espaciado.minimo),
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        items(dias, key = { it.fecha }) { dia ->
            CasillaDeDia(
                dia = dia,
                inicial = iniciales.getOrElse(
                    CalendarioSalud.diaDeLaSemana(dia.fecha) ?: 0,
                ) { "" },
                seleccionado = dia.fecha == fechaSeleccionada,
                alPulsar = { alElegirDia(dia.fecha) },
            )
        }
    }
}

@Composable
private fun CasillaDeDia(
    dia: DiaDeAdherencia,
    inicial: String,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val numero = dia.fecha.takeLast(2).trimStart('0')

    val fondo by animateColorAsState(
        targetValue = if (seleccionado) {
            MaterialTheme.colorScheme.primary
        } else {
            colores.fondoTarjeta
        },
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "fondoDia",
    )
    val tinta = if (seleccionado) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val descripcionBase = when (dia.estado) {
        EstadoDia.COMPLETO -> stringResource(
            Res.string.a11y_semana_dia_completo, inicial, numero.toIntOrNull() ?: 0,
        )

        EstadoDia.INCOMPLETO -> stringResource(
            Res.string.a11y_semana_dia_incompleto,
            inicial,
            numero.toIntOrNull() ?: 0,
            dia.tomasCumplidas,
            dia.tomasProgramadas,
        )

        EstadoDia.SIN_TOMAS -> stringResource(
            Res.string.a11y_semana_dia_sin_tomas, inicial, numero.toIntOrNull() ?: 0,
        )

        EstadoDia.EN_CURSO -> stringResource(
            Res.string.a11y_semana_dia_en_curso,
            inicial,
            numero.toIntOrNull() ?: 0,
            dia.tomasCumplidas,
            dia.tomasProgramadas,
        )
    }
    val descripcion = if (seleccionado) {
        stringResource(Res.string.a11y_semana_dia_seleccionado, descripcionBase)
    } else {
        descripcionBase
    }

    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(ANCHO_CASILLA)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.grande)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                selected = seleccionado
            },
        color = fondo,
        shape = FormaSalud.grande,
        border = if (seleccionado) null else bordeDeTarjeta(),
        shadowElevation = if (seleccionado) 0.dp else elevacionDeTarjeta(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = espaciado.medio),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = inicial,
                style = MaterialTheme.typography.labelSmall,
                color = if (seleccionado) tinta else colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.minimo))
            Text(
                text = numero,
                style = MaterialTheme.typography.titleMedium,
                color = tinta,
            )
            Spacer(Modifier.height(espaciado.compacto))
            Box(
                Modifier
                    .size(MedidaSalud.punto)
                    .background(dia.estado.punto(seleccionado), CircleShape),
            )
        }
    }
}

/**
 * Color del punto de cumplimiento.
 *
 * Sobre un dia seleccionado el punto se aclara al color del texto: los tonos de
 * senalizacion estan calibrados contra el fondo de tarjeta, y sobre el azul de
 * marca el verde y el gris pierden todo su contraste.
 */
@Composable
private fun EstadoDia.punto(seleccionado: Boolean): androidx.compose.ui.graphics.Color {
    val colores = LocalColoresSalud.current
    if (seleccionado) return MaterialTheme.colorScheme.onPrimary
    return when (this) {
        EstadoDia.COMPLETO -> colores.exito
        EstadoDia.INCOMPLETO -> colores.acentoCritico
        EstadoDia.SIN_TOMAS, EstadoDia.EN_CURSO -> colores.separador
    }
}

private val ANCHO_CASILLA = 52.dp
