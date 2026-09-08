package com.eter.salud.ui.profesional

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.profesional.HomeProfesionalUiState
import com.eter.salud.presentation.profesional.HomeProfesionalViewModel
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.ColoresSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_home_accion_cerrar_sesion
import salud.shared.generated.resources.a11y_profesional_home_accion_agenda
import salud.shared.generated.resources.a11y_profesional_home_accion_escanear
import salud.shared.generated.resources.a11y_profesional_home_cargando
import salud.shared.generated.resources.a11y_profesional_home_estado_cedula
import salud.shared.generated.resources.a11y_profesional_home_paciente
import salud.shared.generated.resources.a11y_profesional_home_saludo
import salud.shared.generated.resources.home_accion_cerrar_sesion
import salud.shared.generated.resources.profesional_home_accion_agenda
import salud.shared.generated.resources.profesional_home_accion_escanear
import salud.shared.generated.resources.profesional_home_cedula_pendiente
import salud.shared.generated.resources.profesional_home_cedula_verificada
import salud.shared.generated.resources.profesional_home_estado_cargando
import salud.shared.generated.resources.profesional_home_estado_error
import salud.shared.generated.resources.profesional_home_pacientes_sin_datos
import salud.shared.generated.resources.profesional_home_pacientes_titulo
import salud.shared.generated.resources.profesional_home_saludo

/**
 * Panel principal del profesional de la salud.
 *
 * Es la unica puerta hacia el escaner de emergencia: el boton masivo de aqui
 * es la unica forma de instanciarlo, y solo se llega a esta pantalla tras
 * autenticarse en [LoginProfesionalScreen]. Transmite autoridad y orden con
 * jerarquia tipografica, no con adornos: cabecera de identidad, la accion de
 * emergencia dominando la parte superior, y la cartera de pacientes debajo.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, color y espaciado solo
 * por tokens semanticos, Modo Oscuro automatico, y margenes de seguridad
 * inferiores para que ningun boton colisione con los gestos del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeProfesionalScreen(
    viewModel: HomeProfesionalViewModel,
    modifier: Modifier = Modifier,
    alEscanearTarjeta: () -> Unit = {},
    alAbrirAgenda: () -> Unit = {},
    alAbrirChat: (PacienteVinculado) -> Unit = {},
    alCerrarSesion: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    LaunchedEffect(estado.idMedico) { viewModel.cargar() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            Res.string.profesional_home_saludo,
                            estado.tratamiento,
                            estado.apellidos,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    val descripcion = stringResource(Res.string.a11y_home_accion_cerrar_sesion)
                    TextButton(
                        onClick = alCerrarSesion,
                        modifier = Modifier
                            .heightIn(min = AreaTactilMinima)
                            .semantics { contentDescription = descripcion },
                    ) {
                        Text(
                            text = stringResource(Res.string.home_accion_cerrar_sesion),
                            color = MaterialTheme.colorScheme.error,
                        )
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
            verticalArrangement = Arrangement.spacedBy(espaciado.generoso),
        ) {
            Spacer(Modifier.height(espaciado.minimo))
            Cabecera(estado)
            AccionEscaneo(alPulsar = alEscanearTarjeta)
            AccionAgenda(alPulsar = alAbrirAgenda)

            when {
                estado.cargando -> IndicadorCargando()
                estado.errorCarga -> MensajeError()
                else -> PacientesVinculados(estado.pacientesVinculados, alAbrirChat)
            }
            Spacer(Modifier.height(espaciado.respiro))
        }
    }
}

/** Saludo formal y estado de la Cedula Profesional: quien eres y si estas habilitado. */
@Composable
private fun Cabecera(estado: HomeProfesionalUiState) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val saludo = stringResource(
        Res.string.profesional_home_saludo,
        estado.tratamiento,
        estado.apellidos,
    )
    val anuncioSaludo = stringResource(
        Res.string.a11y_profesional_home_saludo,
        estado.tratamiento,
        estado.apellidos,
    )
    val estadoCedula = if (estado.cedulaVerificada) {
        stringResource(Res.string.profesional_home_cedula_verificada)
    } else {
        stringResource(Res.string.profesional_home_cedula_pendiente)
    }
    val anuncioCedula = stringResource(Res.string.a11y_profesional_home_estado_cedula, estadoCedula)

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = saludo,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics {
                heading()
                contentDescription = anuncioSaludo
            },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = estadoCedula,
            style = MaterialTheme.typography.bodyMedium,
            // Indicador sutil, no una alerta: verificada usa el tono de exito,
            // pendiente se queda en el gris de apoyo, nunca en rojo critico.
            color = if (estado.cedulaVerificada) colores.exito else colores.textoSecundario,
            modifier = Modifier.semantics { contentDescription = anuncioCedula },
        )
    }
}

/**
 * Accion principal de emergencia: la tarjeta mas destacada de la pantalla, con
 * area tactil deliberadamente exagerada para acertarle sin precision mientras
 * se corre. Es la unica forma de abrir el escaner de emergencia en toda la app.
 */
@Composable
private fun AccionEscaneo(alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_profesional_home_accion_escanear)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_ACCION_ESCANEO)
            .clickable(onClick = alPulsar)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.acentoAccion,
        shape = RoundedCornerShape(espaciado.amplio),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(espaciado.generoso),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            IconoSalud(
                glifo = GlifoSalud.ONDAS_NFC,
                lado = TAMANO_ICONO_ESCANEO,
                color = colores.sobreAcentoAccion,
            )
            Text(
                text = stringResource(Res.string.profesional_home_accion_escanear),
                style = MaterialTheme.typography.headlineSmall,
                color = colores.sobreAcentoAccion,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Entrada al calendario de citas.
 *
 * Va en tarjeta y no en el turquesa de la accion principal a proposito: el
 * escaner de emergencia tiene que seguir siendo el unico elemento dominante de
 * esta pantalla. Una agenda compitiendo en peso visual con el boton que se pulsa
 * ante un paciente inconsciente seria una jerarquia equivocada.
 */
@Composable
private fun AccionAgenda(alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_profesional_home_accion_agenda)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(onClick = alPulsar)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(espaciado.amplio),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            IconoSalud(glifo = GlifoSalud.HISTORIAL, color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(Res.string.profesional_home_accion_agenda),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Semaforo de riesgo de la cartera de pacientes vinculados. */
@Composable
private fun PacientesVinculados(
    pacientes: List<PacienteVinculado>,
    alAbrirChat: (PacienteVinculado) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.profesional_home_pacientes_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.medio))
        if (pacientes.isEmpty()) {
            Text(
                text = stringResource(Res.string.profesional_home_pacientes_sin_datos),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
            return@Column
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colores.fondoTarjeta,
            shape = RoundedCornerShape(espaciado.medio),
        ) {
            Column(Modifier.padding(horizontal = espaciado.amplio)) {
                pacientes.forEachIndexed { indice, paciente ->
                    FilaPaciente(paciente = paciente, alAbrir = { alAbrirChat(paciente) })
                    if (indice != pacientes.lastIndex) {
                        HorizontalDivider(color = colores.separador)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaPaciente(paciente: PacienteVinculado, alAbrir: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val etiquetaRiesgo = stringResource(paciente.riesgo.recurso())
    val descripcion = stringResource(
        Res.string.a11y_profesional_home_paciente,
        paciente.nombreCompleto,
        etiquetaRiesgo,
    )

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
            text = paciente.nombreCompleto,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = etiquetaRiesgo,
            style = MaterialTheme.typography.labelLarge,
            color = paciente.riesgo.color(colores),
        )
    }
}

/** Color del semaforo: solo el riesgo alto reclama el tono critico. */
@Composable
private fun RiesgoPaciente.color(colores: ColoresSalud) = when (this) {
    RiesgoPaciente.ALTO -> colores.acentoCritico
    RiesgoPaciente.MEDIO -> MaterialTheme.colorScheme.onSurfaceVariant
    RiesgoPaciente.BAJO -> colores.exito
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_profesional_home_cargando)
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
            text = stringResource(Res.string.profesional_home_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MensajeError() {
    Text(
        text = stringResource(Res.string.profesional_home_estado_error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

private val ALTO_ACCION_ESCANEO = 176.dp
private val TAMANO_ICONO_ESCANEO = 48.dp
