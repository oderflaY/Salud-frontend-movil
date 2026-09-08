package com.eter.salud.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.presentation.home.AccesoRapido
import com.eter.salud.presentation.home.HomeUiState
import com.eter.salud.presentation.home.HomeViewModel
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_home_acceso_historial_corto
import salud.shared.generated.resources.a11y_home_acceso_medicos
import salud.shared.generated.resources.a11y_home_acceso_rfid
import salud.shared.generated.resources.a11y_home_acceso_sintomas
import salud.shared.generated.resources.a11y_home_accion_cerrar_sesion
import salud.shared.generated.resources.a11y_home_adherencia
import salud.shared.generated.resources.a11y_home_cargando
import salud.shared.generated.resources.a11y_home_estado_perfil
import salud.shared.generated.resources.a11y_home_rfid
import salud.shared.generated.resources.a11y_home_saludo
import salud.shared.generated.resources.a11y_home_toma
import salud.shared.generated.resources.a11y_home_toma_omitir
import salud.shared.generated.resources.a11y_home_toma_tomado
import salud.shared.generated.resources.home_acceso_historial_corto
import salud.shared.generated.resources.home_acceso_medicos
import salud.shared.generated.resources.home_acceso_rfid
import salud.shared.generated.resources.home_acceso_sintomas
import salud.shared.generated.resources.home_accesos_titulo
import salud.shared.generated.resources.home_accion_cerrar_sesion
import salud.shared.generated.resources.home_adherencia_detalle
import salud.shared.generated.resources.home_adherencia_porcentaje
import salud.shared.generated.resources.home_adherencia_sin_datos
import salud.shared.generated.resources.home_adherencia_titulo
import salud.shared.generated.resources.home_estado_cargando
import salud.shared.generated.resources.home_estado_error
import salud.shared.generated.resources.home_medicacion_pendientes
import salud.shared.generated.resources.home_medicacion_sin_tomas
import salud.shared.generated.resources.home_medicacion_titulo
import salud.shared.generated.resources.home_medicacion_todo_listo
import salud.shared.generated.resources.home_perfil_pendiente_descripcion
import salud.shared.generated.resources.home_perfil_pendiente_titulo
import salud.shared.generated.resources.home_rfid_activa
import salud.shared.generated.resources.home_rfid_inactiva
import salud.shared.generated.resources.home_saludo_con_nombre
import salud.shared.generated.resources.home_titulo
import salud.shared.generated.resources.home_toma_accion_omitir
import salud.shared.generated.resources.home_toma_accion_tomado
import salud.shared.generated.resources.home_toma_detalle
import salud.shared.generated.resources.home_toma_estado_omitido
import salud.shared.generated.resources.home_toma_estado_pendiente
import salud.shared.generated.resources.home_toma_estado_tomado
import salud.shared.generated.resources.home_toma_estado_tomado_tarde

/**
 * Panel principal del paciente.
 *
 * Orden deliberado de arriba abajo: quien eres y si tu tarjeta funciona, que
 * tienes que tomar hoy, a donde puedes ir, y como vas en la semana. La
 * medicacion del dia va primero entre los bloques de contenido porque es lo
 * unico que caduca.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, iconografia vectorial
 * estandar, color y espaciado solo por tokens semanticos, y etiqueta accesible
 * en cada elemento navegable o accionable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    alCompletarPerfil: () -> Unit = {},
    alAbrirAcceso: (AccesoRapido) -> Unit = {},
    alCerrarSesion: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    LaunchedEffect(estado.idPaciente) { viewModel.cargar() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.home_titulo),
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
                        // Rojo de error explicito: cerrar sesion es una accion
                        // destructiva y debe leerse como tal, no como una mas.
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
                // Zona segura: el contenido nunca termina bajo la barra de
                // gestos del sistema ni bajo el Home Indicator.
                .margenInferiorSeguro(),
            verticalArrangement = Arrangement.spacedBy(espaciado.generoso),
        ) {
            Spacer(Modifier.height(espaciado.minimo))
            Cabecera(estado)

            if (estado.cargando) {
                IndicadorCargando()
                return@Column
            }
            if (estado.errorCarga) {
                MensajeError()
                return@Column
            }

            if (estado.perfilEmergenciaPendiente) {
                AvisoPerfilPendiente(alCompletarPerfil)
            }

            MedicacionDelDia(
                estado = estado,
                alMarcarTomada = viewModel::marcarTomada,
                alOmitir = viewModel::omitirToma,
            )
            AccesosRapidos(estado.accesosRapidos, alAbrirAcceso)
            ResumenSemanal(estado.resumenSemanal)
        }
    }
}

/** Saludo y estado de la tarjeta de emergencia: quien eres y si estas cubierto. */
@Composable
private fun Cabecera(estado: HomeUiState) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val saludo = stringResource(Res.string.home_saludo_con_nombre, estado.nombrePaciente)
    val anuncioSaludo = stringResource(Res.string.a11y_home_saludo, estado.nombrePaciente)
    val estadoTarjeta = if (estado.tarjetaRfidActiva) {
        stringResource(Res.string.home_rfid_activa)
    } else {
        stringResource(Res.string.home_rfid_inactiva)
    }
    val anuncioTarjeta = stringResource(Res.string.a11y_home_rfid, estadoTarjeta)

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
            text = estadoTarjeta,
            style = MaterialTheme.typography.bodyMedium,
            color = if (estado.tarjetaRfidActiva) colores.exito else colores.acentoCritico,
            modifier = Modifier.semantics { contentDescription = anuncioTarjeta },
        )
    }
}

/**
 * Nucleo de la pantalla: las tomas de hoy con sus dos acciones. Los botones son
 * de ancho generoso y area tactil amplia porque se pulsan a diario, a veces con
 * prisa o con la vista cansada.
 */
@Composable
private fun MedicacionDelDia(
    estado: HomeUiState,
    alMarcarTomada: (String) -> Unit,
    alOmitir: (String) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Column(Modifier.padding(espaciado.amplio)) {
            Text(
                text = stringResource(Res.string.home_medicacion_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = when {
                    estado.tomasDelDia.isEmpty() ->
                        stringResource(Res.string.home_medicacion_sin_tomas)

                    estado.todasLasTomasRegistradas ->
                        stringResource(Res.string.home_medicacion_todo_listo)

                    else -> stringResource(
                        Res.string.home_medicacion_pendientes,
                        estado.tomasPendientes,
                        estado.tomasDelDia.size,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )

            estado.tomasDelDia.forEach { toma ->
                Spacer(Modifier.height(espaciado.amplio))
                FilaToma(
                    toma = toma,
                    bloqueada = estado.tomaEnCurso != null,
                    alMarcarTomada = { alMarcarTomada(toma.idToma) },
                    alOmitir = { alOmitir(toma.idToma) },
                )
            }

            if (estado.errorRegistroToma) {
                Spacer(Modifier.height(espaciado.medio))
                Text(
                    text = stringResource(Res.string.home_estado_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}

@Composable
private fun FilaToma(
    toma: TomaDelDia,
    bloqueada: Boolean,
    alMarcarTomada: () -> Unit,
    alOmitir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val estadoTexto = stringResource(toma.estado.recurso())
    val descripcion = stringResource(
        Res.string.a11y_home_toma,
        toma.medicamento,
        toma.dosis,
        toma.horaProgramada,
        estadoTexto,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = toma.medicamento,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(
                        Res.string.home_toma_detalle,
                        toma.dosis,
                        toma.horaProgramada,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
            }
            if (toma.estado.estaRegistrada) {
                Text(
                    text = estadoTexto,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (toma.estado == EstadoToma.OMITIDO) {
                        colores.acentoCritico
                    } else {
                        colores.exito
                    },
                )
            }
        }

        if (!toma.estado.estaRegistrada) {
            Spacer(Modifier.height(espaciado.medio))
            val descripcionTomado =
                stringResource(Res.string.a11y_home_toma_tomado, toma.medicamento)
            val descripcionOmitir =
                stringResource(Res.string.a11y_home_toma_omitir, toma.medicamento)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
            ) {
                Button(
                    onClick = alMarcarTomada,
                    enabled = !bloqueada,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AreaTactilMinima)
                        .semantics { contentDescription = descripcionTomado },
                    shape = RoundedCornerShape(espaciado.compacto + espaciado.minimo),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colores.acentoAccion,
                        contentColor = colores.sobreAcentoAccion,
                    ),
                ) {
                    Text(stringResource(Res.string.home_toma_accion_tomado))
                }
                OutlinedButton(
                    onClick = alOmitir,
                    enabled = !bloqueada,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AreaTactilMinima)
                        .semantics { contentDescription = descripcionOmitir },
                    shape = RoundedCornerShape(espaciado.compacto + espaciado.minimo),
                ) {
                    Text(stringResource(Res.string.home_toma_accion_omitir))
                }
            }
        }
        Spacer(Modifier.height(espaciado.compacto))
        HorizontalDivider(color = colores.separador)
    }
}

/** Rejilla de dos columnas: cuatro destinos fijos, siempre en el mismo sitio. */
@Composable
private fun AccesosRapidos(accesos: List<AccesoRapido>, alAbrir: (AccesoRapido) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.home_accesos_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.medio))
        accesos.chunked(2).forEach { pareja ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(espaciado.medio),
            ) {
                pareja.forEach { acceso ->
                    TarjetaAcceso(
                        acceso = acceso,
                        alAbrir = { alAbrir(acceso) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(espaciado.medio))
        }
    }
}

@Composable
private fun TarjetaAcceso(
    acceso: AccesoRapido,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val etiqueta = stringResource(acceso.recurso())
    val descripcion = stringResource(acceso.recursoAccesible())

    Surface(
        modifier = modifier
            .heightIn(min = AreaTactilMinima * 2)
            .clickable(onClick = alAbrir)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Column(
            modifier = Modifier.padding(espaciado.medio),
            verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
        ) {
            IconoSalud(glifo = acceso.glifo())
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Cumplimiento semanal: una sola barra, un solo numero. */
@Composable
private fun ResumenSemanal(resumen: ResumenAdherencia?) {
    if (resumen == null) return
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val anuncio = stringResource(
        Res.string.a11y_home_adherencia,
        resumen.porcentaje,
        resumen.tomasCumplidas,
        resumen.tomasProgramadas,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = anuncio },
    ) {
        Text(
            text = stringResource(Res.string.home_adherencia_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.medio))
        LinearProgressIndicator(
            progress = { resumen.porcentaje / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = if (resumen.sinDatos) {
                stringResource(Res.string.home_adherencia_sin_datos)
            } else {
                stringResource(Res.string.home_adherencia_porcentaje, resumen.porcentaje) +
                    " - " +
                    stringResource(
                        Res.string.home_adherencia_detalle,
                        resumen.tomasCumplidas,
                        resumen.tomasProgramadas,
                    )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

/** Unico bloque que reclama atencion: el perfil de emergencia incompleto. */
@Composable
private fun AvisoPerfilPendiente(alCompletarPerfil: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val titulo = stringResource(Res.string.home_perfil_pendiente_titulo)
    val anuncio = stringResource(Res.string.a11y_home_estado_perfil, titulo)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alCompletarPerfil)
            .semantics(mergeDescendants = true) { contentDescription = anuncio },
        color = colores.fondoCritico,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Column(Modifier.padding(espaciado.amplio)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = colores.acentoCritico,
            )
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.home_perfil_pendiente_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
        }
    }
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_home_cargando)
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
            text = stringResource(Res.string.home_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MensajeError() {
    Text(
        text = stringResource(Res.string.home_estado_error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

private fun EstadoToma.recurso() = when (this) {
    EstadoToma.PENDIENTE -> Res.string.home_toma_estado_pendiente
    EstadoToma.TOMADO -> Res.string.home_toma_estado_tomado
    EstadoToma.TOMADO_TARDE -> Res.string.home_toma_estado_tomado_tarde
    EstadoToma.OMITIDO -> Res.string.home_toma_estado_omitido
}

private fun AccesoRapido.recurso() = when (this) {
    AccesoRapido.TARJETA_RFID -> Res.string.home_acceso_rfid
    AccesoRapido.DIARIO_SINTOMAS -> Res.string.home_acceso_sintomas
    AccesoRapido.MIS_MEDICOS -> Res.string.home_acceso_medicos
    AccesoRapido.HISTORIAL -> Res.string.home_acceso_historial_corto
}

private fun AccesoRapido.recursoAccesible() = when (this) {
    AccesoRapido.TARJETA_RFID -> Res.string.a11y_home_acceso_rfid
    AccesoRapido.DIARIO_SINTOMAS -> Res.string.a11y_home_acceso_sintomas
    AccesoRapido.MIS_MEDICOS -> Res.string.a11y_home_acceso_medicos
    AccesoRapido.HISTORIAL -> Res.string.a11y_home_acceso_historial_corto
}

private fun AccesoRapido.glifo() = when (this) {
    AccesoRapido.TARJETA_RFID -> GlifoSalud.TARJETA
    AccesoRapido.DIARIO_SINTOMAS -> GlifoSalud.DIARIO
    AccesoRapido.MIS_MEDICOS -> GlifoSalud.MEDICOS
    AccesoRapido.HISTORIAL -> GlifoSalud.HISTORIAL
}
