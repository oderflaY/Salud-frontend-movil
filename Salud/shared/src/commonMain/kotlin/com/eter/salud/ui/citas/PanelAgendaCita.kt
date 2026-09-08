package com.eter.salud.ui.citas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.eter.salud.domain.model.CanalNotificacion
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.model.ReservaFranja
import com.eter.salud.presentation.citas.AgendaCitaUiState
import com.eter.salud.presentation.citas.AgendaCitaViewModel
import com.eter.salud.presentation.citas.ErrorAgendaCita
import com.eter.salud.presentation.citas.ErrorCampoCita
import com.eter.salud.presentation.citas.PasoAgenda
import com.eter.salud.ui.agenda.fechaCorta
import com.eter.salud.ui.agenda.fechaLarga
import com.eter.salud.ui.agenda.recurso
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.directorio.recurso
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_cita_accion_cancelar
import salud.shared.generated.resources.a11y_cita_accion_confirmar
import salud.shared.generated.resources.a11y_cita_accion_continuar
import salud.shared.generated.resources.a11y_cita_accion_especialidad
import salud.shared.generated.resources.a11y_cita_accion_franja
import salud.shared.generated.resources.a11y_cita_accion_medico
import salud.shared.generated.resources.a11y_cita_accion_mi_medico
import salud.shared.generated.resources.a11y_cita_campo_correo
import salud.shared.generated.resources.a11y_cita_campo_motivo
import salud.shared.generated.resources.a11y_cita_campo_nombre
import salud.shared.generated.resources.a11y_cita_campo_telefono
import salud.shared.generated.resources.a11y_cita_cargando
import salud.shared.generated.resources.a11y_cita_confirmada
import salud.shared.generated.resources.a11y_cita_panel
import salud.shared.generated.resources.cita_accion_cambiar_horario
import salud.shared.generated.resources.cita_accion_cancelar
import salud.shared.generated.resources.cita_accion_cerrar
import salud.shared.generated.resources.cita_accion_confirmar
import salud.shared.generated.resources.cita_accion_continuar
import salud.shared.generated.resources.cita_accion_corregir_datos
import salud.shared.generated.resources.cita_accion_mi_medico
import salud.shared.generated.resources.cita_campo_correo
import salud.shared.generated.resources.cita_campo_motivo
import salud.shared.generated.resources.cita_campo_nombre
import salud.shared.generated.resources.cita_campo_telefono
import salud.shared.generated.resources.cita_confirmada_canal_correo
import salud.shared.generated.resources.cita_confirmada_canal_whatsapp
import salud.shared.generated.resources.cita_confirmada_detalle
import salud.shared.generated.resources.cita_confirmada_folio
import salud.shared.generated.resources.cita_confirmada_sin_canales
import salud.shared.generated.resources.cita_confirmada_titulo
import salud.shared.generated.resources.cita_estado_cargando
import salud.shared.generated.resources.cita_marcador_motivo
import salud.shared.generated.resources.cita_panel_titulo
import salud.shared.generated.resources.cita_paso_datos_titulo
import salud.shared.generated.resources.cita_paso_especialidad_ayuda
import salud.shared.generated.resources.cita_paso_especialidad_titulo
import salud.shared.generated.resources.cita_paso_franja_ayuda
import salud.shared.generated.resources.cita_paso_franja_titulo
import salud.shared.generated.resources.cita_paso_medico_titulo
import salud.shared.generated.resources.cita_paso_medico_vacio
import salud.shared.generated.resources.cita_paso_resumen_titulo
import salud.shared.generated.resources.cita_resumen_fecha
import salud.shared.generated.resources.cita_resumen_hora
import salud.shared.generated.resources.cita_resumen_medico
import salud.shared.generated.resources.cita_resumen_motivo
import salud.shared.generated.resources.cita_resumen_paciente
import salud.shared.generated.resources.rango_horas

/**
 * Panel conversacional para agendar una cita, montado sobre el chat.
 *
 * Se presenta como hoja modal nativa y no como burbujas dentro de la
 * conversacion por una razon practica: los pasos son un formulario con estado
 * (campos, validacion, un horario apartado con cuenta atras), y un formulario
 * intercalado entre mensajes se pierde en cuanto llega uno nuevo y la lista
 * scrollea. La hoja mantiene el chat visible detras y deja salir de un toque.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, color y espaciado solo
 * por tokens semanticos, Modo Oscuro automatico, area tactil minima en cada
 * opcion y respeto del teclado a traves de [margenInferiorSeguro].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelAgendaCita(
    viewModel: AgendaCitaViewModel,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    if (!estado.activo) return

    val espaciado = LocalEspaciadoSalud.current
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val descripcionPanel = stringResource(Res.string.a11y_cita_panel)

    ModalBottomSheet(
        onDismissRequest = viewModel::cancelar,
        sheetState = estadoHoja,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = espaciado.amplio)
                .margenInferiorSeguro()
                .semantics { contentDescription = descripcionPanel },
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            Text(
                text = stringResource(Res.string.cita_panel_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            estado.error?.let { AvisoDeError(it) }

            if (estado.cargando) {
                IndicadorCargando()
            } else {
                when (estado.paso) {
                    PasoAgenda.INACTIVO -> Unit
                    PasoAgenda.ESPECIALIDAD -> PasoEspecialidad(viewModel)
                    PasoAgenda.MEDICO -> PasoMedico(estado, viewModel)
                    PasoAgenda.FRANJA -> PasoFranja(estado, viewModel)
                    PasoAgenda.DATOS -> PasoDatos(estado, viewModel)
                    PasoAgenda.RESUMEN -> PasoResumen(estado, viewModel)
                    PasoAgenda.CONFIRMADA -> PasoConfirmada(estado, viewModel)
                }
            }

            // Salir siempre esta a un toque, en todos los pasos salvo el acuse
            // final: el flujo se abre por deteccion de intencion, y si acerto
            // cuando no debia el paciente tiene que poder volver a escribir.
            if (estado.paso != PasoAgenda.CONFIRMADA) {
                BotonSecundarioSalud(
                    etiqueta = stringResource(Res.string.cita_accion_cancelar),
                    alPulsar = viewModel::cancelar,
                    descripcionAccesible = stringResource(Res.string.a11y_cita_accion_cancelar),
                )
            }
        }
    }
}

// -------------------------------------------------------------- Los pasos

@Composable
private fun PasoEspecialidad(viewModel: AgendaCitaViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Text(
        text = stringResource(Res.string.cita_paso_especialidad_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(Res.string.cita_paso_especialidad_ayuda),
        style = MaterialTheme.typography.bodyMedium,
        color = colores.textoSecundario,
    )

    if (viewModel.tieneMedicoVinculado) {
        val nombre = viewModel.nombreMedicoVinculado
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.cita_accion_mi_medico, nombre),
            alPulsar = viewModel::usarMedicoVinculado,
            descripcionAccesible = stringResource(Res.string.a11y_cita_accion_mi_medico, nombre),
        )
        Spacer(Modifier.height(espaciado.minimo))
        HorizontalDivider(color = colores.separador)
    }

    Especialidad.entries.forEach { especialidad ->
        val nombre = stringResource(especialidad.recurso())
        OpcionDeLista(
            titulo = nombre,
            descripcionAccesible = stringResource(Res.string.a11y_cita_accion_especialidad, nombre),
            alPulsar = { viewModel.elegirEspecialidad(especialidad) },
        )
    }
}

@Composable
private fun PasoMedico(estado: AgendaCitaUiState, viewModel: AgendaCitaViewModel) {
    val colores = LocalColoresSalud.current
    Text(
        text = stringResource(Res.string.cita_paso_medico_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    if (estado.medicos.isEmpty()) {
        Text(
            text = stringResource(Res.string.cita_paso_medico_vacio),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        return
    }
    estado.medicos.forEach { medico -> FilaDeMedico(medico, viewModel) }
}

@Composable
private fun FilaDeMedico(medico: PerfilDoctorDirectorio, viewModel: AgendaCitaViewModel) {
    val especialidad = stringResource(medico.especialidad.recurso())
    OpcionDeLista(
        titulo = medico.nombreCompleto,
        apoyo = especialidad,
        descripcionAccesible = stringResource(
            Res.string.a11y_cita_accion_medico,
            medico.nombreCompleto,
            especialidad,
        ),
        alPulsar = { viewModel.elegirMedicoDelDirectorio(medico) },
    )
}

@Composable
private fun PasoFranja(estado: AgendaCitaUiState, viewModel: AgendaCitaViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Text(
        text = stringResource(Res.string.cita_paso_franja_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(
            Res.string.cita_paso_franja_ayuda,
            ReservaFranja.MINUTOS_DE_RETENCION,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoSecundario,
    )

    estado.dias.forEach { dia ->
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = fechaLarga(dia.fecha),
            style = MaterialTheme.typography.labelLarge,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.compacto))
        // Carrusel horizontal por dia: las horas de una jornada no caben en el
        // ancho de un telefono, y apilarlas en vertical dejaria el resumen del
        // dia siguiente fuera de la pantalla.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
            items(dia.franjas, key = { it.idFranja }) { franja ->
                FichaDeHora(franja = franja, alElegir = viewModel::elegirFranja)
            }
        }
    }
}

@Composable
private fun FichaDeHora(franja: FranjaAgenda, alElegir: (FranjaAgenda) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(
        Res.string.a11y_cita_accion_franja,
        franja.horaInicio,
        fechaLarga(franja.fecha),
    )
    Surface(
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .clickable { alElegir(franja) }
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoCampo,
        shape = RoundedCornerShape(espaciado.compacto),
    ) {
        Text(
            text = franja.horaInicio,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(
                horizontal = espaciado.medio,
                vertical = espaciado.compacto + espaciado.minimo,
            ),
        )
    }
}

@Composable
private fun PasoDatos(estado: AgendaCitaUiState, viewModel: AgendaCitaViewModel) {
    Text(
        text = stringResource(Res.string.cita_paso_datos_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    CampoTextoRellenoSalud(
        valor = estado.nombreCompleto,
        alCambiar = viewModel::actualizarNombre,
        etiqueta = stringResource(Res.string.cita_campo_nombre),
        descripcionAccesible = stringResource(Res.string.a11y_cita_campo_nombre),
        error = estado.mensajeDe(ErrorCampoCita.NOMBRE_VACIO),
    )
    CampoTextoRellenoSalud(
        valor = estado.telefono,
        alCambiar = viewModel::actualizarTelefono,
        etiqueta = stringResource(Res.string.cita_campo_telefono),
        descripcionAccesible = stringResource(Res.string.a11y_cita_campo_telefono),
        tipoTeclado = KeyboardType.Phone,
        error = estado.mensajeDe(ErrorCampoCita.TELEFONO_VACIO)
            ?: estado.mensajeDe(ErrorCampoCita.TELEFONO_FORMATO),
    )
    CampoTextoRellenoSalud(
        valor = estado.correo,
        alCambiar = viewModel::actualizarCorreo,
        etiqueta = stringResource(Res.string.cita_campo_correo),
        descripcionAccesible = stringResource(Res.string.a11y_cita_campo_correo),
        tipoTeclado = KeyboardType.Email,
        error = estado.mensajeDe(ErrorCampoCita.CORREO_VACIO)
            ?: estado.mensajeDe(ErrorCampoCita.CORREO_FORMATO),
    )
    CampoTextoRellenoSalud(
        valor = estado.motivo,
        alCambiar = viewModel::actualizarMotivo,
        etiqueta = stringResource(Res.string.cita_campo_motivo),
        marcador = stringResource(Res.string.cita_marcador_motivo),
        descripcionAccesible = stringResource(Res.string.a11y_cita_campo_motivo),
        error = estado.mensajeDe(ErrorCampoCita.MOTIVO_VACIO),
    )
    BotonAccionPrincipal(
        etiqueta = stringResource(Res.string.cita_accion_continuar),
        alPulsar = viewModel::revisarResumen,
        descripcionAccesible = stringResource(Res.string.a11y_cita_accion_continuar),
    )
}

@Composable
private fun PasoResumen(estado: AgendaCitaUiState, viewModel: AgendaCitaViewModel) {
    val franja = estado.franja ?: return
    val medico = estado.medico ?: return
    val fecha = fechaLarga(franja.fecha)
    val horas = stringResource(Res.string.rango_horas, franja.horaInicio, franja.horaFin)

    Text(
        text = stringResource(Res.string.cita_paso_resumen_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    RenglonDeResumen(stringResource(Res.string.cita_resumen_medico), medico.nombreCompleto)
    RenglonDeResumen(stringResource(Res.string.cita_resumen_fecha), fecha)
    RenglonDeResumen(stringResource(Res.string.cita_resumen_hora), horas)
    RenglonDeResumen(stringResource(Res.string.cita_resumen_paciente), estado.nombreCompleto)
    RenglonDeResumen(stringResource(Res.string.cita_resumen_motivo), estado.motivo)

    BotonAccionPrincipal(
        etiqueta = stringResource(Res.string.cita_accion_confirmar),
        alPulsar = viewModel::confirmar,
        descripcionAccesible = stringResource(
            Res.string.a11y_cita_accion_confirmar,
            medico.nombreCompleto,
            fecha,
            franja.horaInicio,
        ),
    )
    BotonSecundarioSalud(
        etiqueta = stringResource(Res.string.cita_accion_corregir_datos),
        alPulsar = viewModel::volverADatos,
        descripcionAccesible = stringResource(Res.string.cita_accion_corregir_datos),
    )
    BotonSecundarioSalud(
        etiqueta = stringResource(Res.string.cita_accion_cambiar_horario),
        alPulsar = viewModel::volverAHorarios,
        descripcionAccesible = stringResource(Res.string.cita_accion_cambiar_horario),
    )
}

@Composable
private fun PasoConfirmada(estado: AgendaCitaUiState, viewModel: AgendaCitaViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val confirmacion = estado.confirmacion ?: return
    val cita = confirmacion.cita

    val detalle = stringResource(
        Res.string.cita_confirmada_detalle,
        cita.nombreMedico,
        fechaCorta(cita.fecha),
        cita.horaInicio,
    )
    val aviso = when {
        // El texto lo decide lo que el backend DIJO haber enviado, no lo que se
        // le pidio: prometer un WhatsApp que nunca salio deja al paciente
        // esperando un comprobante que no va a llegar.
        confirmacion.canalesNotificados.contains(CanalNotificacion.CORREO) ->
            stringResource(Res.string.cita_confirmada_canal_correo)

        confirmacion.canalesNotificados.contains(CanalNotificacion.WHATSAPP) ->
            stringResource(Res.string.cita_confirmada_canal_whatsapp)

        else -> stringResource(Res.string.cita_confirmada_sin_canales)
    }
    val descripcion = stringResource(Res.string.a11y_cita_confirmada, cita.folio, detalle)

    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = descripcion
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text(
            text = stringResource(Res.string.cita_confirmada_titulo),
            style = MaterialTheme.typography.headlineSmall,
            color = colores.exito,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(text = detalle, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.cita_confirmada_folio, cita.folio),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = aviso,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
    BotonAccionPrincipal(
        etiqueta = stringResource(Res.string.cita_accion_cerrar),
        alPulsar = viewModel::cerrarConfirmacion,
        descripcionAccesible = stringResource(Res.string.cita_accion_cerrar),
    )
}

// ------------------------------------------------------------- Auxiliares

/**
 * Mensaje del campo, o nulo si ese campo no tiene error.
 *
 * La traduccion ocurre aqui y no en el ViewModel, que solo emite claves: es lo
 * que permite probar la validacion sin cargar recursos.
 */
@Composable
private fun AgendaCitaUiState.mensajeDe(campo: ErrorCampoCita): String? =
    if (campo in errores) stringResource(campo.recurso()) else null

@Composable
private fun OpcionDeLista(
    titulo: String,
    descripcionAccesible: String,
    alPulsar: () -> Unit,
    apoyo: String? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(onClick = alPulsar)
            .semantics(mergeDescendants = true) { contentDescription = descripcionAccesible }
            .padding(vertical = espaciado.compacto),
    ) {
        Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
        if (apoyo != null) {
            Text(
                text = apoyo,
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
            )
        }
    }
}

@Composable
private fun RenglonDeResumen(etiqueta: String, valor: String) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = colores.textoSecundario,
            modifier = Modifier.weight(WEIGHT_ETIQUETA),
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(WEIGHT_VALOR),
        )
    }
}

@Composable
private fun AvisoDeError(error: ErrorAgendaCita) {
    Text(
        text = stringResource(error.recurso()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_cita_cargando)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = espaciado.medio)
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        CircularProgressIndicator(color = LocalColoresSalud.current.acentoAccion)
        Text(
            text = stringResource(Res.string.cita_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val WEIGHT_ETIQUETA = 1f
private const val WEIGHT_VALOR = 2f
