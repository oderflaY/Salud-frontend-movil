package com.eter.salud.ui.login

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
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.presentation.login.ErrorCampoLogin
import com.eter.salud.presentation.login.LoginUiState
import com.eter.salud.presentation.login.LoginViewModel
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
import salud.shared.generated.resources.a11y_login_accion_acceso_profesional
import salud.shared.generated.resources.a11y_login_accion_entrar
import salud.shared.generated.resources.a11y_login_accion_recuperar
import salud.shared.generated.resources.a11y_login_accion_registrarse
import salud.shared.generated.resources.a11y_login_autenticando
import salud.shared.generated.resources.a11y_login_contrasena_campo
import salud.shared.generated.resources.a11y_login_correo_campo
import salud.shared.generated.resources.a11y_login_mostrar_contrasena
import salud.shared.generated.resources.a11y_login_ocultar_contrasena
import salud.shared.generated.resources.login_accion_entrar
import salud.shared.generated.resources.login_accion_mostrar_contrasena
import salud.shared.generated.resources.login_accion_ocultar_contrasena
import salud.shared.generated.resources.login_accion_recuperar
import salud.shared.generated.resources.login_accion_registrarse
import salud.shared.generated.resources.login_contrasena_campo
import salud.shared.generated.resources.login_contrasena_placeholder
import salud.shared.generated.resources.login_correo_campo
import salud.shared.generated.resources.login_correo_placeholder
import salud.shared.generated.resources.login_divisor
import salud.shared.generated.resources.login_estado_autenticando
import salud.shared.generated.resources.login_subtitulo
import salud.shared.generated.resources.login_titulo
import salud.shared.generated.resources.profesional_login_titulo

/**
 * Pantalla de acceso del paciente.
 *
 * Jerarquia vertical: isotipo, bienvenida, formulario, accion principal y, bien
 * separadas, las acciones secundarias. Un solo elemento domina la pantalla.
 *
 * Cumplimiento del DM:
 *  - Cero texto literal: todo sale de `strings.xml`.
 *  - Sin emojis; el isotipo es vectorial y hereda el color del tema.
 *  - Color y espaciado solo por tokens semanticos, con Modo Claro y Oscuro
 *    resueltos en `SaludTheme`.
 *  - Cada campo y cada boton declaran su etiqueta para TalkBack / VoiceOver, y
 *    el fallo de acceso se anuncia como region viva.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
    alIniciarSesion: (SesionPaciente) -> Unit = {},
    alRecuperarContrasena: () -> Unit = {},
    alRegistrarse: () -> Unit = {},
    alAccesoProfesional: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    LaunchedEffect(estado.sesion) { estado.sesion?.let(alIniciarSesion) }

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
        Formulario(
            estado = estado,
            viewModel = viewModel,
            alRecuperarContrasena = alRecuperarContrasena,
        )

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
            etiqueta = stringResource(Res.string.login_accion_registrarse),
            alPulsar = alRegistrarse,
            descripcionAccesible = stringResource(Res.string.a11y_login_accion_registrarse),
        )

        // Jerarquia mas baja que registrarse: es la salida a OTRO portal, no una
        // accion del paciente, y nunca abre nada por si sola -- lleva a un
        // acceso que exige autenticarse antes de ver cualquier dato clinico.
        // El escaner de emergencia jamas se instancia desde esta pantalla.
        Spacer(Modifier.height(espaciado.amplio))
        val descripcionProfesional = stringResource(Res.string.a11y_login_accion_acceso_profesional)
        TextButton(
            onClick = alAccesoProfesional,
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionProfesional },
        ) {
            Text(
                text = stringResource(Res.string.profesional_login_titulo),
                style = MaterialTheme.typography.labelLarge,
                color = LocalColoresSalud.current.textoSecundario,
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
            text = stringResource(Res.string.login_titulo),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.login_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

/** Correo y contrasena agrupados, con el enlace de recuperacion a la derecha. */
@Composable
private fun Formulario(
    estado: LoginUiState,
    viewModel: LoginViewModel,
    alRecuperarContrasena: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

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
                ErrorCampoLogin.CORREO_VACIO,
                ErrorCampoLogin.CORREO_FORMATO,
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
                ErrorCampoLogin.CONTRASENA_VACIA,
                ErrorCampoLogin.CONTRASENA_CORTA,
            ),
            accion = {
                AlternarVisibilidad(
                    visible = estado.contrasenaVisible,
                    alPulsar = viewModel::alternarVisibilidadContrasena,
                )
            },
        )
        // Pegado al campo al que pertenece y en jerarquia baja: es una salida de
        // emergencia, no una accion que compita con la principal.
        val descripcionRecuperar = stringResource(Res.string.a11y_login_accion_recuperar)
        TextButton(
            onClick = alRecuperarContrasena,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = AreaTactilMinima)
                .semantics { contentDescription = descripcionRecuperar },
        ) {
            Text(
                text = stringResource(Res.string.login_accion_recuperar),
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
        }
    }
}

/**
 * Alterna la visibilidad con texto en lugar de un icono: el paquete de iconos
 * del sistema no esta disponible en este objetivo y una etiqueta explicita se
 * lee mejor con TalkBack y VoiceOver que un glifo sin nombre.
 */
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
    errores: List<ErrorCampoLogin>,
    vararg propios: ErrorCampoLogin,
): String? = errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }
