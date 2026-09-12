package com.eter.salud.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.presentation.agenda.AgendaMedicoUiState
import com.eter.salud.presentation.agenda.AgendaMedicoViewModel
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_agenda_propuesta_campo_motivo
import salud.shared.generated.resources.a11y_agenda_propuesta_elegir_paciente
import salud.shared.generated.resources.agenda_propuesta_campo_motivo
import salud.shared.generated.resources.agenda_propuesta_error
import salud.shared.generated.resources.agenda_propuesta_marcador_motivo
import salud.shared.generated.resources.agenda_propuesta_sin_horarios
import salud.shared.generated.resources.agenda_propuesta_sin_pacientes
import salud.shared.generated.resources.agenda_propuesta_titulo
import salud.shared.generated.resources.agenda_propuesta_titulo_horario

/**
 * Hoja de "Agendar cita": el medico ofrece una hora, en dos pasos.
 *
 * 1. Elige a QUIEN, de entre su propia cartera de pacientes vinculados. No hay
 *    busqueda de paciente nuevo aqui -- eso es el Directorio, y desde la agenda
 *    solo se le propone hora a alguien con quien ya existe una relacion clinica.
 * 2. Elige CUANDO, de sus propios horarios libres, con un motivo opcional.
 *
 * Reutiliza [ListaDeFranjasPorFecha]: es la misma pregunta que ya resuelve
 * [ContenidoReprogramacion], solo que el resultado de elegir una franja aqui es
 * una cita NUEVA en vez de mover una existente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaProponerCita(
    estado: AgendaMedicoUiState,
    viewModel: AgendaMedicoViewModel,
) {
    val espaciado = LocalEspaciadoSalud.current
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::cancelarPropuestaCita,
        sheetState = estadoHoja,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = espaciado.amplio)
                .margenInferiorSeguro(),
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            val paciente = estado.pacienteParaPropuesta
            if (paciente == null) {
                SelectorDePaciente(
                    pacientes = estado.pacientesVinculados,
                    alElegir = viewModel::elegirPacienteParaPropuesta,
                )
            } else {
                ContenidoDeHorario(
                    paciente = paciente,
                    estado = estado,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SelectorDePaciente(
    pacientes: List<PacienteVinculado>,
    alElegir: (PacienteVinculado) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Text(
        text = stringResource(Res.string.agenda_propuesta_titulo),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    if (pacientes.isEmpty()) {
        Text(
            text = stringResource(Res.string.agenda_propuesta_sin_pacientes),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        return
    }
    pacientes.forEach { paciente ->
        val descripcion = stringResource(
            Res.string.a11y_agenda_propuesta_elegir_paciente,
            paciente.nombreCompleto,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AreaTactilMinima)
                .clickable { alElegir(paciente) }
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        ) {
            Text(
                text = paciente.nombreCompleto,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = espaciado.compacto),
            )
        }
    }
}

@Composable
private fun ContenidoDeHorario(
    paciente: PacienteVinculado,
    estado: AgendaMedicoUiState,
    viewModel: AgendaMedicoViewModel,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Text(
        text = stringResource(Res.string.agenda_propuesta_titulo_horario, paciente.nombreCompleto),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )

    CampoTextoRellenoSalud(
        valor = estado.motivoPropuesta,
        alCambiar = viewModel::actualizarMotivoPropuesta,
        etiqueta = stringResource(Res.string.agenda_propuesta_campo_motivo),
        descripcionAccesible = stringResource(Res.string.a11y_agenda_propuesta_campo_motivo),
        marcador = stringResource(Res.string.agenda_propuesta_marcador_motivo),
        tipoTeclado = KeyboardType.Text,
    )

    if (estado.errorPropuesta) {
        Text(
            text = stringResource(Res.string.agenda_propuesta_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(espaciado.minimo))

    ListaDeFranjasPorFecha(
        franjas = estado.franjasParaPropuesta,
        textoVacio = stringResource(Res.string.agenda_propuesta_sin_horarios),
        alElegir = viewModel::confirmarPropuestaCita,
    )
}
