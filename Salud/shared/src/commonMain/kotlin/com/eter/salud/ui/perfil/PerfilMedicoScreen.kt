package com.eter.salud.ui.perfil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.presentation.perfil.PerfilMedicoViewModel
import com.eter.salud.presentation.perfil.PerfilUiState
import com.eter.salud.presentation.perfil.SeccionPerfil
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_perfil_cargando
import salud.shared.generated.resources.a11y_perfil_cerrar_seccion
import salud.shared.generated.resources.a11y_perfil_fila
import salud.shared.generated.resources.a11y_perfil_guardando
import salud.shared.generated.resources.a11y_perfil_guardar_seccion
import salud.shared.generated.resources.a11y_perfil_progreso
import salud.shared.generated.resources.a11y_perfil_seccion_guardada
import salud.shared.generated.resources.accion_guardar
import salud.shared.generated.resources.perfil_accion_cerrar
import salud.shared.generated.resources.perfil_estado_cargando
import salud.shared.generated.resources.perfil_estado_guardado
import salud.shared.generated.resources.perfil_estado_guardando
import salud.shared.generated.resources.perfil_estado_seccion_completa
import salud.shared.generated.resources.perfil_estado_seccion_pendiente
import salud.shared.generated.resources.perfil_progreso_completado
import salud.shared.generated.resources.perfil_seccion_completar_descripcion
import salud.shared.generated.resources.perfil_seccion_completar_titulo
import salud.shared.generated.resources.perfil_titulo

/**
 * Pantalla de Ajustes de Perfil (Fase 2).
 *
 * Es una lista de secciones, no un cuestionario: el paciente entra a corregir
 * un dato suelto y sale. Cada seccion se edita dentro de un modal del sistema,
 * como marca el DM (seccion 5: componentes y controles nativos).
 *
 * Cumplimiento del DM:
 *  - Cero texto literal: todo sale de `strings.xml`.
 *  - Sin emojis ni iconografia decorativa; el estado se comunica con palabras.
 *  - Color y espaciado solo por tokens semanticos, con Modo Oscuro resuelto en
 *    `SaludTheme`.
 *  - Cada fila y cada boton declaran su etiqueta para TalkBack / VoiceOver, y
 *    las confirmaciones se anuncian como region viva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilMedicoScreen(
    viewModel: PerfilMedicoViewModel,
    idPaciente: String,
    modifier: Modifier = Modifier,
    /** Retorno al inicio; nulo cuando la pantalla es la raiz de su pila. */
    alVolver: (() -> Unit)? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    LaunchedEffect(idPaciente) { viewModel.cargarPerfil(idPaciente) }

    // Cabecera grande, igual que en las otras dos pestanas. La flecha de
    // retroceso solo aparece cuando esta pantalla se abre APILADA (con
    // `alVolver`); como raiz de pestana no hay a donde volver.
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = espaciado.amplio)
            .margenInferiorSeguro(),
    ) {
        Spacer(Modifier.height(espaciado.medio))
        CabeceraGrande(
            titulo = stringResource(Res.string.perfil_titulo),
            accion = if (alVolver != null) {
                { BotonAtras(alPulsar = alVolver) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(espaciado.amplio))
        CabeceraPerfil(estado)
        Spacer(Modifier.height(espaciado.generoso))

        if (estado.cargando) {
            IndicadorCargando()
        } else {
            SeccionPerfil.entries.forEach { seccion ->
                FilaSeccion(
                    seccion = seccion,
                    completa = estado.estaCompleta(seccion),
                    alAbrir = { viewModel.abrirSeccion(seccion) },
                )
            }
        }

        AvisoDeGuardado(estado, viewModel::descartarConfirmacion)
        Spacer(Modifier.height(espaciado.respiro))
    }

    estado.seccionAbierta?.let { seccion ->
        ModalBottomSheet(
            onDismissRequest = viewModel::cerrarSeccion,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = espaciado.amplio)
                    .verticalScroll(rememberScrollState())
                    .margenInferiorSeguro(),
                verticalArrangement = Arrangement.spacedBy(espaciado.amplio),
            ) {
                ContenidoSeccion(seccion = seccion, estado = estado, viewModel = viewModel)
                AccionesSeccion(
                    guardando = estado.guardando,
                    alGuardar = viewModel::guardarSeccion,
                    alCerrar = viewModel::cerrarSeccion,
                )
            }
        }
    }
}

/** Titulo de la seccion de ajustes y avance del expediente. */
@Composable
private fun CabeceraPerfil(estado: PerfilUiState) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcionProgreso = stringResource(
        Res.string.a11y_perfil_progreso,
        estado.porcentajeCompletado,
    )

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.perfil_seccion_completar_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.perfil_seccion_completar_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.amplio))
        LinearProgressIndicator(
            progress = { estado.porcentajeCompletado / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = descripcionProgreso },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(
                Res.string.perfil_progreso_completado,
                estado.porcentajeCompletado,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
    }
}

/** Fila de la lista: nombre de la seccion y si ya quedo guardada. */
@Composable
private fun FilaSeccion(
    seccion: SeccionPerfil,
    completa: Boolean,
    alAbrir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val nombre = stringResource(seccion.recursoFila())
    val estadoTexto = if (completa) {
        stringResource(Res.string.perfil_estado_seccion_completa)
    } else {
        stringResource(Res.string.perfil_estado_seccion_pendiente)
    }
    val descripcion = stringResource(Res.string.a11y_perfil_fila, nombre, estadoTexto)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .clickable(onClick = alAbrir)
                .semantics(mergeDescendants = true) { contentDescription = descripcion }
                .padding(vertical = espaciado.medio),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = estadoTexto,
                style = MaterialTheme.typography.labelMedium,
                color = if (completa) colores.exito else colores.textoSecundario,
            )
        }
        HorizontalDivider(color = colores.separador)
    }
}

/** Una sola accion primaria por modal, mas la salida sin guardar. */
@Composable
private fun AccionesSeccion(
    guardando: Boolean,
    alGuardar: () -> Unit,
    alCerrar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val descripcionGuardar = stringResource(Res.string.a11y_perfil_guardar_seccion)
    val descripcionCerrar = stringResource(Res.string.a11y_perfil_cerrar_seccion)
    val descripcionGuardando = stringResource(Res.string.a11y_perfil_guardando)

    if (guardando) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = descripcionGuardando
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.perfil_estado_guardando),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
        Button(
            onClick = alGuardar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionGuardar },
        ) {
            Text(stringResource(Res.string.accion_guardar))
        }
        TextButton(
            onClick = alCerrar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionCerrar },
        ) {
            Text(stringResource(Res.string.perfil_accion_cerrar))
        }
    }
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_perfil_cargando)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(Res.string.perfil_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Confirmacion o fallo del ultimo guardado. Va como region viva para que
 * VoiceOver y TalkBack lo anuncien sin que el usuario tenga que buscarlo.
 */
@Composable
private fun AvisoDeGuardado(estado: PerfilUiState, alDescartar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    estado.errorGuardado?.let { error ->
        Spacer(Modifier.height(espaciado.amplio))
        Text(
            text = stringResource(error.recurso()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }

    estado.seccionGuardada?.let { seccion ->
        val nombre = stringResource(seccion.recursoFila())
        val anuncio = stringResource(Res.string.a11y_perfil_seccion_guardada, nombre)
        Spacer(Modifier.height(espaciado.amplio))
        Text(
            text = stringResource(Res.string.perfil_estado_guardado, nombre),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.exito,
            modifier = Modifier.semantics {
                contentDescription = anuncio
                liveRegion = LiveRegionMode.Polite
            },
        )
        TextButton(
            onClick = alDescartar,
            modifier = Modifier.heightIn(min = AreaTactilMinima),
        ) {
            Text(stringResource(Res.string.perfil_accion_cerrar))
        }
    }
}
