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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.presentation.agenda.AgendaMedicoUiState
import com.eter.salud.presentation.agenda.AgendaMedicoViewModel
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_agenda_accion_iniciar_consulta
import salud.shared.generated.resources.a11y_agenda_accion_nuevo_horario
import salud.shared.generated.resources.agenda_accion_cancelar_cita
import salud.shared.generated.resources.agenda_accion_cerrar_detalle
import salud.shared.generated.resources.agenda_accion_confirmar
import salud.shared.generated.resources.agenda_accion_iniciar_consulta
import salud.shared.generated.resources.agenda_accion_liberar_bloqueo
import salud.shared.generated.resources.agenda_accion_no_asistio
import salud.shared.generated.resources.agenda_accion_reprogramar
import salud.shared.generated.resources.agenda_detalle_bloqueo
import salud.shared.generated.resources.agenda_detalle_contacto
import salud.shared.generated.resources.agenda_detalle_folio
import salud.shared.generated.resources.agenda_detalle_motivo
import salud.shared.generated.resources.agenda_detalle_sin_motivo
import salud.shared.generated.resources.agenda_reprogramar_titulo
import salud.shared.generated.resources.agenda_reprogramar_vacio
import salud.shared.generated.resources.rango_horas

/**
 * Ventana de detalle de una cita del calendario.
 *
 * Es una hoja modal nativa y no una pantalla aparte porque el medico la abre y
 * la cierra decenas de veces en una jornada: sacarlo del calendario para cada
 * consulta le costaria el contexto de lo que estaba mirando.
 *
 * "Iniciar consulta" hace dos cosas en un solo toque: mueve la cita a EN CURSO y
 * abre el expediente. Separarlas obligaria a recordar marcar el estado a mano, y
 * una agenda con todo en amarillo deja de informar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaDetalleCita(
    cita: Cita,
    estado: AgendaMedicoUiState,
    viewModel: AgendaMedicoViewModel,
    alAbrirExpediente: (Cita) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::cerrarDetalle,
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
            if (estado.reprogramando) {
                ContenidoReprogramacion(estado = estado, viewModel = viewModel)
            } else {
                Encabezado(cita)
                if (cita.esBloqueo) {
                    AccionesDeBloqueo(cita = cita, viewModel = viewModel)
                } else {
                    DatosDelPaciente(cita)
                    AccionesDeCita(
                        cita = cita,
                        viewModel = viewModel,
                        alAbrirExpediente = alAbrirExpediente,
                    )
                }
            }
            BotonSecundarioSalud(
                etiqueta = stringResource(Res.string.agenda_accion_cerrar_detalle),
                alPulsar = viewModel::cerrarDetalle,
                descripcionAccesible = stringResource(Res.string.agenda_accion_cerrar_detalle),
            )
        }
    }
}

@Composable
private fun Encabezado(cita: Cita) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column {
        Text(
            text = if (cita.esBloqueo) {
                cita.notaBloqueo.ifBlank { stringResource(Res.string.agenda_detalle_bloqueo) }
            } else {
                cita.contacto?.nombreCompleto.orEmpty()
            },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = fechaLarga(cita.fecha) + " · " +
                stringResource(Res.string.rango_horas, cita.horaInicio, cita.horaFin),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.compacto))
        InsigniaEstado(cita.estado)
        if (cita.folio.isNotBlank()) {
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.agenda_detalle_folio, cita.folio),
                style = MaterialTheme.typography.labelSmall,
                color = colores.textoSecundario,
            )
        }
    }
}

@Composable
private fun DatosDelPaciente(cita: Cita) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val contacto = cita.contacto ?: return

    HorizontalDivider(color = colores.separador)
    Column {
        Text(
            text = stringResource(Res.string.agenda_detalle_contacto),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        Text(text = contacto.telefono, style = MaterialTheme.typography.bodyLarge)
        Text(text = contacto.correo, style = MaterialTheme.typography.bodyMedium)
    }
    Column {
        Text(
            text = stringResource(Res.string.agenda_detalle_motivo),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        Text(
            text = contacto.motivo.ifBlank { stringResource(Res.string.agenda_detalle_sin_motivo) },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AccionesDeCita(
    cita: Cita,
    viewModel: AgendaMedicoViewModel,
    alAbrirExpediente: (Cita) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current

    // DOS niveles, no seis botones apilados.
    //
    // Antes esta hoja era un muro de seis rectangulos del mismo ancho y casi el
    // mismo alto, distinguidos solo por color: iniciar, confirmar, reprogramar,
    // inasistencia, cancelar y cerrar. Nada decia cual era la accion esperada, y
    // las dos destructivas estaban al mismo alcance que las demas.
    //
    // Ahora hay una accion principal a ancho completo, las de gestion en una
    // fila compacta, y las destructivas separadas por un filete al final.
    Column(verticalArrangement = Arrangement.spacedBy(espaciado.medio)) {
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.agenda_accion_iniciar_consulta),
            alPulsar = {
                viewModel.iniciarConsulta(cita)
                alAbrirExpediente(cita)
            },
            descripcionAccesible = stringResource(
                Res.string.a11y_agenda_accion_iniciar_consulta,
                cita.contacto?.nombreCompleto.orEmpty(),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            // Confirmar solo aparece mientras signifique algo: sobre una cita ya
            // confirmada seria una accion que no cambia nada.
            if (cita.estado == EstadoCita.PENDIENTE) {
                AccionCompacta(
                    etiqueta = stringResource(Res.string.agenda_accion_confirmar),
                    alPulsar = { viewModel.confirmarCita(cita) },
                    modifier = Modifier.weight(1f),
                )
            }
            AccionCompacta(
                etiqueta = stringResource(Res.string.agenda_accion_reprogramar),
                alPulsar = viewModel::abrirReprogramacion,
                modifier = Modifier.weight(1f),
            )
        }

        // Un filete separa lo reversible de lo que no lo es. Sin el, marcar una
        // inasistencia estaba a la misma distancia visual que reprogramar.
        HorizontalDivider(color = LocalColoresSalud.current.separador)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            AccionCompacta(
                etiqueta = stringResource(Res.string.agenda_accion_no_asistio),
                alPulsar = { viewModel.marcarInasistencia(cita) },
                modifier = Modifier.weight(1f),
                tinta = LocalColoresSalud.current.textoSecundario,
            )
            AccionCompacta(
                etiqueta = stringResource(Res.string.agenda_accion_cancelar_cita),
                alPulsar = { viewModel.cancelarCita(cita) },
                modifier = Modifier.weight(1f),
                tinta = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Accion de gestion: media altura y medio ancho.
 *
 * Existe para que las acciones secundarias dejen de parecerse a la principal.
 * Un boton de ancho completo anuncia "esto es lo que se hace aqui"; cuatro
 * seguidos no anuncian nada.
 */
@Composable
private fun AccionCompacta(
    etiqueta: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    tinta: Color = MaterialTheme.colorScheme.primary,
) {
    val espaciado = LocalEspaciadoSalud.current
    TextButton(
        onClick = alPulsar,
        modifier = modifier
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = etiqueta },
        shape = FormaSalud.media,
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelLarge,
            color = tinta,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = espaciado.minimo),
        )
    }
}

@Composable
private fun AccionesDeBloqueo(cita: Cita, viewModel: AgendaMedicoViewModel) {
    BotonSecundarioSalud(
        etiqueta = stringResource(Res.string.agenda_accion_liberar_bloqueo),
        alPulsar = { viewModel.liberarBloqueo(cita) },
        descripcionAccesible = stringResource(Res.string.agenda_accion_liberar_bloqueo),
    )
}

@Composable
private fun ContenidoReprogramacion(
    estado: AgendaMedicoUiState,
    viewModel: AgendaMedicoViewModel,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Text(
        text = stringResource(Res.string.agenda_reprogramar_titulo),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    ListaDeFranjasPorFecha(
        franjas = estado.franjasParaReprogramar,
        textoVacio = stringResource(Res.string.agenda_reprogramar_vacio),
        alElegir = viewModel::reprogramarEn,
    )
    BotonSecundarioSalud(
        etiqueta = stringResource(Res.string.agenda_accion_cerrar_detalle),
        alPulsar = viewModel::cancelarReprogramacion,
        descripcionAccesible = stringResource(Res.string.agenda_accion_cerrar_detalle),
    )
}

/**
 * Lista de horarios libres agrupados por fecha, o el aviso de que no hay
 * ninguno. La comparte [ContenidoReprogramacion] (mover una cita existente) y
 * la hoja de "Agendar cita" (proponerle una hora nueva a un paciente): las dos
 * son la misma pregunta -- "cual de estos horarios libres" -- y solo cambia
 * que pasa al elegir uno.
 */
@Composable
internal fun ListaDeFranjasPorFecha(
    franjas: List<FranjaAgenda>,
    textoVacio: String,
    alElegir: (FranjaAgenda) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    if (franjas.isEmpty()) {
        Text(
            text = textoVacio,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        return
    }
    franjas.groupBy { it.fecha }.forEach { (fecha, delDia) ->
        Text(
            text = fechaLarga(fecha),
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        delDia.forEach { franja -> FilaDeFranjaElegible(franja, alElegir) }
        Spacer(Modifier.height(espaciado.compacto))
    }
}

@Composable
private fun FilaDeFranjaElegible(franja: FranjaAgenda, alElegir: (FranjaAgenda) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(
        Res.string.a11y_agenda_accion_nuevo_horario,
        franja.horaInicio,
        fechaLarga(franja.fecha),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable { alElegir(franja) }
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = stringResource(Res.string.rango_horas, franja.horaInicio, franja.horaFin),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = espaciado.compacto),
        )
    }
}
