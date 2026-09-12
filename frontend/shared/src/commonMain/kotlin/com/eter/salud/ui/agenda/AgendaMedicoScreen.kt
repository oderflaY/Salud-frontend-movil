package com.eter.salud.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.presentation.agenda.AgendaMedicoUiState
import com.eter.salud.presentation.agenda.AgendaMedicoViewModel
import com.eter.salud.presentation.agenda.CeldaDelMes
import com.eter.salud.presentation.agenda.RenglonDelDia
import com.eter.salud.presentation.agenda.VistaCalendario
import com.eter.salud.ui.componentes.AccionExtendidaFlotante
import com.eter.salud.ui.componentes.BarraAccionInferior
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.BotonSecundarioSalud
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.bordeDeTarjeta
import com.eter.salud.ui.componentes.elevacionDeTarjeta
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_agenda_accion_agendar_cita
import salud.shared.generated.resources.a11y_agenda_accion_anterior
import salud.shared.generated.resources.a11y_agenda_accion_hoy
import salud.shared.generated.resources.a11y_agenda_accion_siguiente
import salud.shared.generated.resources.agenda_accion_agendar_cita
import salud.shared.generated.resources.a11y_agenda_bloqueo_campo_nota
import salud.shared.generated.resources.a11y_agenda_cargando
import salud.shared.generated.resources.a11y_agenda_celda_mes
import salud.shared.generated.resources.a11y_agenda_celda_mes_vacia
import salud.shared.generated.resources.a11y_agenda_leyenda
import salud.shared.generated.resources.a11y_agenda_renglon_libre
import salud.shared.generated.resources.a11y_agenda_renglon_ocupado
import salud.shared.generated.resources.a11y_agenda_vista
import salud.shared.generated.resources.agenda_accion_anterior
import salud.shared.generated.resources.agenda_accion_cancelar_bloqueo
import salud.shared.generated.resources.agenda_accion_hoy
import salud.shared.generated.resources.agenda_accion_siguiente
import salud.shared.generated.resources.agenda_bloqueo_ayuda
import salud.shared.generated.resources.agenda_bloqueo_campo_nota
import salud.shared.generated.resources.agenda_bloqueo_en_curso
import salud.shared.generated.resources.agenda_bloqueo_marcador_nota
import salud.shared.generated.resources.agenda_error_accion
import salud.shared.generated.resources.agenda_estado_bloqueado
import salud.shared.generated.resources.agenda_estado_cargando
import salud.shared.generated.resources.agenda_estado_error
import salud.shared.generated.resources.agenda_hora_libre
import salud.shared.generated.resources.agenda_leyenda_conteo
import salud.shared.generated.resources.agenda_leyenda_titulo
import salud.shared.generated.resources.agenda_sin_citas
import salud.shared.generated.resources.agenda_titulo
import salud.shared.generated.resources.opciones_iniciales_dias
import salud.shared.generated.resources.rango_fechas
import salud.shared.generated.resources.rango_horas

/**
 * Calendario de citas del medico.
 *
 * El requerimiento pedia un panel web tipo Google Calendar; esta app es
 * multiplataforma movil, asi que la rejilla se traduce a lo que funciona en una
 * pantalla de mano: las tres vistas (dia, semana, mes) se conservan enteras, y
 * el arrastre para bloquear horas se sustituye por dos toques, porque arrastrar
 * sobre una lista que scrollea pelearia con el gesto de desplazamiento y
 * produciria bloqueos accidentales.
 *
 * El codigo de colores del requerimiento se respeta, pero el color nunca viaja
 * solo: cada cita lleva su estado escrito al lado. Un calendario clinico que
 * solo se distingue por tono es ilegible para quien no percibe el rojo y el
 * verde, y es justo ahi donde se ve si un paciente no asistio.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, iconografia vectorial,
 * color y espaciado solo por tokens semanticos, Modo Oscuro automatico y
 * margenes inferiores de seguridad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaMedicoScreen(
    viewModel: AgendaMedicoViewModel,
    modifier: Modifier = Modifier,
    alAbrirExpediente: (Cita) -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Cabecera grande y sin flecha, igual que las demas pestanas: la agenda
        // es raiz de navegacion del portal medico, no una pantalla apilada.
        topBar = {
            CabeceraGrande(
                titulo = stringResource(Res.string.agenda_titulo),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(
                        start = LocalEspaciadoSalud.current.amplio,
                        end = LocalEspaciadoSalud.current.amplio,
                        top = LocalEspaciadoSalud.current.medio,
                    ),
            )
        },
        // La barra de bloqueo solo existe mientras hay un rango a medio marcar:
        // ocupar el fondo de la pantalla el resto del tiempo robaria altura al
        // calendario, que es lo que el medico viene a ver.
        bottomBar = {
            if (estado.hayBloqueoEnCurso) {
                BarraDeBloqueo(estado = estado, viewModel = viewModel)
            }
        },
        floatingActionButton = {
            AccionExtendidaFlotante(
                etiqueta = stringResource(Res.string.agenda_accion_agendar_cita),
                glifo = GlifoSalud.EVENTO,
                descripcionAccesible = stringResource(Res.string.a11y_agenda_accion_agendar_cita),
                alPulsar = viewModel::iniciarPropuestaCita,
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            SelectorDeVista(
                seleccionada = estado.vista,
                alElegir = viewModel::cambiarVista,
                modifier = Modifier.padding(
                    horizontal = espaciado.amplio,
                    vertical = espaciado.medio,
                ),
            )
            BarraDePeriodo(
                estado = estado,
                alAnterior = viewModel::irAlPeriodoAnterior,
                alSiguiente = viewModel::irAlPeriodoSiguiente,
                alHoy = viewModel::irAHoy,
            )
            HorizontalDivider(color = LocalColoresSalud.current.separador)

            if (estado.errorAccion) AvisoErrorAccion()

            when {
                estado.cargando -> IndicadorCargando()
                estado.errorCarga -> BloqueDeError(
                    mensaje = stringResource(Res.string.agenda_estado_error),
                    alReintentar = viewModel::reintentar,
                )
                else -> when (estado.vista) {
                    VistaCalendario.DIARIA -> VistaDiaria(
                        estado = estado,
                        alTocarCita = viewModel::abrirCita,
                        alTocarLibre = { hora ->
                            if (estado.hayBloqueoEnCurso) {
                                viewModel.completarBloqueo(hora)
                            } else {
                                viewModel.iniciarBloqueo(hora)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )

                    VistaCalendario.DOS_SEMANAS -> VistaDosSemanas(
                        estado = estado,
                        alTocarCita = viewModel::abrirCita,
                        alTocarDia = viewModel::abrirDia,
                        modifier = Modifier.weight(1f),
                    )

                    VistaCalendario.MENSUAL -> VistaMensual(
                        estado = estado,
                        alTocarDia = viewModel::abrirDia,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            LeyendaDeEstados(estado)
        }
    }

    val seleccionada = estado.citaSeleccionada
    if (seleccionada != null) {
        HojaDetalleCita(
            cita = seleccionada,
            estado = estado,
            viewModel = viewModel,
            alAbrirExpediente = alAbrirExpediente,
        )
    }

    if (estado.proponiendoCita) {
        HojaProponerCita(estado = estado, viewModel = viewModel)
    }
}

// --------------------------------------------------------------- Cabeceras

/**
 * Control segmentado de tres posiciones. Se dibuja con superficies del tema en
 * lugar de con el componente experimental de Material3: esta pantalla no puede
 * depender de una API que aun cambia de firma entre versiones.
 */
@Composable
private fun SelectorDeVista(
    seleccionada: VistaCalendario,
    alElegir: (VistaCalendario) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colores.fondoCampo,
        shape = FormaSalud.media,
    ) {
        Row(modifier = Modifier.padding(espaciado.minimo)) {
            VistaCalendario.entries.forEach { vista ->
                val activa = vista == seleccionada
                val etiqueta = stringResource(vista.recurso())
                val descripcion = stringResource(Res.string.a11y_agenda_vista, etiqueta)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AreaTactilMinima)
                        .clickable { alElegir(vista) }
                        .semantics { contentDescription = descripcion },
                    color = if (activa) MaterialTheme.colorScheme.surface else colores.fondoCampo,
                    shape = FormaSalud.sutil,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = etiqueta,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activa) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                colores.textoSecundario
                            },
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraDePeriodo(
    estado: AgendaMedicoUiState,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    alHoy: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val rango = estado.rango
    val titulo = if (estado.vista == VistaCalendario.DIARIA) {
        fechaLarga(estado.fechaAncla)
    } else {
        stringResource(Res.string.rango_fechas, fechaCorta(rango.desde), fechaCorta(rango.hasta))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonDeTexto(
            etiqueta = stringResource(Res.string.agenda_accion_anterior),
            descripcion = stringResource(Res.string.a11y_agenda_accion_anterior),
            alPulsar = alAnterior,
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    // El titulo cambia al navegar sin que nada mas se mueva: se
                    // anuncia para que quien no ve la pantalla sepa donde quedo.
                    liveRegion = LiveRegionMode.Polite
                },
        )
        BotonDeTexto(
            etiqueta = stringResource(Res.string.agenda_accion_siguiente),
            descripcion = stringResource(Res.string.a11y_agenda_accion_siguiente),
            alPulsar = alSiguiente,
        )
        BotonDeTexto(
            etiqueta = stringResource(Res.string.agenda_accion_hoy),
            descripcion = stringResource(Res.string.a11y_agenda_accion_hoy),
            alPulsar = alHoy,
        )
    }
}

@Composable
private fun BotonDeTexto(etiqueta: String, descripcion: String, alPulsar: () -> Unit) {
    TextButton(
        onClick = alPulsar,
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .semantics { contentDescription = descripcion },
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

// ------------------------------------------------------------- Vista diaria

@Composable
private fun VistaDiaria(
    estado: AgendaMedicoUiState,
    alTocarCita: (Cita) -> Unit,
    alTocarLibre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = espaciado.medio,
            vertical = espaciado.compacto,
        ),
        verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        items(estado.renglonesDelDia, key = { it.horaInicio }) { renglon ->
            RenglonDeHora(
                renglon = renglon,
                marcado = renglon.horaInicio == estado.inicioDeBloqueo,
                alTocarCita = alTocarCita,
                alTocarLibre = alTocarLibre,
            )
        }
    }
}

@Composable
private fun RenglonDeHora(
    renglon: RenglonDelDia,
    marcado: Boolean,
    alTocarCita: (Cita) -> Unit,
    alTocarLibre: (String) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val cita = renglon.cita
    val horas = stringResource(Res.string.rango_horas, renglon.horaInicio, renglon.horaFin)

    val descripcion = if (cita == null) {
        stringResource(Res.string.a11y_agenda_renglon_libre, horas)
    } else {
        stringResource(
            Res.string.a11y_agenda_renglon_ocupado,
            horas,
            tituloDe(cita),
            stringResource(cita.estado.recurso()),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable {
                if (cita != null) alTocarCita(cita) else alTocarLibre(renglon.horaInicio)
            }
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Text(
            text = renglon.horaInicio,
            style = MaterialTheme.typography.labelLarge,
            color = colores.textoSecundario,
            modifier = Modifier.width(ANCHO_COLUMNA_HORA),
        )
        if (cita == null) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = AreaTactilMinima),
                color = if (marcado) colores.fondoCitaEnCurso else colores.fondoCampo,
                shape = FormaSalud.sutil,
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(Res.string.agenda_hora_libre),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.textoSecundario,
                        modifier = Modifier.padding(horizontal = espaciado.medio),
                    )
                }
            }
        } else {
            TarjetaDeCita(cita = cita, modifier = Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------- Vista de dos semanas

@Composable
private fun VistaDosSemanas(
    estado: AgendaMedicoUiState,
    alTocarCita: (Cita) -> Unit,
    alTocarDia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val porDia = estado.citasVisibles.groupBy { it.fecha }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = espaciado.medio,
            vertical = espaciado.compacto,
        ),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        items(estado.rango.fechas, key = { it }) { fecha ->
            val delDia = porDia[fecha].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
                Text(
                    text = fechaLarga(fecha),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AreaTactilMinima)
                        .clickable { alTocarDia(fecha) }
                        .semantics { heading() },
                )
                if (delDia.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.agenda_sin_citas),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoSecundario,
                    )
                } else {
                    delDia.forEach { cita ->
                        TarjetaDeCita(
                            cita = cita,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alTocarCita(cita) },
                        )
                    }
                }
                HorizontalDivider(color = colores.separador)
            }
        }
    }
}

// ------------------------------------------------------------ Vista mensual

@Composable
private fun VistaMensual(
    estado: AgendaMedicoUiState,
    alTocarDia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val iniciales = stringArrayResource(Res.array.opciones_iniciales_dias)

    // Los huecos de delante alinean el dia 1 bajo su columna. Sin ellos, el
    // calendario mostraria los dias corridos y el medico leeria mal el dia de
    // la semana de cada cita.
    val celdas: List<CeldaDelMes?> =
        List(estado.desplazamientoDelMes) { null } + estado.celdasDelMes

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = espaciado.medio),
    ) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            iniciales.forEach { inicial ->
                Text(
                    text = inicial,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(espaciado.compacto))
        celdas.chunked(DIAS_POR_SEMANA).forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                semana.forEach { celda ->
                    if (celda == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        CeldaDeDia(
                            celda = celda,
                            esAncla = celda.fecha == estado.fechaAncla,
                            alTocar = alTocarDia,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Relleno de la ultima semana incompleta: sin el, los dias
                // sobrantes se estirarian y quedarian mas anchos que los demas.
                repeat(DIAS_POR_SEMANA - semana.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CeldaDeDia(
    celda: CeldaDelMes,
    esAncla: Boolean,
    alTocar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val fechaLegible = fechaCorta(celda.fecha)
    val descripcion = if (celda.vacio) {
        stringResource(Res.string.a11y_agenda_celda_mes_vacia, fechaLegible)
    } else {
        stringResource(Res.string.a11y_agenda_celda_mes, fechaLegible, celda.citas.size)
    }
    val dia = celda.fecha.takeLast(2).trimStart('0')

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(espaciado.minimo)
            .background(
                color = if (esAncla) colores.fondoCitaEnCurso else MaterialTheme.colorScheme.background,
                shape = FormaSalud.sutil,
            )
            .clickable { alTocar(celda.fecha) }
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dia,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(espaciado.minimo))
            // Un punto por estado presente ese dia, no uno por cita: con la
            // agenda llena, una fila de doce puntos no dice nada, y estos cuatro
            // como mucho si dicen "hay algo por confirmar" de un vistazo.
            Row(horizontalArrangement = Arrangement.spacedBy(espaciado.minimo / 2)) {
                celda.citas.map { it.estado }.distinct().take(MAXIMO_PUNTOS).forEach { estado ->
                    Box(
                        Modifier
                            .size(espaciado.compacto / 2)
                            .background(colores.parDeEstado(estado).first, CircleShape),
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Tarjeta

/**
 * Tarjeta de una cita en el calendario.
 *
 * La superficie es la de cualquier tarjeta del sistema, NO el color del estado:
 * con la superficie tenida, una semana llena convertia el calendario en un
 * mosaico de colores donde ya no se leia ningun nombre. El estado lo lleva la
 * insignia, que es donde el ojo lo busca.
 */
@Composable
private fun TarjetaDeCita(cita: Cita, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Surface(
        modifier = modifier.heightIn(min = AreaTactilMinima),
        color = colores.fondoTarjeta,
        shape = FormaSalud.grande,
        border = bordeDeTarjeta(),
        shadowElevation = elevacionDeTarjeta(),
    ) {
        Column(Modifier.padding(espaciado.medio)) {
            Text(
                text = tituloDe(cita),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(espaciado.compacto))
            InsigniaEstado(cita.estado)
        }
    }
}

/** Nombre del paciente, o la nota del bloqueo si no hay paciente que mostrar. */
@Composable
private fun tituloDe(cita: Cita): String = when {
    cita.esBloqueo && cita.notaBloqueo.isNotBlank() -> cita.notaBloqueo
    cita.esBloqueo -> stringResource(Res.string.agenda_estado_bloqueado)
    else -> cita.contacto?.nombreCompleto.orEmpty()
}

// ----------------------------------------------------------------- Leyenda

@Composable
private fun LeyendaDeEstados(estado: AgendaMedicoUiState) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val resumen = estado.resumenPorEstado
    if (resumen.isEmpty()) return

    val descripcion = stringResource(Res.string.a11y_agenda_leyenda)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = espaciado.medio)
            .margenInferiorSeguro(respiro = espaciado.medio)
            .semantics { contentDescription = descripcion },
    ) {
        HorizontalDivider(color = colores.separador)
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.agenda_leyenda_titulo),
            style = MaterialTheme.typography.labelSmall,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        Row(horizontalArrangement = Arrangement.spacedBy(espaciado.medio)) {
            EstadoCita.entries.filter { resumen.containsKey(it) }.forEach { estadoCita ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(espaciado.compacto)
                            .background(colores.parDeEstado(estadoCita).first, CircleShape),
                    )
                    Spacer(Modifier.width(espaciado.minimo))
                    Text(
                        text = stringResource(
                            Res.string.agenda_leyenda_conteo,
                            stringResource(estadoCita.recurso()),
                            resumen.getValue(estadoCita),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.textoSecundario,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ Barra bloqueo

@Composable
private fun BarraDeBloqueo(estado: AgendaMedicoUiState, viewModel: AgendaMedicoViewModel) {
    val espaciado = LocalEspaciadoSalud.current
    val inicio = estado.inicioDeBloqueo.orEmpty()
    BarraAccionInferior {
        Text(
            text = stringResource(Res.string.agenda_bloqueo_en_curso, inicio),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = stringResource(Res.string.agenda_bloqueo_ayuda),
            style = MaterialTheme.typography.bodySmall,
            color = LocalColoresSalud.current.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        CampoTextoRellenoSalud(
            valor = estado.notaDeBloqueo,
            alCambiar = viewModel::actualizarNotaDeBloqueo,
            etiqueta = stringResource(Res.string.agenda_bloqueo_campo_nota),
            marcador = stringResource(Res.string.agenda_bloqueo_marcador_nota),
            descripcionAccesible = stringResource(Res.string.a11y_agenda_bloqueo_campo_nota),
        )
        BotonSecundarioSalud(
            etiqueta = stringResource(Res.string.agenda_accion_cancelar_bloqueo),
            alPulsar = viewModel::cancelarBloqueo,
            descripcionAccesible = stringResource(Res.string.agenda_accion_cancelar_bloqueo),
        )
    }
}

// -------------------------------------------------------------- Auxiliares

@Composable
private fun IndicadorCargando() {
    val descripcion = stringResource(Res.string.a11y_agenda_cargando)
    val espaciado = LocalEspaciadoSalud.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(espaciado.generoso)
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = LocalColoresSalud.current.acentoAccion)
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.agenda_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresSalud.current.textoSecundario,
        )
    }
}


@Composable
private fun AvisoErrorAccion() {
    val espaciado = LocalEspaciadoSalud.current
    Text(
        text = stringResource(Res.string.agenda_error_accion),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

private val ANCHO_COLUMNA_HORA = 52.dp
private const val DIAS_POR_SEMANA = 7
private const val MAXIMO_PUNTOS = 4
