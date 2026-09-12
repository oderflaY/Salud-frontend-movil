package com.eter.salud.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.model.PuntoResumenIa
import com.eter.salud.presentation.chatmedico.EstadoResumenIa
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.NeonSky
import com.eter.salud.ui.theme.Sapphire
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_ia_resumen_accion_leer_original
import salud.shared.generated.resources.a11y_ia_resumen_accion_ocultar_original
import salud.shared.generated.resources.a11y_ia_resumen_cargando
import salud.shared.generated.resources.a11y_ia_resumen_tarjeta
import salud.shared.generated.resources.ia_resumen_accion_leer_original
import salud.shared.generated.resources.ia_resumen_accion_ocultar_original
import salud.shared.generated.resources.ia_resumen_estado_cargando
import salud.shared.generated.resources.ia_resumen_estado_error
import salud.shared.generated.resources.ia_resumen_titulo

/**
 * Tarjeta de resumen clinico generado por IA, para la vista del medico.
 *
 * ## La marca de "esto lo escribio una IA"
 *
 * Un filete de 1dp con gradiente Sapphire -> NeonSky es la unica diferencia
 * visual entre esta tarjeta y [TarjetaSalud]: no hay insignia, ni icono, ni
 * fondo tintado. El gradiente cruza los dos acentos del sistema (el de Modo
 * Claro y el de Modo Oscuro) a proposito, para que la marca se reconozca igual
 * mire quien la mire en el modo que sea -- es identidad de "contenido
 * procesado", no un color de estado.
 *
 * ## El acordeon
 *
 * El mensaje original nunca desaparece de la app: sigue estando siempre, un
 * toque por debajo del resumen. El resorte con el que se despliega es
 * deliberadamente blando (sin rebote): es una revelacion de dato clinico, no un
 * gesto ludico.
 *
 * [original] y [alAlternarOriginal] viven fuera del componente (lo decide quien
 * llama) por la misma razon que en [TranslatedMessageBubble]: la tarjeta es
 * tonta, solo dibuja el estado que recibe.
 */
@Composable
fun ClinicalAiSummaryCard(
    estado: EstadoResumenIa,
    original: Boolean,
    alAlternarOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcionTarjeta = stringResource(Res.string.a11y_ia_resumen_tarjeta)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = GROSOR_FILETE_IA,
                brush = Brush.linearGradient(listOf(Sapphire, NeonSky)),
                shape = FormaSalud.grande,
            )
            .semantics(mergeDescendants = true) { contentDescription = descripcionTarjeta },
        color = colores.fondoTarjeta,
        shape = FormaSalud.grande,
    ) {
        Column(
            modifier = Modifier.padding(espaciado.amplio),
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            Text(
                text = stringResource(Res.string.ia_resumen_titulo),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
            )

            when (estado) {
                EstadoResumenIa.Cargando -> ResumenCargando()
                is EstadoResumenIa.Fallido -> {
                    Text(
                        text = stringResource(Res.string.ia_resumen_estado_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is EstadoResumenIa.Disponible -> {
                    Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
                        estado.resumen.puntos.forEach { punto -> PuntoResumen(punto) }
                    }

                    HorizontalDivider(color = colores.separador)

                    AcordeonMensajeOriginal(
                        mensajeOriginal = estado.resumen.mensajeOriginal,
                        numeroPalabras = estado.resumen.numeroPalabrasOriginal,
                        expandido = original,
                        alAlternar = alAlternarOriginal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumenCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_ia_resumen_cargando)
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(LADO_PROGRESO_RESUMEN),
            color = colores.acentoAccion,
        )
        Text(
            text = stringResource(Res.string.ia_resumen_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
            modifier = Modifier.semantics { contentDescription = descripcion },
        )
    }
}

/** Una fila etiqueta/valor del resumen, en tipografia pesada para escanearse rapido. */
@Composable
private fun PuntoResumen(punto: PuntoResumenIa) {
    val colores = LocalColoresSalud.current
    Column {
        Text(
            text = punto.etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = colores.textoSecundario,
        )
        Text(
            text = punto.valor,
            style = MaterialTheme.typography.titleSmall,
            color = colores.textoPrincipal,
        )
    }
}

/**
 * Area tactil expansible que revela el mensaje original completo.
 *
 * El resorte usa amortiguacion SIN rebote: el dato que revela es clinico, y un
 * rebote lo haria leer como un gesto de juego en lugar de una consulta seria.
 */
@Composable
private fun AcordeonMensajeOriginal(
    mensajeOriginal: String,
    numeroPalabras: Int,
    expandido: Boolean,
    alAlternar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuenteDeInteraccion = remember { MutableInteractionSource() }

    val etiqueta = if (expandido) {
        stringResource(Res.string.ia_resumen_accion_ocultar_original)
    } else {
        stringResource(Res.string.ia_resumen_accion_leer_original, numeroPalabras)
    }
    val descripcion = if (expandido) {
        stringResource(Res.string.a11y_ia_resumen_accion_ocultar_original)
    } else {
        stringResource(Res.string.a11y_ia_resumen_accion_leer_original, numeroPalabras)
    }

    val rotacionFlecha by animateFloatAsState(
        targetValue = if (expandido) ROTACION_FLECHA_ABIERTA else 0f,
        animationSpec = MUELLE_ROTACION,
        label = "rotacionFlechaAcordeon",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clip(FormaSalud.sutil)
            .clickable(interactionSource = fuenteDeInteraccion, indication = null, onClick = alAlternar)
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.sutil)
            .semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelLarge,
            color = colores.acentoAccion,
        )
        IconoSalud(
            glifo = GlifoSalud.SIGUIENTE,
            lado = LADO_FLECHA_ACORDEON,
            color = colores.acentoAccion,
            modifier = Modifier.graphicsLayer { rotationZ = rotacionFlecha },
        )
    }

    AnimatedVisibility(
        visible = expandido,
        enter = expandVertically(MUELLE_TAMANO) + fadeIn(),
        exit = shrinkVertically(MUELLE_TAMANO) + fadeOut(),
    ) {
        Text(
            text = mensajeOriginal,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoPrincipal,
            modifier = Modifier.padding(top = espaciado.compacto),
        )
    }
}

/** Resorte sin rebote para el tamano del bloque revelado (alto en px). */
private val MUELLE_TAMANO = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)

/** El mismo caracter de resorte, aplicado al giro de la flecha. */
private val MUELLE_ROTACION = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)

private val GROSOR_FILETE_IA = 1.dp
private val LADO_FLECHA_ACORDEON = 20.dp
private val LADO_PROGRESO_RESUMEN = 20.dp
private const val ROTACION_FLECHA_ABIERTA = 90f
