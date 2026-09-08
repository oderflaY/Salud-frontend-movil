package com.eter.salud.ui.emergencia

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.presentation.emergencia.EscanerUiState
import com.eter.salud.presentation.emergencia.ErrorEscaneo
import com.eter.salud.presentation.emergencia.EscanerEmergenciaViewModel
import com.eter.salud.presentation.emergencia.FaseEscaneo
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_nfc_cancelar
import salud.shared.generated.resources.a11y_nfc_consultando
import salud.shared.generated.resources.a11y_nfc_ondas
import salud.shared.generated.resources.a11y_nfc_reintentar
import salud.shared.generated.resources.a11y_nfc_titulo
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.nfc_accion_cancelar
import salud.shared.generated.resources.nfc_accion_reintentar
import salud.shared.generated.resources.nfc_descripcion
import salud.shared.generated.resources.nfc_estado_consultando
import salud.shared.generated.resources.nfc_titulo

/**
 * Escaner de emergencia para personal medico.
 *
 * Es el punto de entrada al modulo NFC: activa la antena al montarse y la
 * apaga siempre al salir, sin importar por que fase pase la pantalla, porque
 * una antena encendida de mas consume bateria en un turno donde el telefono no
 * se vuelve a cargar hasta terminar.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, color y espaciado solo
 * por tokens semanticos, Modo Oscuro automatico via `SaludTheme`, margenes de
 * seguridad inferiores en la barra de acciones, y una vibracion de exito que la
 * Vista dispara y notifica de vuelta al ViewModel (el ViewModel no toca hardware
 * de interfaz, solo pide el evento).
 */
@Composable
fun EscanerEmergenciaScreen(
    viewModel: EscanerEmergenciaViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val haptica = LocalHapticFeedback.current

    // La antena es un recurso de hardware: se enciende al entrar y se apaga al
    // salir por cualquier camino (cerrar, error, o cambio de pantalla), no solo
    // cuando el ViewModel se destruye.
    DisposableEffect(viewModel) {
        viewModel.activarEscaneo()
        onDispose { viewModel.detenerEscaneo() }
    }

    LaunchedEffect(estado.confirmarConVibracion) {
        if (estado.confirmarConVibracion) {
            haptica.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.vibracionConsumida()
        }
    }

    if (estado.fase == FaseEscaneo.PERFIL_DISPONIBLE) {
        val perfil = estado.perfil
        if (perfil != null) {
            PerfilSupervivenciaScreen(
                perfil = perfil,
                edad = estado.edadPaciente,
                modifier = modifier,
                alCerrar = alVolver,
                alEscanearOtra = viewModel::escanearOtraTarjeta,
            )
            return
        }
    }

    PantallaDeEscaneo(
        estado = estado,
        modifier = modifier,
        alCancelar = alVolver,
        alReintentarAntena = viewModel::activarEscaneo,
        alReintentarConsulta = viewModel::escanearOtraTarjeta,
    )
}

/**
 * Estado de espera, consulta y error de lectura: todo lo que no es el perfil ya
 * resuelto. Es una sola pantalla tipo modal a pantalla completa, con el mismo
 * fondo del tema y un resplandor difuminado detras del icono que pulsa.
 */
@Composable
private fun PantallaDeEscaneo(
    estado: EscanerUiState,
    modifier: Modifier = Modifier,
    alCancelar: () -> Unit,
    alReintentarAntena: () -> Unit,
    alReintentarConsulta: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(espaciado.generoso),
            contentAlignment = Alignment.Center,
        ) {
            when (estado.fase) {
                FaseEscaneo.ANTENA_NO_DISPONIBLE ->
                    MensajeCentral(
                        titulo = stringResource(estado.disponibilidad.recurso()),
                        colorTitulo = colores.acentoCritico,
                    )

                FaseEscaneo.ERROR -> estado.errorConsulta?.let { error ->
                    MensajeCentral(
                        titulo = stringResource(error.recurso()),
                        colorTitulo = colores.acentoCritico,
                    )
                }

                FaseEscaneo.CONSULTANDO -> EstadoConsultando()

                else -> EstadoEsperandoTarjeta(errorTransitorio = estado.errorConsulta)
            }
        }

        BarraAccionInferior {
            when (estado.fase) {
                FaseEscaneo.ANTENA_NO_DISPONIBLE -> {
                    if (estado.disponibilidad == DisponibilidadNfc.DESACTIVADA) {
                        BotonAccionPrincipal(
                            etiqueta = stringResource(Res.string.nfc_accion_reintentar),
                            alPulsar = alReintentarAntena,
                            descripcionAccesible = stringResource(Res.string.a11y_nfc_reintentar),
                        )
                    }
                    BotonSecundarioSalud(
                        etiqueta = stringResource(Res.string.accion_atras),
                        alPulsar = alCancelar,
                        descripcionAccesible = stringResource(Res.string.a11y_nfc_cancelar),
                    )
                }

                FaseEscaneo.ERROR -> {
                    BotonAccionPrincipal(
                        etiqueta = stringResource(Res.string.nfc_accion_reintentar),
                        alPulsar = alReintentarConsulta,
                        descripcionAccesible = stringResource(Res.string.a11y_nfc_reintentar),
                    )
                    BotonSecundarioSalud(
                        etiqueta = stringResource(Res.string.accion_atras),
                        alPulsar = alCancelar,
                        descripcionAccesible = stringResource(Res.string.a11y_nfc_cancelar),
                    )
                }

                else -> BotonSecundarioSalud(
                    etiqueta = stringResource(Res.string.nfc_accion_cancelar),
                    alPulsar = alCancelar,
                    descripcionAccesible = stringResource(Res.string.a11y_nfc_cancelar),
                )
            }
        }
    }
}

/** Antena escuchando: las ondas NFC pulsantes, el titulo grande y la ayuda. */
@Composable
private fun EstadoEsperandoTarjeta(errorTransitorio: ErrorEscaneo?) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcionOndas = stringResource(Res.string.a11y_nfc_ondas)
    val descripcionTitulo = stringResource(Res.string.a11y_nfc_titulo)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OndasNfcPulsantes(
            modifier = Modifier.semantics { contentDescription = descripcionOndas },
        )
        Spacer(Modifier.height(espaciado.generoso))
        Text(
            text = stringResource(Res.string.nfc_titulo),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                contentDescription = descripcionTitulo
            },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.nfc_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
            textAlign = TextAlign.Center,
        )
        // Fallo fisico de lectura (la tarjeta se alejo): se avisa sin abandonar
        // la espera, porque el paramedico va a volver a acercarla de inmediato.
        if (errorTransitorio != null) {
            Spacer(Modifier.height(espaciado.amplio))
            Text(
                text = stringResource(errorTransitorio.recurso()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun EstadoConsultando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_nfc_consultando)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = descripcion
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        CircularProgressIndicator(color = LocalColoresSalud.current.acentoAccion)
        Spacer(Modifier.height(espaciado.amplio))
        Text(
            text = stringResource(Res.string.nfc_estado_consultando),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Mensaje central para los estados que no admiten espera activa. */
@Composable
private fun MensajeCentral(titulo: String, colorTitulo: Color) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.headlineSmall,
        color = colorTitulo,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

/**
 * Ondas concentricas que laten en opacidad y escala. Detras se difumina un
 * resplandor solido del mismo color: el "fondo difuminado" que pide el diseno,
 * aplicado aqui porque esta pantalla no tiene contenido real detras que
 * desenfocar (reemplaza a la pantalla completa, no flota sobre ella).
 */
@Composable
private fun OndasNfcPulsantes(modifier: Modifier = Modifier) {
    val color = LocalColoresSalud.current.acentoAccion
    val transicion = rememberInfiniteTransition(label = "ondas_nfc")
    val escala by transicion.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(DURACION_PULSO_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "escala_ondas",
    )
    val opacidad by transicion.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(DURACION_PULSO_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacidad_ondas",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(TAMANO_ONDAS)) {
        Box(
            modifier = Modifier
                .size(TAMANO_ONDAS)
                .scale(escala)
                .blur(RADIO_RESPLANDOR)
                .background(color = color.copy(alpha = 0.25f), shape = CircleShape),
        )
        Canvas(
            modifier = Modifier
                .size(TAMANO_ONDAS * 0.7f)
                .graphicsLayer { alpha = opacidad },
        ) {
            val trazo = Stroke(width = size.minDimension * 0.06f)
            val centro = Offset(size.width / 2f, size.height / 2f)
            listOf(0.32f, 0.55f, 0.8f).forEach { proporcion ->
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2f * proporcion,
                    center = centro,
                    style = trazo,
                )
            }
        }
    }
}

private const val DURACION_PULSO_MS = 1100
private val TAMANO_ONDAS = 180.dp
private val RADIO_RESPLANDOR = 24.dp
