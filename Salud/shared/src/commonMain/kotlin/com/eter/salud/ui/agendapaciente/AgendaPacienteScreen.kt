// Hallmark - 2026-09-10 (segunda corrida) - genero: modern-minimal, tono: soft
// macroestructura: calendario de dos semanas + linea del dia + proximas citas (una sola columna)
// tema: custom "Clinica Serena" - audiencia: personas mayores
// cada dia dice su nombre, su numero y su estado con icono; "Hoy" siempre escrito
package com.eter.salud.ui.agendapaciente

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.EstadoDia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.presentation.agendapaciente.AgendaPacienteUiState
import com.eter.salud.presentation.agendapaciente.AgendaPacienteViewModel
import com.eter.salud.presentation.agendapaciente.DiaDelCalendario
import com.eter.salud.presentation.agendapaciente.EventoAgenda
import com.eter.salud.ui.agenda.fechaCorta
import com.eter.salud.ui.agenda.fechaLarga
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.CifraSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_agenda_pac_anterior
import salud.shared.generated.resources.a11y_agenda_pac_con_cita
import salud.shared.generated.resources.a11y_agenda_pac_evento
import salud.shared.generated.resources.a11y_agenda_pac_proxima
import salud.shared.generated.resources.a11y_agenda_pac_seleccionado
import salud.shared.generated.resources.a11y_agenda_pac_siguiente
import salud.shared.generated.resources.agenda_pac_cargando
import salud.shared.generated.resources.agenda_pac_cita_cancelada
import salud.shared.generated.resources.agenda_pac_cita_confirmada
import salud.shared.generated.resources.agenda_pac_cita_detalle
import salud.shared.generated.resources.agenda_pac_cita_en_curso
import salud.shared.generated.resources.agenda_pac_cita_no_asistio
import salud.shared.generated.resources.agenda_pac_cita_propuesta_medico
import salud.shared.generated.resources.agenda_pac_propuesta_accion_aceptar
import salud.shared.generated.resources.agenda_pac_propuesta_accion_rechazar
import salud.shared.generated.resources.agenda_pac_propuesta_error
import salud.shared.generated.resources.a11y_agenda_pac_propuesta_aceptar
import salud.shared.generated.resources.a11y_agenda_pac_propuesta_rechazar
import salud.shared.generated.resources.agenda_pac_cita_pendiente
import salud.shared.generated.resources.agenda_pac_cita_titulo
import salud.shared.generated.resources.agenda_pac_citas_no_disponibles
import salud.shared.generated.resources.agenda_pac_error
import salud.shared.generated.resources.agenda_pac_hoy
import salud.shared.generated.resources.agenda_pac_leyenda_cita
import salud.shared.generated.resources.agenda_pac_leyenda_completo
import salud.shared.generated.resources.agenda_pac_leyenda_incompleto
import salud.shared.generated.resources.agenda_pac_mes
import salud.shared.generated.resources.agenda_pac_proxima_cuando
import salud.shared.generated.resources.agenda_pac_proximas_titulo
import salud.shared.generated.resources.agenda_pac_proximas_vacio
import salud.shared.generated.resources.agenda_pac_rango
import salud.shared.generated.resources.agenda_pac_subtitulo
import salud.shared.generated.resources.agenda_pac_titulo
import salud.shared.generated.resources.agenda_pac_toma_programada
import salud.shared.generated.resources.agenda_pac_vacio
import salud.shared.generated.resources.agenda_pac_volver_hoy
import salud.shared.generated.resources.home_toma_estado_omitido
import salud.shared.generated.resources.home_toma_estado_pendiente
import salud.shared.generated.resources.home_toma_estado_tomado
import salud.shared.generated.resources.home_toma_estado_tomado_tarde
import salud.shared.generated.resources.opciones_dias_semana
import salud.shared.generated.resources.opciones_meses

/**
 * Agenda del paciente: sus medicinas y sus citas, dia por dia.
 *
 * ## El calendario
 *
 * Dos semanas, no un mes: la que esta en curso y la siguiente, en dos filas de
 * siete. Un mes de 35 casillas en un telefono deja numeros de 12sp que una
 * vista cansada no lee; dos filas conservan el NOMBRE del dia escrito ("Lun",
 * "Mar") y el numero a 22sp, y ya ensenan la cita del martes que viene sin
 * tener que cambiar de pagina. Para ir a otra quincena hay dos botones con
 * flecha, uno a cada lado del mes -- nunca un deslizamiento, que es un gesto
 * que hay que adivinar.
 *
 * Cada casilla dice tres cosas sin depender del color:
 *  - **Hoy**: escrito debajo del numero, siempre.
 *  - **Cita**: un punto azul, explicado en la leyenda.
 *  - **Medicinas del dia**: una palomita verde si se tomaron todas, un aviso
 *    ambar si falto alguna. Los dias futuros no se juzgan.
 *
 * ## El dia
 *
 * Debajo, el dia elegido escrito completo ("Jueves 10 de septiembre") y lo que
 * pasa en el, en UNA lista por hora: la cita y las pastillas juntas, como la
 * persona se cuenta su dia.
 *
 * ## Lo que viene
 *
 * Al final, las citas de las proximas dos semanas en una lista. Es la pregunta
 * "que tengo agendado" contestada de un vistazo; tocar una lleva a su dia.
 */
@Composable
fun AgendaPacienteScreen(
    viewModel: AgendaPacienteViewModel,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val margen = Modifier.padding(horizontal = MARGEN_PANTALLA)

    // Cada vez que se vuelve a la pestana: una cita pedida en el chat mientras
    // tanto tiene que estar ya en el calendario.
    LaunchedEffect(Unit) { viewModel.refrescar() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentPadding = PaddingValues(top = espaciado.amplio, bottom = espaciado.respiro),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        item(key = "cabecera") {
            Column(margen) {
                Text(
                    text = stringResource(Res.string.agenda_pac_titulo),
                    style = CifraSalud.portada,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(espaciado.compacto))
                Text(
                    text = stringResource(Res.string.agenda_pac_subtitulo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalColoresSalud.current.textoSecundario,
                )
            }
        }

        if (estado.dias.isNotEmpty()) {
            item(key = "calendario") {
                Calendario(
                    estado = estado,
                    alAnterior = viewModel::periodoAnterior,
                    alSiguiente = viewModel::periodoSiguiente,
                    alHoy = viewModel::irAHoy,
                    alElegir = viewModel::elegirDia,
                    modifier = Modifier.padding(horizontal = MARGEN_CALENDARIO),
                )
            }
        }

        if (estado.citasNoDisponibles) {
            item(key = "aviso_citas") {
                Text(
                    text = stringResource(Res.string.agenda_pac_citas_no_disponibles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalColoresSalud.current.textoAdvertencia,
                    modifier = margen,
                )
            }
        }

        if (estado.errorRespuestaPropuesta) {
            item(key = "aviso_propuesta") {
                Text(
                    text = stringResource(Res.string.agenda_pac_propuesta_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = margen,
                )
            }
        }

        when {
            estado.errorCarga -> item(key = "error") {
                BloqueDeError(
                    mensaje = stringResource(Res.string.agenda_pac_error),
                    alReintentar = viewModel::cargar,
                    modifier = margen,
                )
            }

            estado.cargando -> item(key = "cargando") { Cargando(margen) }

            else -> {
                item(key = "dia_${estado.fechaSeleccionada}") {
                    EncabezadoDelDia(estado, margen)
                }
                if (estado.eventos.isEmpty()) {
                    item(key = "vacio") { DiaVacio(margen) }
                } else {
                    items(estado.eventos, key = { it.clave() }) { evento ->
                        FilaDeEvento(
                            evento = evento,
                            esFuturo = estado.diaSeleccionado?.esFuturo == true,
                            alAceptarPropuesta = viewModel::aceptarPropuesta,
                            alRechazarPropuesta = viewModel::rechazarPropuesta,
                            modifier = margen,
                        )
                    }
                }
            }
        }

        if (!estado.citasNoDisponibles && estado.dias.isNotEmpty()) {
            item(key = "proximas_titulo") {
                Text(
                    text = stringResource(Res.string.agenda_pac_proximas_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = margen.padding(top = espaciado.amplio).semantics { heading() },
                )
            }
            if (estado.proximasCitas.isEmpty()) {
                item(key = "proximas_vacio") {
                    Text(
                        text = stringResource(Res.string.agenda_pac_proximas_vacio),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalColoresSalud.current.textoSecundario,
                        modifier = margen,
                    )
                }
            } else {
                items(estado.proximasCitas, key = { "proxima_${it.idCita}" }) { cita ->
                    FilaDeProximaCita(
                        cita = cita,
                        esHoy = cita.fecha == estado.hoy,
                        alPulsar = { viewModel.elegirDia(cita.fecha) },
                        modifier = margen,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- Calendario

@Composable
private fun Calendario(
    estado: AgendaPacienteUiState,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    alHoy: () -> Unit,
    alElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val meses = stringArrayResource(Res.array.opciones_meses)
    val partesLunes = CalendarioSalud.descomponer(estado.lunes)
    val domingo = estado.dias.lastOrNull()?.fecha.orEmpty()
    val partesDomingo = CalendarioSalud.descomponer(domingo)
    // Dos semanas pueden cruzar de mes: entonces se nombran los dos.
    val mes = if (partesLunes != null && partesDomingo != null) {
        val nombreInicio = meses.getOrElse(partesLunes.mes.toInt() - 1) { "" }
        val nombreFin = meses.getOrElse(partesDomingo.mes.toInt() - 1) { "" }
        val nombres = if (nombreInicio == nombreFin) nombreInicio else "$nombreInicio - $nombreFin"
        stringResource(Res.string.agenda_pac_mes, nombres, partesDomingo.anio)
    } else {
        ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FormaSalud.destacada)
            .background(colores.fondoTarjeta)
            .border(1.dp, colores.separador, FormaSalud.destacada)
            .padding(horizontal = espaciado.compacto, vertical = espaciado.medio),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonFlecha(GlifoSalud.ANTERIOR, stringResource(Res.string.a11y_agenda_pac_anterior), alAnterior)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = mes,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.agenda_pac_rango, fechaCorta(estado.lunes), fechaCorta(domingo)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoSecundario,
                    textAlign = TextAlign.Center,
                )
            }
            BotonFlecha(GlifoSalud.SIGUIENTE, stringResource(Res.string.a11y_agenda_pac_siguiente), alSiguiente)
        }

        Spacer(Modifier.height(espaciado.medio))
        Column(verticalArrangement = Arrangement.spacedBy(SEPARACION_CASILLAS)) {
            estado.dias.chunked(DIAS_POR_FILA).forEach { semana ->
                Row(horizontalArrangement = Arrangement.spacedBy(SEPARACION_CASILLAS)) {
                    semana.forEachIndexed { indice, dia ->
                        CasillaDelDia(
                            dia = dia,
                            indice = indice,
                            seleccionado = dia.fecha == estado.fechaSeleccionada,
                            alPulsar = { alElegir(dia.fecha) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(espaciado.medio))
        Leyenda()

        if (!estado.incluyeHoy) {
            Spacer(Modifier.height(espaciado.medio))
            BotonVolverAHoy(alHoy)
        }
    }
}

/**
 * Una casilla: nombre corto del dia, numero grande, "Hoy" si lo es, y los
 * indicadores de cita y de medicinas.
 *
 * Seleccionada: relleno azul clinico con texto blanco. Hoy sin seleccionar:
 * borde azul de 2dp. Las dos marcas son distintas para que "estoy mirando este
 * dia" y "este dia es hoy" no se confundan.
 */
@Composable
private fun CasillaDelDia(
    dia: DiaDelCalendario,
    indice: Int,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = LocalColoresSalud.current
    val dias = stringArrayResource(Res.array.opciones_dias_semana)
    val nombreCorto = dias.getOrElse(indice) { "" }.take(LETRAS_DIA_CORTO)
    val numero = CalendarioSalud.descomponer(dia.fecha)?.dia?.toIntOrNull()?.toString().orEmpty()
    val tinta = if (seleccionado) colores.sobreAcentoAccion else MaterialTheme.colorScheme.onSurface
    val tintaSecundaria = if (seleccionado) colores.sobreAcentoAccion else colores.textoSecundario
    val fuente = remember { MutableInteractionSource() }

    val descripcion = buildList {
        add(fechaLarga(dia.fecha))
        if (dia.esHoy) add(stringResource(Res.string.agenda_pac_hoy))
        if (dia.citas > 0) add(stringResource(Res.string.a11y_agenda_pac_con_cita))
        estadoDeMedicinas(dia)?.let { add(stringResource(it.texto)) }
        if (seleccionado) add(stringResource(Res.string.a11y_agenda_pac_seleccionado))
    }.joinToString(". ")

    Column(
        modifier = modifier
            .heightIn(min = ALTO_CASILLA)
            .clip(FormaSalud.grande)
            .background(if (seleccionado) colores.acentoAccion else Color.Transparent)
            .then(
                if (dia.esHoy && !seleccionado) {
                    Modifier.border(2.dp, colores.acentoAccion, FormaSalud.grande)
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.grande)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                selected = seleccionado
            }
            .padding(vertical = LocalEspaciadoSalud.current.compacto),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = nombreCorto, style = MaterialTheme.typography.labelMedium, color = tintaSecundaria, maxLines = 1)
        Text(text = numero, style = MaterialTheme.typography.titleLarge, color = tinta, maxLines = 1)
        Text(
            // "Hoy" ocupa su renglon en TODAS las casillas (vacio en las demas):
            // asi los indicadores de abajo quedan alineados en fila.
            text = if (dia.esHoy) stringResource(Res.string.agenda_pac_hoy) else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (seleccionado) colores.sobreAcentoAccion else colores.acentoAccion,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.height(LADO_INDICADOR),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dia.citas > 0) {
                Box(
                    Modifier
                        .size(DIAMETRO_PUNTO_CITA)
                        .background(if (seleccionado) colores.sobreAcentoAccion else colores.acentoAccion, CircleShape),
                )
            }
            estadoDeMedicinas(dia)?.let { marca ->
                IconoSalud(
                    glifo = marca.glifo,
                    lado = LADO_INDICADOR,
                    color = if (seleccionado) colores.sobreAcentoAccion else marca.color(),
                )
            }
        }
    }
}

/** Lo que significan las marcas de las casillas, escrito. */
@Composable
private fun Leyenda() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(
        modifier = Modifier.padding(horizontal = espaciado.compacto),
        verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(DIAMETRO_PUNTO_CITA + 2.dp).background(colores.acentoAccion, CircleShape))
            Spacer(Modifier.width(espaciado.compacto))
            Text(stringResource(Res.string.agenda_pac_leyenda_cita), style = MaterialTheme.typography.bodySmall, color = colores.textoSecundario)
            Spacer(Modifier.width(espaciado.medio))
            IconoSalud(glifo = GlifoSalud.VERIFICADO, lado = LADO_INDICADOR + 2.dp, color = colores.exito)
            Spacer(Modifier.width(espaciado.minimo))
            Text(stringResource(Res.string.agenda_pac_leyenda_completo), style = MaterialTheme.typography.bodySmall, color = colores.textoSecundario)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoSalud(glifo = GlifoSalud.CANCELADO, lado = LADO_INDICADOR + 2.dp, color = colores.textoAdvertencia)
            Spacer(Modifier.width(espaciado.minimo))
            Text(stringResource(Res.string.agenda_pac_leyenda_incompleto), style = MaterialTheme.typography.bodySmall, color = colores.textoSecundario)
        }
    }
}

/** Flecha de semana: circulo de 52dp con borde, nunca un icono suelto. */
@Composable
private fun BotonFlecha(glifo: GlifoSalud, descripcion: String, alPulsar: () -> Unit) {
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(LADO_BOTON_FLECHA)
            .clip(CircleShape)
            .background(colores.acentoSuave)
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, CircleShape)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        IconoSalud(glifo = glifo, lado = LADO_ICONO_FLECHA, color = colores.acentoAccion)
    }
}

@Composable
private fun BotonVolverAHoy(alPulsar: () -> Unit) {
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima + 8.dp)
            .clip(FormaSalud.grande)
            .border(2.dp, colores.acentoAccion, FormaSalud.grande)
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.grande),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.agenda_pac_volver_hoy),
            style = MaterialTheme.typography.labelLarge,
            color = colores.acentoAccion,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------- Dia

@Composable
private fun EncabezadoDelDia(estado: AgendaPacienteUiState, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(modifier.padding(top = espaciado.compacto), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = fechaLarga(estado.fechaSeleccionada),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (estado.diaSeleccionado?.esHoy == true) {
            Spacer(Modifier.width(espaciado.compacto))
            Text(
                text = stringResource(Res.string.agenda_pac_hoy),
                style = MaterialTheme.typography.labelMedium,
                color = colores.sobreAcentoAccion,
                modifier = Modifier
                    .background(colores.acentoAccion, FormaSalud.pastilla)
                    .padding(horizontal = espaciado.compacto + espaciado.minimo, vertical = espaciado.minimo),
            )
        }
    }
}

/**
 * Una toma o una cita: la hora grande en su columna y, a la derecha, una
 * tarjeta con icono en circulo, que es, detalle y estado con icono + palabra.
 */
@Composable
private fun FilaDeEvento(
    evento: EventoAgenda,
    esFuturo: Boolean,
    alAceptarPropuesta: (Cita) -> Unit,
    alRechazarPropuesta: (Cita) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    val titulo: String
    val detalle: String
    val glifo: GlifoSalud
    val marca: Marca
    when (evento) {
        is EventoAgenda.Toma -> {
            titulo = evento.toma.medicamento
            detalle = evento.toma.dosis
            glifo = GlifoSalud.PASTILLA
            marca = marcaDeToma(evento.toma.estado, esFuturo)
        }

        is EventoAgenda.ConCita -> {
            val cita = evento.cita
            titulo = stringResource(Res.string.agenda_pac_cita_titulo, cita.nombreMedico)
            detalle = stringResource(Res.string.agenda_pac_cita_detalle, cita.horaInicio, cita.horaFin, cita.folio)
            glifo = GlifoSalud.EVENTO
            marca = marcaDeCita(cita.estado)
        }
    }
    val textoEstado = stringResource(marca.texto)
    val descripcion = stringResource(Res.string.a11y_agenda_pac_evento, evento.hora, titulo, detalle, textoEstado)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = evento.hora,
            style = CifraSalud.hora,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(ANCHO_COLUMNA_HORA).padding(top = espaciado.medio),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(FormaSalud.destacada)
                .background(if (evento is EventoAgenda.ConCita) colores.acentoSuave else colores.fondoTarjeta)
                .border(1.dp, if (evento is EventoAgenda.ConCita) colores.acentoSuave else colores.separador, FormaSalud.destacada)
                .padding(espaciado.medio),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(DIAMETRO_CIRCULO_ICONO)
                    .background(if (evento is EventoAgenda.ConCita) colores.fondoTarjeta else colores.acentoSuave, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconoSalud(glifo = glifo, lado = LADO_ICONO_EVENTO, color = colores.acentoAccion)
            }
            Spacer(Modifier.width(espaciado.medio))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = detalle, style = MaterialTheme.typography.bodyMedium, color = colores.textoSecundario)
                Spacer(Modifier.height(espaciado.compacto))
                Etiqueta(marca, textoEstado)
                if (evento is EventoAgenda.ConCita && evento.cita.estado == EstadoCita.PROPUESTA_MEDICO) {
                    Spacer(Modifier.height(espaciado.compacto))
                    AccionesDePropuesta(
                        cita = evento.cita,
                        alAceptar = { alAceptarPropuesta(evento.cita) },
                        alRechazar = { alRechazarPropuesta(evento.cita) },
                    )
                }
            }
        }
    }
}

/**
 * Aceptar / rechazar una cita que el medico propuso, en el sitio exacto donde
 * el paciente ya la esta mirando: no hace falta abrir nada aparte para
 * responder.
 */
@Composable
private fun AccionesDePropuesta(
    cita: Cita,
    alAceptar: () -> Unit,
    alRechazar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val dia = fechaLarga(cita.fecha)
    val descripcionAceptar = stringResource(
        Res.string.a11y_agenda_pac_propuesta_aceptar,
        cita.nombreMedico,
        dia,
        cita.horaInicio,
    )
    val descripcionRechazar = stringResource(
        Res.string.a11y_agenda_pac_propuesta_rechazar,
        cita.nombreMedico,
        dia,
        cita.horaInicio,
    )
    val fuenteAceptar = remember { MutableInteractionSource() }
    val fuenteRechazar = remember { MutableInteractionSource() }

    Row(horizontalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
        Row(
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .clip(FormaSalud.pastilla)
                .background(colores.acentoAccion)
                .clickable(interactionSource = fuenteAceptar, indication = null, onClick = alAceptar)
                .superficiePulsable(fuenteAceptar, FormaSalud.pastilla)
                .semantics { contentDescription = descripcionAceptar }
                .padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.agenda_pac_propuesta_accion_aceptar),
                style = MaterialTheme.typography.labelLarge,
                color = colores.sobreAcentoAccion,
            )
        }
        Row(
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .clip(FormaSalud.pastilla)
                .border(1.dp, colores.separador, FormaSalud.pastilla)
                .clickable(interactionSource = fuenteRechazar, indication = null, onClick = alRechazar)
                .superficiePulsable(fuenteRechazar, FormaSalud.pastilla)
                .semantics { contentDescription = descripcionRechazar }
                .padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.agenda_pac_propuesta_accion_rechazar),
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoSecundario,
            )
        }
    }
}

/**
 * Una cita de las proximas dos semanas: cuando (dia y hora), con quien y en que
 * estado esta. Toda la fila lleva al dia de la cita en el calendario.
 */
@Composable
private fun FilaDeProximaCita(cita: Cita, esHoy: Boolean, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fuente = remember { MutableInteractionSource() }
    val marca = marcaDeCita(cita.estado)
    val textoEstado = stringResource(marca.texto)
    val dia = if (esHoy) stringResource(Res.string.agenda_pac_hoy) else fechaLarga(cita.fecha)
    val titulo = stringResource(Res.string.agenda_pac_cita_titulo, cita.nombreMedico)
    val descripcion = stringResource(Res.string.a11y_agenda_pac_proxima, dia, cita.horaInicio, titulo, textoEstado)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clip(FormaSalud.destacada)
            .background(colores.fondoTarjeta)
            .border(1.dp, colores.separador, FormaSalud.destacada)
            .clickable(interactionSource = fuente, indication = null, onClick = alPulsar)
            .superficiePulsable(fuente, FormaSalud.destacada)
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .padding(espaciado.medio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(DIAMETRO_CIRCULO_ICONO).background(colores.acentoSuave, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(glifo = GlifoSalud.EVENTO, lado = LADO_ICONO_EVENTO, color = colores.acentoAccion)
        }
        Spacer(Modifier.width(espaciado.medio))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.agenda_pac_proxima_cuando, dia, cita.horaInicio),
                style = MaterialTheme.typography.labelLarge,
                color = colores.acentoAccion,
            )
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(espaciado.compacto))
            Etiqueta(marca, textoEstado)
        }
    }
}

@Composable
private fun Etiqueta(marca: Marca, texto: String) {
    val espaciado = LocalEspaciadoSalud.current
    Row(
        modifier = Modifier
            .background(marca.fondo(), FormaSalud.pastilla)
            .padding(horizontal = espaciado.compacto + espaciado.minimo, vertical = espaciado.minimo + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(glifo = marca.glifo, lado = LADO_INDICADOR + 4.dp, color = marca.color())
        Spacer(Modifier.width(espaciado.minimo + 2.dp))
        Text(text = texto, style = MaterialTheme.typography.labelMedium, color = marca.color(), maxLines = 1)
    }
}

@Composable
private fun DiaVacio(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FormaSalud.destacada)
            .background(colores.fondoTarjeta)
            .border(1.dp, colores.separador, FormaSalud.destacada)
            .padding(espaciado.amplio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoSalud(glifo = GlifoSalud.EVENTO, lado = LADO_ICONO_EVENTO, color = colores.textoSecundario)
        Spacer(Modifier.width(espaciado.medio))
        Text(
            text = stringResource(Res.string.agenda_pac_vacio),
            style = MaterialTheme.typography.bodyLarge,
            color = colores.textoSecundario,
        )
    }
}

@Composable
private fun Cargando(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val texto = stringResource(Res.string.agenda_pac_cargando)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = texto
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Text(text = texto, style = MaterialTheme.typography.bodyLarge, color = colores.textoSecundario)
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ALTO_ESQUELETO)
                    .background(colores.fondoCampo, FormaSalud.destacada),
            )
        }
    }
}

// ---------------------------------------------------------------- Marcas

/** Un estado dibujado: icono + palabra + tono. Nunca el tono solo. */
private enum class TonoMarca { EXITO, ATENCION, CRITICO, INFO, NEUTRO }

private data class Marca(val glifo: GlifoSalud, val texto: StringResource, val tono: TonoMarca)

@Composable
private fun Marca.color(): Color {
    val colores = LocalColoresSalud.current
    return when (tono) {
        TonoMarca.EXITO -> colores.exito
        TonoMarca.ATENCION -> colores.textoAdvertencia
        TonoMarca.CRITICO -> colores.acentoCritico
        TonoMarca.INFO -> colores.acentoAccion
        TonoMarca.NEUTRO -> colores.textoSecundario
    }
}

@Composable
private fun Marca.fondo(): Color {
    val colores = LocalColoresSalud.current
    return when (tono) {
        TonoMarca.EXITO -> colores.fondoExito
        TonoMarca.ATENCION -> colores.fondoAdvertencia
        TonoMarca.CRITICO -> colores.fondoCritico
        TonoMarca.INFO -> colores.fondoTarjeta
        TonoMarca.NEUTRO -> colores.fondoCampo
    }
}

private fun marcaDeToma(estado: EstadoToma, esFuturo: Boolean): Marca = when {
    esFuturo -> Marca(GlifoSalud.PENDIENTE, Res.string.agenda_pac_toma_programada, TonoMarca.NEUTRO)
    estado == EstadoToma.TOMADO -> Marca(GlifoSalud.VERIFICADO, Res.string.home_toma_estado_tomado, TonoMarca.EXITO)
    estado == EstadoToma.TOMADO_TARDE -> Marca(GlifoSalud.VERIFICADO, Res.string.home_toma_estado_tomado_tarde, TonoMarca.EXITO)
    estado == EstadoToma.OMITIDO -> Marca(GlifoSalud.CANCELADO, Res.string.home_toma_estado_omitido, TonoMarca.ATENCION)
    else -> Marca(GlifoSalud.PENDIENTE, Res.string.home_toma_estado_pendiente, TonoMarca.NEUTRO)
}

private fun marcaDeCita(estado: EstadoCita): Marca = when (estado) {
    EstadoCita.PENDIENTE -> Marca(GlifoSalud.PENDIENTE, Res.string.agenda_pac_cita_pendiente, TonoMarca.ATENCION)
    EstadoCita.CONFIRMADA -> Marca(GlifoSalud.VERIFICADO, Res.string.agenda_pac_cita_confirmada, TonoMarca.EXITO)
    EstadoCita.EN_CURSO -> Marca(GlifoSalud.MEDICOS, Res.string.agenda_pac_cita_en_curso, TonoMarca.INFO)
    EstadoCita.CANCELADA -> Marca(GlifoSalud.CANCELADO, Res.string.agenda_pac_cita_cancelada, TonoMarca.CRITICO)
    EstadoCita.NO_ASISTIO -> Marca(GlifoSalud.CANCELADO, Res.string.agenda_pac_cita_no_asistio, TonoMarca.CRITICO)
    // Un bloqueo nunca llega aqui: el ViewModel los descarta.
    EstadoCita.BLOQUEADO -> Marca(GlifoSalud.CANCELADO, Res.string.agenda_pac_cita_cancelada, TonoMarca.NEUTRO)
    EstadoCita.PROPUESTA_MEDICO ->
        Marca(GlifoSalud.PENDIENTE, Res.string.agenda_pac_cita_propuesta_medico, TonoMarca.ATENCION)
}

/**
 * La marca de medicinas de una casilla: solo para dias ya vividos o en curso
 * con tomas registradas. Un dia futuro, o sin tratamiento, no lleva marca.
 */
private fun estadoDeMedicinas(dia: DiaDelCalendario): Marca? {
    val adherencia = dia.adherencia ?: return null
    if (dia.esFuturo) return null
    return when (adherencia.estado) {
        EstadoDia.COMPLETO -> Marca(GlifoSalud.VERIFICADO, Res.string.agenda_pac_leyenda_completo, TonoMarca.EXITO)
        EstadoDia.INCOMPLETO -> Marca(GlifoSalud.CANCELADO, Res.string.agenda_pac_leyenda_incompleto, TonoMarca.ATENCION)
        EstadoDia.EN_CURSO, EstadoDia.SIN_TOMAS -> null
    }
}

private fun EventoAgenda.clave(): String = when (this) {
    is EventoAgenda.Toma -> "toma_${toma.idToma}"
    is EventoAgenda.ConCita -> "cita_${cita.idCita}"
}

private val MARGEN_PANTALLA = 20.dp
/** El calendario gana 8dp por lado sobre el margen: cada casilla necesita el ancho. */
private val MARGEN_CALENDARIO = 12.dp
private val SEPARACION_CASILLAS = 4.dp
private val ALTO_CASILLA = 96.dp
private val LADO_INDICADOR = 14.dp
private val DIAMETRO_PUNTO_CITA = 8.dp
private val LADO_BOTON_FLECHA = 52.dp
private val LADO_ICONO_FLECHA = 30.dp
private val ANCHO_COLUMNA_HORA = 76.dp
private val DIAMETRO_CIRCULO_ICONO = 48.dp
private val LADO_ICONO_EVENTO = 26.dp
private val ALTO_ESQUELETO = 96.dp
private const val LETRAS_DIA_CORTO = 3
private const val DIAS_POR_FILA = 7
