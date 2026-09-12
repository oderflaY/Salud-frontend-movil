package com.eter.salud.ui.profesional

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.presentation.profesional.ErrorCampoLoginProfesional
import com.eter.salud.presentation.profesional.LoginProfesionalUiState
import com.eter.salud.presentation.profesional.LoginProfesionalViewModel
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.DivisorConTexto
import com.eter.salud.ui.componentes.IsotipoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_login_accion_entrar
import salud.shared.generated.resources.a11y_login_autenticando
import salud.shared.generated.resources.a11y_login_contrasena_campo
import salud.shared.generated.resources.a11y_login_correo_campo
import salud.shared.generated.resources.a11y_login_mostrar_contrasena
import salud.shared.generated.resources.a11y_login_ocultar_contrasena
import salud.shared.generated.resources.a11y_profesional_login_accion_crear_cuenta
import salud.shared.generated.resources.a11y_profesional_login_accion_volver_paciente
import salud.shared.generated.resources.login_accion_entrar
import salud.shared.generated.resources.login_accion_mostrar_contrasena
import salud.shared.generated.resources.login_accion_ocultar_contrasena
import salud.shared.generated.resources.login_contrasena_campo
import salud.shared.generated.resources.login_contrasena_placeholder
import salud.shared.generated.resources.login_correo_campo
import salud.shared.generated.resources.login_correo_placeholder
import salud.shared.generated.resources.login_divisor
import salud.shared.generated.resources.login_estado_autenticando
import salud.shared.generated.resources.profesional_login_accion_crear_cuenta
import salud.shared.generated.resources.profesional_login_accion_volver_paciente
import salud.shared.generated.resources.profesional_login_subtitulo
import salud.shared.generated.resources.profesional_login_titulo

/**
 * Pantalla de acceso de personal medico.
 *
 * Es la unica puerta hacia el panel del profesional y, por extension, hacia el
 * escaner de emergencia: sin autenticarse aqui no existe forma de llegar al
 * escaner desde la app. Misma gramatica visual que el acceso de paciente
 * (campos rellenos, una sola accion dominante), con textos y salidas propias
 * de este portal.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, color y espaciado solo
 * por tokens semanticos, Modo Oscuro automatico, y etiquetas de accesibilidad
 * en cada control.
 */
@Composable
fun LoginProfesionalScreen(
    viewModel: LoginProfesionalViewModel,
    modifier: Modifier = Modifier,
    alIniciarSesion: (SesionProfesional) -> Unit = {},
    alRegistrarse: () -> Unit = {},
    alVolverAPaciente: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    LaunchedEffect(estado.sesion) {
        estado.sesion?.let { sesion ->
            alIniciarSesion(sesion)
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
        IsotipoSalud()
        Spacer(Modifier.height(espaciado.generoso))
        Cabecera()

        Spacer(Modifier.height(espaciado.generoso))
        Formulario(estado, viewModel)

        Spacer(Modifier.height(espaciado.medio))
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.login_accion_entrar),
            alPulsar = viewModel::iniciarSesion,
            descripcionAccesible = stringResource(Res.string.a11y_login_accion_entrar),
            habilitado = estado.puedeEnviar,
            cargando = estado.autenticando,
            etiquetaCargando = stringResource(Res.string.login_estado_autenticando),
            descripcionCargando = stringResource(Res.string.a11y_login_autenticando),
        )

        estado.errorAutenticacion?.let { error ->
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

        Spacer(Modifier.height(espaciado.respiro))
        DivisorConTexto(stringResource(Res.string.login_divisor))
        Spacer(Modifier.height(espaciado.amplio))
        BotonSecundarioSalud(
            etiqueta = stringResource(Res.string.profesional_login_accion_crear_cuenta),
            alPulsar = alRegistrarse,
            descripcionAccesible = stringResource(Res.string.a11y_profesional_login_accion_crear_cuenta),
        )

        // Jerarquia mas baja: es la salida de un portal a otro, no una accion
        // que compita con entrar o crear cuenta.
        Spacer(Modifier.height(espaciado.amplio))
        val descripcionVolver = stringResource(Res.string.a11y_profesional_login_accion_volver_paciente)
        TextButton(
            onClick = alVolverAPaciente,
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionVolver },
        ) {
            Text(
                text = stringResource(Res.string.profesional_login_accion_volver_paciente),
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
        }
        Spacer(Modifier.height(espaciado.respiro))
    }
}

@Composable
private fun Cabecera() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.profesional_login_titulo),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.profesional_login_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

@Composable
private fun Formulario(estado: LoginProfesionalUiState, viewModel: LoginProfesionalViewModel) {
    val espaciado = LocalEspaciadoSalud.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        CampoTextoRellenoSalud(
            valor = estado.correo,
            alCambiar = viewModel::actualizarCorreo,
            etiqueta = stringResource(Res.string.login_correo_campo),
            marcador = stringResource(Res.string.login_correo_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_login_correo_campo),
            tipoTeclado = KeyboardType.Email,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoLoginProfesional.CORREO_VACIO,
                ErrorCampoLoginProfesional.CORREO_FORMATO,
            ),
        )
        CampoTextoRellenoSalud(
            valor = estado.contrasena,
            alCambiar = viewModel::actualizarContrasena,
            etiqueta = stringResource(Res.string.login_contrasena_campo),
            marcador = stringResource(Res.string.login_contrasena_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_login_contrasena_campo),
            tipoTeclado = KeyboardType.Password,
            ocultarTexto = !estado.contrasenaVisible,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoLoginProfesional.CONTRASENA_VACIA,
                ErrorCampoLoginProfesional.CONTRASENA_CORTA,
            ),
            accion = {
                AlternarVisibilidadProfesional(
                    visible = estado.contrasenaVisible,
                    alPulsar = viewModel::alternarVisibilidadContrasena,
                )
            },
        )
    }
}

@Composable
internal fun AlternarVisibilidadProfesional(visible: Boolean, alPulsar: () -> Unit) {
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
    errores: List<ErrorCampoLoginProfesional>,
    vararg propios: ErrorCampoLoginProfesional,
): String? = errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }
