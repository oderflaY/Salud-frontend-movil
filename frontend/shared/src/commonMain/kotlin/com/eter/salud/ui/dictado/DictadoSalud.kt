package com.eter.salud.ui.dictado

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.dictado.ErrorDictado
import com.eter.salud.domain.dictado.EtiquetasResumen
import com.eter.salud.domain.dictado.rememberReconocedorDeVoz
import com.eter.salud.presentation.dictado.ControladorDeDictado
import com.eter.salud.presentation.dictado.FaseDictado
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_dictado_descartar
import salud.shared.generated.resources.a11y_dictado_detener
import salud.shared.generated.resources.a11y_dictado_iniciar
import salud.shared.generated.resources.dictado_accion_descartar
import salud.shared.generated.resources.dictado_accion_detener
import salud.shared.generated.resources.dictado_accion_entendido
import salud.shared.generated.resources.dictado_agregar_resumen
import salud.shared.generated.resources.dictado_ayuda_puntuacion
import salud.shared.generated.resources.dictado_aviso_urgencia
import salud.shared.generated.resources.dictado_datos_detectados
import salud.shared.generated.resources.dictado_error_desconocido
import salud.shared.generated.resources.dictado_error_microfono_ocupado
import salud.shared.generated.resources.dictado_error_no_disponible
import salud.shared.generated.resources.dictado_error_sin_conexion
import salud.shared.generated.resources.dictado_error_sin_permiso
import salud.shared.generated.resources.dictado_estado_escuchando
import salud.shared.generated.resources.dictado_estado_finalizando
import salud.shared.generated.resources.dictado_estado_preparando
import salud.shared.generated.resources.dictado_resumen_desde
import salud.shared.generated.resources.dictado_resumen_dolor
import salud.shared.generated.resources.dictado_resumen_glucosa
import salud.shared.generated.resources.dictado_resumen_presion
import salud.shared.generated.resources.dictado_resumen_pulso
import salud.shared.generated.resources.dictado_resumen_saturacion
import salud.shared.generated.resources.dictado_resumen_temperatura
import salud.shared.generated.resources.dictado_resumen_titulo

/**
 * Dictado para un campo de texto: el micrófono del teléfono escribe en el mismo
 * campo que el teclado. Se usa en el diario del paciente y en los dos chats.
 *
 * @param alCambiarTexto el setter del campo (`actualizarBorrador`, `actualizarTexto`).
 */
@Composable
fun rememberControladorDeDictado(alCambiarTexto: (String) -> Unit): ControladorDeDictado {
    val reconocedor = rememberReconocedorDeVoz()
    val etiquetas = etiquetasDelResumen()
    val alCambiar by rememberUpdatedState(alCambiarTexto)
    val controlador = remember(reconocedor, etiquetas) {
        ControladorDeDictado(reconocedor, etiquetas) { alCambiar(it) }
    }
    // Salir de la pantalla suelta el micrófono sin perder lo dictado.
    DisposableEffect(controlador) {
        onDispose { controlador.soltar() }
    }
    return controlador
}

@Composable
private fun etiquetasDelResumen() = EtiquetasResumen(
    titulo = stringResource(Res.string.dictado_resumen_titulo),
    temperatura = stringResource(Res.string.dictado_resumen_temperatura),
    presion = stringResource(Res.string.dictado_resumen_presion),
    pulso = stringResource(Res.string.dictado_resumen_pulso),
    saturacion = stringResource(Res.string.dictado_resumen_saturacion),
    glucosa = stringResource(Res.string.dictado_resumen_glucosa),
    dolor = stringResource(Res.string.dictado_resumen_dolor),
    desde = stringResource(Res.string.dictado_resumen_desde),
)

/**
 * Botón de micrófono. Mientras escucha cambia a "detener" y un halo crece con el
 * volumen de la voz: confirma a simple vista que el teléfono está oyendo.
 *
 * El halo se escala en `graphicsLayer` (fase de dibujo): los cambios de volumen,
 * que llegan varias veces por segundo, no recomponen ni vuelven a medir la fila
 * del campo.
 *
 * Si el teléfono no tiene reconocedor de voz, el botón no aparece.
 */
@Composable
fun BotonDictar(
    controlador: ControladorDeDictado,
    textoActual: () -> String,
    modifier: Modifier = Modifier,
) {
    if (!controlador.disponible) return
    val estado by controlador.estado.collectAsStateWithLifecycle()
    val colores = LocalColoresSalud.current
    val haptica = LocalHapticFeedback.current
    val activo = estado.activo
    val escala by animateFloatAsState(
        targetValue = if (activo) 1f + estado.nivelDeVoz * 0.45f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "haloDictado",
    )
    val descripcion = stringResource(if (activo) Res.string.a11y_dictado_detener else Res.string.a11y_dictado_iniciar)

    Box(
        modifier = modifier
            .size(AreaTactilMinima)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        if (activo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        alpha = 0.28f
                    }
                    .background(colores.acentoAccion, CircleShape),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button) {
                    haptica.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (activo) controlador.detener() else controlador.iniciar(textoActual())
                },
            color = if (activo) colores.acentoAccion else colores.fondoCampo,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                IconoSalud(
                    glifo = if (activo) GlifoSalud.DETENER else GlifoSalud.MICROFONO,
                    lado = 22.dp,
                    color = if (activo) colores.sobreAcentoAccion else colores.textoSecundario,
                )
            }
        }
    }
}

/**
 * Lo que acompaña al dictado mientras está activo: en qué paso va, la ayuda de
 * puntuación, los datos clínicos que se van detectando y las acciones.
 *
 * @param alEnviar si no es null, aparece "Detener y enviar" (o guardar): se
 * espera al texto final antes de enviarlo.
 * @param etiquetaEnviar el texto de ese botón.
 * @param avisarUrgencia muestra la recomendación de llamar al 911 cuando el
 * triage del texto sale en rojo (chat del paciente).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelDeDictado(
    controlador: ControladorDeDictado,
    modifier: Modifier = Modifier,
    alEnviar: (() -> Unit)? = null,
    etiquetaEnviar: StringResource? = null,
    avisarUrgencia: Boolean = false,
) {
    val estado by controlador.estado.collectAsStateWithLifecycle()
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    val etiquetas = etiquetasDelResumen()

    AnimatedVisibility(
        visible = estado.activo || estado.error != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colores.fondoTarjeta, FormaSalud.destacada)
                .padding(espaciado.medio),
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            val error = estado.error
            if (error != null && !estado.activo) {
                Text(
                    text = stringResource(error.recurso()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                TextButton(onClick = controlador::descartarError) {
                    Text(stringResource(Res.string.dictado_accion_entendido))
                }
                return@Column
            }

            Text(
                text = stringResource(
                    when (estado.fase) {
                        FaseDictado.PREPARANDO, FaseDictado.INACTIVO -> Res.string.dictado_estado_preparando
                        FaseDictado.ESCUCHANDO -> Res.string.dictado_estado_escuchando
                        FaseDictado.FINALIZANDO -> Res.string.dictado_estado_finalizando
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                text = stringResource(Res.string.dictado_ayuda_puntuacion),
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
            )

            val datos = estado.resumen.piezas(etiquetas)
            if (datos.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.dictado_datos_detectados),
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoSecundario,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(espaciado.minimo),
                    verticalArrangement = Arrangement.spacedBy(espaciado.minimo),
                ) {
                    datos.forEach { dato ->
                        Text(
                            text = dato,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(colores.fondoCampo, CircleShape)
                                .padding(horizontal = espaciado.compacto, vertical = espaciado.minimo),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = estado.agregarResumen,
                            role = Role.Switch,
                            onValueChange = { controlador.alternarResumen() },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.dictado_agregar_resumen),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = estado.agregarResumen, onCheckedChange = null)
                }
            }

            if (avisarUrgencia && estado.triage.severidad == SeveridadDiario.ROJO) {
                Text(
                    text = stringResource(Res.string.dictado_aviso_urgencia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoAdvertencia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colores.fondoAdvertencia, FormaSalud.destacada)
                        .padding(espaciado.compacto)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            val descripcionDescartar = stringResource(Res.string.a11y_dictado_descartar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
            ) {
                TextButton(
                    onClick = controlador::cancelar,
                    modifier = Modifier.semantics { contentDescription = descripcionDescartar },
                ) {
                    Text(stringResource(Res.string.dictado_accion_descartar))
                }
                Spacer(Modifier.weight(1f))
                val finalizando = estado.fase == FaseDictado.FINALIZANDO
                OutlinedButton(onClick = controlador::detener, enabled = !finalizando) {
                    Text(stringResource(Res.string.dictado_accion_detener))
                }
                if (alEnviar != null && etiquetaEnviar != null) {
                    Button(onClick = { controlador.detenerYEnviar(alEnviar) }, enabled = !finalizando) {
                        Text(stringResource(etiquetaEnviar))
                    }
                }
            }
        }
    }
}

private fun ErrorDictado.recurso(): StringResource = when (this) {
    ErrorDictado.SIN_PERMISO -> Res.string.dictado_error_sin_permiso
    ErrorDictado.NO_DISPONIBLE -> Res.string.dictado_error_no_disponible
    ErrorDictado.SIN_CONEXION -> Res.string.dictado_error_sin_conexion
    ErrorDictado.MICROFONO_OCUPADO -> Res.string.dictado_error_microfono_ocupado
    ErrorDictado.DESCONOCIDO -> Res.string.dictado_error_desconocido
}
