package com.eter.salud.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.presentation.onboarding.OnboardingPacienteViewModel
import com.eter.salud.presentation.onboarding.OnboardingUiState
import com.eter.salud.presentation.onboarding.PasoOnboarding
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.accion_cerrar_sesion
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_boton_continuar
import salud.shared.generated.resources.a11y_boton_finalizar
import salud.shared.generated.resources.a11y_cargando
import salud.shared.generated.resources.a11y_onb_alerta_criticos
import salud.shared.generated.resources.a11y_progreso
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.accion_continuar
import salud.shared.generated.resources.accion_finalizar
import salud.shared.generated.resources.accion_omitir
import salud.shared.generated.resources.app_nombre
import salud.shared.generated.resources.estado_enviando
import salud.shared.generated.resources.onb_alerta_criticos_accion_completar
import salud.shared.generated.resources.onb_alerta_criticos_accion_continuar
import salud.shared.generated.resources.onb_alerta_criticos_mensaje
import salud.shared.generated.resources.onb_alerta_criticos_titulo
import salud.shared.generated.resources.onb_bienvenida_accion

/**
 * Vista inicial del flujo guiado de la Fase 1.
 *
 * Es pasiva: no valida, no transforma y no guarda nada. Solo pinta el
 * [OnboardingUiState] y reenvia las interacciones al ViewModel.
 *
 * Cumplimiento del DM:
 *  - Todo el texto sale de `strings.xml`; no hay ni una cadena literal visible.
 *  - Sin emojis ni adornos: jerarquia por tipografia y espacio en blanco.
 *  - Color y espaciado exclusivamente por tokens semanticos, con Modo Oscuro
 *    resuelto en `SaludTheme`.
 *  - Navegacion con la barra superior del sistema y decisiones en action sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPacienteScreen(
    viewModel: OnboardingPacienteViewModel,
    modifier: Modifier = Modifier,
    alTerminarRegistro: () -> Unit = {},
    /**
     * Salida del cuestionario cuando es obligatorio (cuenta recien creada). Solo
     * se ofrece en la primera pantalla: a media captura, "Cerrar sesion" al lado
     * de "Siguiente" invitaria a un toque equivocado que tira lo escrito.
     */
    alCerrarSesion: (() -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    val numero = estado.numeroPregunta
                    Text(
                        text = if (numero == null) {
                            stringResource(Res.string.app_nombre)
                        } else {
                            stringResource(Res.string.a11y_progreso, numero, estado.totalPreguntas)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    // Aqui la flecha retrocede una PREGUNTA, no una pantalla, y
                    // solo aparece si hay pregunta anterior: en la primera, una
                    // flecha apagada invitaria a salir del cuestionario.
                    if (estado.puedeRetroceder) {
                        BotonAtras(alPulsar = viewModel::retroceder)
                    }
                },
                actions = {
                    if (alCerrarSesion != null && !estado.puedeRetroceder) {
                        TextButton(onClick = alCerrarSesion) {
                            Text(
                                text = stringResource(Res.string.accion_cerrar_sesion),
                                style = MaterialTheme.typography.labelLarge,
                                color = colores.acentoAccion,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BarraAcciones(
                estado = estado,
                alAvanzar = {
                    if (estado.esUltimoPaso) viewModel.enviar() else viewModel.avanzar()
                },
                alOmitir = viewModel::omitirPaso,
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = espaciado.amplio),
        ) {
            // El avance se calcula UNA vez y la lambda cierra sobre ese Float.
            //
            // `progress` es una lambda diferida: Material3 la evalua en la fase
            // de dibujo, no al componer. Si dentro se volviera a leer
            // `estado.numeroPregunta`, al pasar de la ultima pregunta al resumen
            // (donde vale null) la lambda podria ejecutarse con el estado nuevo
            // y reventar la app justo al terminar el cuestionario.
            val numeroPregunta = estado.numeroPregunta
            if (numeroPregunta != null && estado.totalPreguntas > 0) {
                val avance = (numeroPregunta.toFloat() / estado.totalPreguntas)
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { avance },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(espaciado.generoso))

            ContenidoPaso(estado = estado, viewModel = viewModel)

            estado.errorEnvio?.let { error ->
                Spacer(Modifier.height(espaciado.amplio))
                Text(
                    text = stringResource(error.recurso()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            if (estado.envioExitoso) {
                Spacer(Modifier.height(espaciado.amplio))
                MensajeRegistroCompletado(alTerminarRegistro = alTerminarRegistro)
            }

            Spacer(Modifier.height(espaciado.amplio))
        }
    }

    if (estado.mostrarAlertaCriticos) {
        val descripcionAlerta = stringResource(Res.string.a11y_onb_alerta_criticos)
        ModalBottomSheet(
            onDismissRequest = viewModel::descartarAlertaCriticos,
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = espaciado.amplio)
                    .padding(bottom = espaciado.generoso)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(espaciado.medio),
            ) {
                Text(
                    text = stringResource(Res.string.onb_alerta_criticos_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = colores.acentoCritico,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription = descripcionAlerta
                    },
                )
                Text(
                    text = stringResource(Res.string.onb_alerta_criticos_mensaje),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
                estado.camposCriticosOmitidos.forEach { campo ->
                    Text(
                        text = stringResource(campo.recurso()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = {
                        estado.camposCriticosOmitidos.firstOrNull()?.let(
                            viewModel::irAPasoDelCampoCritico,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AreaTactilMinima),
                ) {
                    Text(stringResource(Res.string.onb_alerta_criticos_accion_completar))
                }
                TextButton(
                    onClick = viewModel::confirmarEnvioConCamposCriticosOmitidos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AreaTactilMinima),
                ) {
                    Text(stringResource(Res.string.onb_alerta_criticos_accion_continuar))
                }
            }
        }
    }
}

/** Barra inferior fija: una sola accion primaria por pantalla. */
@Composable
private fun BarraAcciones(
    estado: OnboardingUiState,
    alAvanzar: () -> Unit,
    alOmitir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val etiquetaPrimaria = when (estado.paso) {
        PasoOnboarding.BIENVENIDA -> stringResource(Res.string.onb_bienvenida_accion)
        PasoOnboarding.RESUMEN -> stringResource(Res.string.accion_finalizar)
        else -> stringResource(Res.string.accion_continuar)
    }
    val descripcionPrimaria = if (estado.esUltimoPaso) {
        stringResource(Res.string.a11y_boton_finalizar)
    } else {
        stringResource(Res.string.a11y_boton_continuar)
    }
    val descripcionCargando = stringResource(Res.string.a11y_cargando)

    BarraAccionInferior {
        if (estado.enviando) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = descripcionCargando
                        liveRegion = LiveRegionMode.Polite
                    },
                verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(Res.string.estado_enviando),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@BarraAccionInferior
        }
        Button(
            onClick = alAvanzar,
            enabled = !estado.envioExitoso,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionPrimaria },
        ) {
            Text(etiquetaPrimaria)
        }
        if (estado.puedeOmitir) {
            TextButton(
                onClick = alOmitir,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AreaTactilMinima),
            ) {
                Text(stringResource(Res.string.accion_omitir))
            }
        }
    }
}
