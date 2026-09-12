package com.eter.salud.ui.registro

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.presentation.registro.ErrorCampoRegistro
import com.eter.salud.presentation.registro.RegistroUiState
import com.eter.salud.presentation.registro.RegistroViewModel
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.CasillaDeclaracion
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_login_mostrar_contrasena
import salud.shared.generated.resources.a11y_login_ocultar_contrasena
import salud.shared.generated.resources.a11y_registro_accion_crear
import salud.shared.generated.resources.a11y_registro_accion_ya_tengo_cuenta
import salud.shared.generated.resources.a11y_registro_aviso_privacidad
import salud.shared.generated.resources.a11y_registro_confirmacion_campo
import salud.shared.generated.resources.a11y_registro_contrasena_campo
import salud.shared.generated.resources.a11y_registro_correo_campo
import salud.shared.generated.resources.a11y_registro_creando
import salud.shared.generated.resources.login_accion_mostrar_contrasena
import salud.shared.generated.resources.login_accion_ocultar_contrasena
import salud.shared.generated.resources.registro_accion_crear
import salud.shared.generated.resources.registro_accion_ya_tengo_cuenta
import salud.shared.generated.resources.registro_aviso_privacidad
import salud.shared.generated.resources.registro_confirmacion_campo
import salud.shared.generated.resources.registro_contrasena_ayuda
import salud.shared.generated.resources.registro_contrasena_campo
import salud.shared.generated.resources.registro_contrasena_placeholder
import salud.shared.generated.resources.registro_correo_campo
import salud.shared.generated.resources.registro_correo_placeholder
import salud.shared.generated.resources.registro_estado_creando
import salud.shared.generated.resources.registro_subtitulo
import salud.shared.generated.resources.registro_titulo

/**
 * Pantalla de alta de cuenta del paciente.
 *
 * Misma gramatica visual que el acceso: campos rellenos, una sola accion
 * dominante y las salidas secundarias bien separadas. Se navega con la barra
 * superior del sistema para poder volver al acceso sin perder lo escrito.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, color y espaciado solo
 * por tokens semanticos y etiquetas de accesibilidad en cada control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel,
    modifier: Modifier = Modifier,
    alCrearCuenta: (SesionPaciente) -> Unit = {},
    alVolverAlAcceso: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    LaunchedEffect(estado.sesion) {
        estado.sesion?.let { sesion ->
            alCrearCuenta(sesion)
            viewModel.sesionEntregada()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = stringResource(Res.string.registro_titulo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BotonAtras(alPulsar = alVolverAlAcceso) },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = espaciado.amplio)
                .margenInferiorSeguro(),
        ) {
            Spacer(Modifier.height(espaciado.amplio))
            Text(
                text = stringResource(Res.string.registro_titulo),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.registro_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )

            Spacer(Modifier.height(espaciado.generoso))
            Formulario(estado, viewModel)

            Spacer(Modifier.height(espaciado.amplio))
            BotonAccionPrincipal(
                etiqueta = stringResource(Res.string.registro_accion_crear),
                alPulsar = viewModel::crearCuenta,
                descripcionAccesible = stringResource(Res.string.a11y_registro_accion_crear),
                habilitado = estado.puedeEnviar,
                cargando = estado.creandoCuenta,
                etiquetaCargando = stringResource(Res.string.registro_estado_creando),
                descripcionCargando = stringResource(Res.string.a11y_registro_creando),
            )

            estado.errorRegistro?.let { error ->
                Spacer(Modifier.height(espaciado.medio))
                Text(
                    text = stringResource(error.recurso()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            Spacer(Modifier.height(espaciado.amplio))
            val descripcionVolver = stringResource(Res.string.a11y_registro_accion_ya_tengo_cuenta)
            TextButton(
                onClick = alVolverAlAcceso,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionVolver },
            ) {
                Text(
                    text = stringResource(Res.string.registro_accion_ya_tengo_cuenta),
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoSecundario,
                )
            }
            Spacer(Modifier.height(espaciado.respiro))
        }
    }
}

@Composable
private fun Formulario(estado: RegistroUiState, viewModel: RegistroViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        CampoTextoRellenoSalud(
            valor = estado.correo,
            alCambiar = viewModel::actualizarCorreo,
            etiqueta = stringResource(Res.string.registro_correo_campo),
            marcador = stringResource(Res.string.registro_correo_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_registro_correo_campo),
            tipoTeclado = KeyboardType.Email,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistro.CORREO_VACIO,
                ErrorCampoRegistro.CORREO_FORMATO,
            ),
        )
        CampoTextoRellenoSalud(
            valor = estado.contrasena,
            alCambiar = viewModel::actualizarContrasena,
            etiqueta = stringResource(Res.string.registro_contrasena_campo),
            marcador = stringResource(Res.string.registro_contrasena_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_registro_contrasena_campo),
            tipoTeclado = KeyboardType.Password,
            ocultarTexto = !estado.contrasenaVisible,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistro.CONTRASENA_VACIA,
                ErrorCampoRegistro.CONTRASENA_CORTA,
                ErrorCampoRegistro.CONTRASENA_DEBIL,
            ),
            accion = {
                AlternarVisibilidad(
                    visible = estado.contrasenaVisible,
                    alPulsar = viewModel::alternarVisibilidadContrasena,
                )
            },
        )
        Text(
            text = stringResource(Res.string.registro_contrasena_ayuda),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
        CampoTextoRellenoSalud(
            valor = estado.confirmacion,
            alCambiar = viewModel::actualizarConfirmacion,
            etiqueta = stringResource(Res.string.registro_confirmacion_campo),
            descripcionAccesible = stringResource(Res.string.a11y_registro_confirmacion_campo),
            tipoTeclado = KeyboardType.Password,
            ocultarTexto = !estado.contrasenaVisible,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistro.CONFIRMACION_NO_COINCIDE,
            ),
        )

        Spacer(Modifier.height(espaciado.minimo))
        CasillaDeclaracion(
            marcada = estado.avisoAceptado,
            alCambiar = viewModel::aceptarAvisoPrivacidad,
            etiqueta = stringResource(Res.string.registro_aviso_privacidad),
        )
        errorTexto(estado.erroresCampo, ErrorCampoRegistro.AVISO_NO_ACEPTADO)?.let { mensaje ->
            val descripcion = stringResource(Res.string.a11y_registro_aviso_privacidad)
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = descripcion },
            )
        }
    }
}

/** Mismo control que en el acceso: texto explicito en vez de un glifo sin nombre. */
@Composable
private fun AlternarVisibilidad(visible: Boolean, alPulsar: () -> Unit) {
    val descripcion = if (visible) {
        stringResource(Res.string.a11y_login_ocultar_contrasena)
    } else {
        stringResource(Res.string.a11y_login_mostrar_contrasena)
    }
    TextButton(
        onClick = alPulsar,
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcion },
    ) {
        Text(
            text = if (visible) {
                stringResource(Res.string.login_accion_ocultar_contrasena)
            } else {
                stringResource(Res.string.login_accion_mostrar_contrasena)
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Primer error presente de los que afectan a un campo, ya traducido. */
@Composable
private fun errorTexto(
    errores: List<ErrorCampoRegistro>,
    vararg propios: ErrorCampoRegistro,
): String? = errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }
