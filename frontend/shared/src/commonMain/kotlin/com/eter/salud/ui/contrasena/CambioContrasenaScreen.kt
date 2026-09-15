package com.eter.salud.ui.contrasena

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.MotivoFalloCambioContrasena
import com.eter.salud.presentation.contrasena.CambioContrasenaUiState
import com.eter.salud.presentation.contrasena.CambioContrasenaViewModel
import com.eter.salud.presentation.contrasena.ErrorCampoContrasena
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.IsotipoSalud
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_accion_cerrar_sesion
import salud.shared.generated.resources.a11y_config_contrasena_accion
import salud.shared.generated.resources.a11y_contrasena_cancelar
import salud.shared.generated.resources.a11y_contrasena_guardar
import salud.shared.generated.resources.a11y_login_mostrar_contrasena
import salud.shared.generated.resources.a11y_login_ocultar_contrasena
import salud.shared.generated.resources.accion_cerrar_sesion
import salud.shared.generated.resources.config_contrasena_accion
import salud.shared.generated.resources.config_contrasena_actualizada
import salud.shared.generated.resources.config_contrasena_apoyo
import salud.shared.generated.resources.contrasena_accion_cancelar
import salud.shared.generated.resources.contrasena_accion_guardar
import salud.shared.generated.resources.contrasena_accion_guardar_continuar
import salud.shared.generated.resources.contrasena_actual_campo
import salud.shared.generated.resources.contrasena_confirmacion_campo
import salud.shared.generated.resources.contrasena_error_actual_incorrecta
import salud.shared.generated.resources.contrasena_error_actual_vacia
import salud.shared.generated.resources.contrasena_error_conexion
import salud.shared.generated.resources.contrasena_error_igual
import salud.shared.generated.resources.contrasena_error_no_coinciden
import salud.shared.generated.resources.contrasena_error_no_valida
import salud.shared.generated.resources.contrasena_error_nueva_corta
import salud.shared.generated.resources.contrasena_error_sesion
import salud.shared.generated.resources.contrasena_estado_guardando
import salud.shared.generated.resources.contrasena_nueva_apoyo
import salud.shared.generated.resources.contrasena_nueva_campo
import salud.shared.generated.resources.contrasena_obligatoria_explicacion
import salud.shared.generated.resources.contrasena_obligatoria_titulo
import salud.shared.generated.resources.error_contrasena_temporal_vencida
import salud.shared.generated.resources.login_accion_mostrar_contrasena
import salud.shared.generated.resources.login_accion_ocultar_contrasena

/**
 * Lo primero que ve el paciente al entrar con la contrasena temporal que le
 * dio su medico (reset desde el panel web).
 *
 * Es una compuerta, como el cuestionario del expediente: sin barra inferior y
 * sin otra salida que elegir la contrasena o cerrar sesion. El backend tampoco
 * le deja hacer nada mas con esa sesion.
 */
@Composable
fun CambioContrasenaObligatorioScreen(
    viewModel: CambioContrasenaViewModel,
    alTerminar: (SesionPaciente) -> Unit,
    alCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    LaunchedEffect(estado.sesionNueva) {
        estado.sesionNueva?.let { sesion ->
            alTerminar(sesion)
            viewModel.sesionEntregada()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = espaciado.amplio)
            .margenInferiorSeguro(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(espaciado.respiro + espaciado.generoso))
        Box(
            modifier = Modifier
                .size(DIAMETRO_VELO)
                .background(colores.veloAcento, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IsotipoSalud()
        }
        Spacer(Modifier.height(espaciado.generoso))
        Text(
            text = stringResource(Res.string.contrasena_obligatoria_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.contrasena_obligatoria_explicacion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = espaciado.medio),
        )

        Spacer(Modifier.height(espaciado.generoso))
        TarjetaSalud {
            CamposContrasena(estado, viewModel)
        }

        Spacer(Modifier.height(espaciado.medio))
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.contrasena_accion_guardar_continuar),
            alPulsar = viewModel::guardar,
            descripcionAccesible = stringResource(Res.string.a11y_contrasena_guardar),
            habilitado = estado.puedeEnviar,
            cargando = estado.enviando,
            etiquetaCargando = stringResource(Res.string.contrasena_estado_guardando),
        )
        MensajeDeFallo(estado)

        Spacer(Modifier.height(espaciado.amplio))
        val descripcionSalir = stringResource(Res.string.a11y_accion_cerrar_sesion)
        TextButton(
            onClick = alCerrarSesion,
            enabled = !estado.enviando,
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionSalir },
        ) {
            Text(
                text = stringResource(Res.string.accion_cerrar_sesion),
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
        }
        Spacer(Modifier.height(espaciado.respiro))
    }
}

/**
 * "Cambiar contrasena" en Ajustes. Plegada por defecto, como la baja: un
 * formulario de contrasenas no vive abierto al lado de los interruptores.
 */
@Composable
fun SeccionCambioContrasena(
    viewModel: CambioContrasenaViewModel,
    alContrasenaCambiada: (SesionPaciente) -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    LaunchedEffect(estado.sesionNueva) {
        estado.sesionNueva?.let { sesion ->
            alContrasenaCambiada(sesion)
            viewModel.sesionEntregada()
        }
    }

    TarjetaSalud {
        if (!estado.desplegado) {
            Text(
                text = stringResource(
                    if (estado.completado) Res.string.config_contrasena_actualizada else Res.string.config_contrasena_apoyo,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (estado.completado) colores.acentoAccion else colores.textoSecundario,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            val descripcion = stringResource(Res.string.a11y_config_contrasena_accion)
            TextButton(
                onClick = viewModel::desplegar,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcion },
                shape = FormaSalud.media,
            ) {
                Text(
                    text = stringResource(Res.string.config_contrasena_accion),
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.acentoAccion,
                    modifier = Modifier.padding(vertical = espaciado.compacto),
                )
            }
        } else {
            CamposContrasena(estado, viewModel)
            Spacer(Modifier.height(espaciado.compacto))
            BotonAccionPrincipal(
                etiqueta = stringResource(Res.string.contrasena_accion_guardar),
                alPulsar = viewModel::guardar,
                descripcionAccesible = stringResource(Res.string.a11y_contrasena_guardar),
                habilitado = estado.puedeEnviar,
                cargando = estado.enviando,
                etiquetaCargando = stringResource(Res.string.contrasena_estado_guardando),
            )
            MensajeDeFallo(estado)
            val descripcionCancelar = stringResource(Res.string.a11y_contrasena_cancelar)
            TextButton(
                onClick = viewModel::plegar,
                enabled = !estado.enviando,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionCancelar },
                shape = FormaSalud.media,
            ) {
                Text(
                    text = stringResource(Res.string.contrasena_accion_cancelar),
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.acentoAccion,
                )
            }
        }
    }
}

@Composable
private fun CamposContrasena(estado: CambioContrasenaUiState, viewModel: CambioContrasenaViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val alternar: @Composable () -> Unit = {
        AlternarVisibilidad(visible = estado.visible, alPulsar = viewModel::alternarVisibilidad)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        if (!estado.obligatorio) {
            val etiqueta = stringResource(Res.string.contrasena_actual_campo)
            CampoTextoRellenoSalud(
                valor = estado.actual,
                alCambiar = viewModel::actualizarActual,
                etiqueta = etiqueta,
                descripcionAccesible = etiqueta,
                tipoTeclado = KeyboardType.Password,
                ocultarTexto = !estado.visible,
                error = errorDe(estado.erroresCampo, ErrorCampoContrasena.ACTUAL_VACIA),
                accion = alternar,
            )
        }
        val etiquetaNueva = stringResource(Res.string.contrasena_nueva_campo)
        CampoTextoRellenoSalud(
            valor = estado.nueva,
            alCambiar = viewModel::actualizarNueva,
            etiqueta = etiquetaNueva,
            descripcionAccesible = etiquetaNueva,
            tipoTeclado = KeyboardType.Password,
            ocultarTexto = !estado.visible,
            error = errorDe(estado.erroresCampo, ErrorCampoContrasena.NUEVA_CORTA, ErrorCampoContrasena.IGUAL_A_LA_ACTUAL),
            // En el obligatorio no hay campo "actual": el boton de mostrar va aqui.
            accion = if (estado.obligatorio) alternar else null,
        )
        val etiquetaConfirmacion = stringResource(Res.string.contrasena_confirmacion_campo)
        CampoTextoRellenoSalud(
            valor = estado.confirmacion,
            alCambiar = viewModel::actualizarConfirmacion,
            etiqueta = etiquetaConfirmacion,
            descripcionAccesible = etiquetaConfirmacion,
            tipoTeclado = KeyboardType.Password,
            ocultarTexto = !estado.visible,
            error = errorDe(estado.erroresCampo, ErrorCampoContrasena.NO_COINCIDEN),
        )
        Text(
            text = stringResource(Res.string.contrasena_nueva_apoyo),
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoSecundario,
        )
    }
}

@Composable
private fun MensajeDeFallo(estado: CambioContrasenaUiState) {
    val espaciado = LocalEspaciadoSalud.current
    estado.error?.let { motivo ->
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(motivo.recurso()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** Texto y no icono, igual que en el acceso: se lee mejor con TalkBack y VoiceOver. */
@Composable
private fun AlternarVisibilidad(visible: Boolean, alPulsar: () -> Unit) {
    val descripcion = stringResource(
        if (visible) Res.string.a11y_login_ocultar_contrasena else Res.string.a11y_login_mostrar_contrasena,
    )
    TextButton(
        onClick = alPulsar,
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcion },
    ) {
        Text(
            text = stringResource(
                if (visible) Res.string.login_accion_ocultar_contrasena else Res.string.login_accion_mostrar_contrasena,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun errorDe(errores: List<ErrorCampoContrasena>, vararg propios: ErrorCampoContrasena): String? =
    errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }

internal fun ErrorCampoContrasena.recurso(): StringResource = when (this) {
    ErrorCampoContrasena.ACTUAL_VACIA -> Res.string.contrasena_error_actual_vacia
    ErrorCampoContrasena.NUEVA_CORTA -> Res.string.contrasena_error_nueva_corta
    ErrorCampoContrasena.NO_COINCIDEN -> Res.string.contrasena_error_no_coinciden
    ErrorCampoContrasena.IGUAL_A_LA_ACTUAL -> Res.string.contrasena_error_igual
}

internal fun MotivoFalloCambioContrasena.recurso(): StringResource = when (this) {
    MotivoFalloCambioContrasena.CREDENCIALES_INVALIDAS -> Res.string.contrasena_error_actual_incorrecta
    MotivoFalloCambioContrasena.CONTRASENA_NO_VALIDA -> Res.string.contrasena_error_no_valida
    MotivoFalloCambioContrasena.CONTRASENA_TEMPORAL_VENCIDA -> Res.string.error_contrasena_temporal_vencida
    MotivoFalloCambioContrasena.SESION_REVOCADA,
    MotivoFalloCambioContrasena.CUENTA_INACTIVA,
    -> Res.string.contrasena_error_sesion
    MotivoFalloCambioContrasena.SIN_CONEXION -> Res.string.contrasena_error_conexion
}

private val DIAMETRO_VELO = 112.dp
