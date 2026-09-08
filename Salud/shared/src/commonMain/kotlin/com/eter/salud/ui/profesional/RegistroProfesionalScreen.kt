package com.eter.salud.ui.profesional

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
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.presentation.profesional.ErrorCampoRegistroProfesional
import com.eter.salud.presentation.profesional.RegistroProfesionalUiState
import com.eter.salud.presentation.profesional.RegistroProfesionalViewModel
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.CasillaDeclaracion
import com.eter.salud.ui.componentes.SelectorSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_login_mostrar_contrasena
import salud.shared.generated.resources.a11y_login_ocultar_contrasena
import salud.shared.generated.resources.a11y_profesional_registro_accion_crear
import salud.shared.generated.resources.a11y_profesional_registro_accion_ya_tengo_cuenta
import salud.shared.generated.resources.a11y_profesional_registro_cedula_campo
import salud.shared.generated.resources.a11y_profesional_registro_creando
import salud.shared.generated.resources.a11y_profesional_registro_tratamiento_campo
import salud.shared.generated.resources.a11y_registro_aviso_privacidad
import salud.shared.generated.resources.a11y_registro_confirmacion_campo
import salud.shared.generated.resources.a11y_registro_contrasena_campo
import salud.shared.generated.resources.a11y_registro_correo_campo
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.login_accion_mostrar_contrasena
import salud.shared.generated.resources.login_accion_ocultar_contrasena
import salud.shared.generated.resources.opciones_tratamiento_profesional
import salud.shared.generated.resources.profesional_registro_accion_crear
import salud.shared.generated.resources.profesional_registro_accion_ya_tengo_cuenta
import salud.shared.generated.resources.profesional_registro_apellidos_campo
import salud.shared.generated.resources.profesional_registro_apellidos_placeholder
import salud.shared.generated.resources.profesional_registro_cedula_campo
import salud.shared.generated.resources.profesional_registro_cedula_placeholder
import salud.shared.generated.resources.profesional_registro_estado_creando
import salud.shared.generated.resources.profesional_registro_nombre_campo
import salud.shared.generated.resources.profesional_registro_nombre_placeholder
import salud.shared.generated.resources.profesional_registro_subtitulo
import salud.shared.generated.resources.profesional_registro_titulo
import salud.shared.generated.resources.profesional_registro_tratamiento_campo
import salud.shared.generated.resources.registro_aviso_privacidad
import salud.shared.generated.resources.registro_confirmacion_campo
import salud.shared.generated.resources.registro_contrasena_ayuda
import salud.shared.generated.resources.registro_contrasena_campo
import salud.shared.generated.resources.registro_contrasena_placeholder
import salud.shared.generated.resources.registro_correo_campo
import salud.shared.generated.resources.registro_correo_placeholder

/**
 * Pantalla de alta de personal medico.
 *
 * A diferencia del alta de paciente, aqui la identidad y la Cedula Profesional
 * se capturan de una vez: DM_PerfilMedico.md liga la identidad legal del
 * profesional a su credencial de acceso desde el primer momento.
 *
 * Misma gramatica visual que el resto del portal: campos rellenos, una sola
 * accion dominante y barra superior para volver sin perder lo escrito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroProfesionalScreen(
    viewModel: RegistroProfesionalViewModel,
    modifier: Modifier = Modifier,
    alCrearCuenta: (SesionProfesional) -> Unit = {},
    alVolverAlAcceso: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    LaunchedEffect(estado.sesion) { estado.sesion?.let(alCrearCuenta) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.profesional_registro_titulo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    val descripcion = stringResource(Res.string.a11y_boton_atras)
                    TextButton(
                        onClick = alVolverAlAcceso,
                        modifier = Modifier
                            .heightIn(min = AreaTactilMinima)
                            .semantics { contentDescription = descripcion },
                    ) {
                        Text(stringResource(Res.string.accion_atras))
                    }
                },
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
                text = stringResource(Res.string.profesional_registro_titulo),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.profesional_registro_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )

            Spacer(Modifier.height(espaciado.generoso))
            Formulario(estado, viewModel)

            Spacer(Modifier.height(espaciado.amplio))
            BotonAccionPrincipal(
                etiqueta = stringResource(Res.string.profesional_registro_accion_crear),
                alPulsar = viewModel::crearCuenta,
                descripcionAccesible = stringResource(Res.string.a11y_profesional_registro_accion_crear),
                habilitado = estado.puedeEnviar,
                cargando = estado.creandoCuenta,
                etiquetaCargando = stringResource(Res.string.profesional_registro_estado_creando),
                descripcionCargando = stringResource(Res.string.a11y_profesional_registro_creando),
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
            val descripcionVolver =
                stringResource(Res.string.a11y_profesional_registro_accion_ya_tengo_cuenta)
            TextButton(
                onClick = alVolverAlAcceso,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionVolver },
            ) {
                Text(
                    text = stringResource(Res.string.profesional_registro_accion_ya_tengo_cuenta),
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoSecundario,
                )
            }
            Spacer(Modifier.height(espaciado.respiro))
        }
    }
}

@Composable
private fun Formulario(
    estado: RegistroProfesionalUiState,
    viewModel: RegistroProfesionalViewModel,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        CampoTextoRellenoSalud(
            valor = estado.nombre,
            alCambiar = viewModel::actualizarNombre,
            etiqueta = stringResource(Res.string.profesional_registro_nombre_campo),
            marcador = stringResource(Res.string.profesional_registro_nombre_placeholder),
            descripcionAccesible = stringResource(Res.string.profesional_registro_nombre_campo),
            error = errorTexto(estado.erroresCampo, ErrorCampoRegistroProfesional.NOMBRE_VACIO),
        )
        CampoTextoRellenoSalud(
            valor = estado.apellidos,
            alCambiar = viewModel::actualizarApellidos,
            etiqueta = stringResource(Res.string.profesional_registro_apellidos_campo),
            marcador = stringResource(Res.string.profesional_registro_apellidos_placeholder),
            descripcionAccesible = stringResource(Res.string.profesional_registro_apellidos_campo),
            error = errorTexto(estado.erroresCampo, ErrorCampoRegistroProfesional.APELLIDOS_VACIO),
        )
        SelectorSalud(
            etiqueta = stringResource(Res.string.profesional_registro_tratamiento_campo),
            seleccion = estado.tratamiento,
            opciones = stringArrayResource(Res.array.opciones_tratamiento_profesional),
            alSeleccionar = viewModel::actualizarTratamiento,
            descripcionAccesible = stringResource(Res.string.a11y_profesional_registro_tratamiento_campo),
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistroProfesional.TRATAMIENTO_VACIO,
            ),
        )
        CampoTextoRellenoSalud(
            valor = estado.cedulaProfesional,
            alCambiar = viewModel::actualizarCedulaProfesional,
            etiqueta = stringResource(Res.string.profesional_registro_cedula_campo),
            marcador = stringResource(Res.string.profesional_registro_cedula_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_profesional_registro_cedula_campo),
            tipoTeclado = KeyboardType.Number,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistroProfesional.CEDULA_VACIA,
                ErrorCampoRegistroProfesional.CEDULA_FORMATO,
            ),
        )
        CampoTextoRellenoSalud(
            valor = estado.correo,
            alCambiar = viewModel::actualizarCorreo,
            etiqueta = stringResource(Res.string.registro_correo_campo),
            marcador = stringResource(Res.string.registro_correo_placeholder),
            descripcionAccesible = stringResource(Res.string.a11y_registro_correo_campo),
            tipoTeclado = KeyboardType.Email,
            error = errorTexto(
                estado.erroresCampo,
                ErrorCampoRegistroProfesional.CORREO_VACIO,
                ErrorCampoRegistroProfesional.CORREO_FORMATO,
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
                ErrorCampoRegistroProfesional.CONTRASENA_VACIA,
                ErrorCampoRegistroProfesional.CONTRASENA_CORTA,
                ErrorCampoRegistroProfesional.CONTRASENA_DEBIL,
            ),
            accion = {
                AlternarVisibilidadProfesional(
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
                ErrorCampoRegistroProfesional.CONFIRMACION_NO_COINCIDE,
            ),
        )

        Spacer(Modifier.height(espaciado.minimo))
        CasillaDeclaracion(
            marcada = estado.avisoAceptado,
            alCambiar = viewModel::aceptarAvisoPrivacidad,
            etiqueta = stringResource(Res.string.registro_aviso_privacidad),
        )
        errorTexto(estado.erroresCampo, ErrorCampoRegistroProfesional.AVISO_NO_ACEPTADO)?.let {
            val descripcion = stringResource(Res.string.a11y_registro_aviso_privacidad)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = descripcion },
            )
        }
    }
}

/** Primer error presente de los que afectan a un campo, ya traducido. */
@Composable
private fun errorTexto(
    errores: List<ErrorCampoRegistroProfesional>,
    vararg propios: ErrorCampoRegistroProfesional,
): String? = errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }
