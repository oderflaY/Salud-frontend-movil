package com.eter.salud.ui.directorio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.presentation.directorio.DescubrimientoMedicoUiState
import com.eter.salud.presentation.directorio.DescubrimientoMedicoViewModel
import com.eter.salud.presentation.directorio.DirectorioUiState
import com.eter.salud.presentation.directorio.ErrorDirectorio
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_boton_atras
import salud.shared.generated.resources.a11y_directorio_accion_contactar
import salud.shared.generated.resources.a11y_directorio_busqueda
import salud.shared.generated.resources.a11y_directorio_doctor_sin_verificar
import salud.shared.generated.resources.a11y_directorio_doctor_verificado
import salud.shared.generated.resources.a11y_directorio_filtro_especialidad
import salud.shared.generated.resources.a11y_mimedico_cargando
import salud.shared.generated.resources.accion_atras
import salud.shared.generated.resources.directorio_accion_contactar
import salud.shared.generated.resources.directorio_accion_solicitando
import salud.shared.generated.resources.directorio_busqueda_placeholder
import salud.shared.generated.resources.directorio_error_vinculacion
import salud.shared.generated.resources.directorio_filtro_todas
import salud.shared.generated.resources.directorio_sin_doctores
import salud.shared.generated.resources.directorio_sin_resultados
import salud.shared.generated.resources.directorio_titulo
import salud.shared.generated.resources.directorio_universidad_egresado
import salud.shared.generated.resources.mimedico_estado_cargando
import salud.shared.generated.resources.mimedico_estado_vacio_descripcion
import salud.shared.generated.resources.mimedico_estado_vacio_titulo
import salud.shared.generated.resources.mimedico_titulo

/**
 * Seccion "Mi Medico": estado vacio + Directorio Medico Inteligente.
 *
 * Se instancia SOLO cuando el paciente aun no tiene un doctor vinculado -- en
 * cuanto lo tiene, `App.kt` construye el chat con su propio ViewModel y esta
 * pantalla deja de existir, exactamente como decide entre Onboarding y Perfil.
 * Esta pantalla nunca necesita saber que el chat existe.
 *
 * La lista se pinta con un unico [LazyColumn] que ES el contenedor de scroll de
 * la pantalla: la cabecera viaja como `item`. Envolverla en una `Column` con
 * `verticalScroll` haria que la lista se midiera con altura infinita y la app
 * reventaria al abrir esta pantalla.
 *
 * Cumplimiento del DM: cero texto literal, sin emojis, iconografia vectorial
 * estandar, color y espaciado solo por tokens semanticos, Modo Oscuro
 * automatico y margenes de seguridad inferiores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiMedicoScreen(
    viewModel: DescubrimientoMedicoViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colores.fondoConversacion,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.mimedico_titulo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    val descripcion = stringResource(Res.string.a11y_boton_atras)
                    TextButton(
                        onClick = alVolver,
                        modifier = Modifier
                            .heightIn(min = AreaTactilMinima)
                            .semantics { contentDescription = descripcion },
                    ) {
                        Text(stringResource(Res.string.accion_atras))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colores.fondoConversacion,
                ),
            )
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .margenInferiorSeguro(),
            contentPadding = PaddingValues(
                start = espaciado.amplio,
                end = espaciado.amplio,
                top = espaciado.amplio,
                bottom = espaciado.respiro,
            ),
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            item(key = "cabecera") { EstadoVacio() }

            item(key = "titulo_directorio") {
                Text(
                    text = stringResource(Res.string.directorio_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }

            item(key = "busqueda") {
                BarraBusqueda(
                    valor = estado.textoBusqueda,
                    alCambiar = viewModel::actualizarTextoBusqueda,
                )
            }

            item(key = "filtros") {
                FiltrosEspecialidad(
                    seleccion = estado.especialidadFiltro,
                    alSeleccionar = viewModel::filtrarPorEspecialidad,
                )
            }

            if (estado.errorVinculacion) {
                item(key = "error_vinculacion") { MensajeErrorVinculacion() }
            }

            // Los cuatro escenarios del directorio: el compilador obliga a
            // resolverlos todos, ninguno se queda sin pintar.
            when (val directorio = estado.directorio) {
                DirectorioUiState.Cargando -> item(key = "cargando") { IndicadorCargando() }

                DirectorioUiState.Vacio -> item(key = "vacio") { MensajeSinDoctores() }

                is DirectorioUiState.Error -> item(key = "error") {
                    MensajeError(directorio.motivo)
                }

                is DirectorioUiState.ConDoctores -> listaDeDoctores(estado, viewModel)
            }
        }
    }
}

/**
 * Tarjetas de los doctores ya filtrados por la busqueda. Si el filtro de texto
 * no deja a nadie se avisa aparte: no es lo mismo que el directorio venga vacio.
 */
private fun LazyListScope.listaDeDoctores(
    estado: DescubrimientoMedicoUiState,
    viewModel: DescubrimientoMedicoViewModel,
) {
    val doctores = estado.doctoresFiltrados
    if (doctores.isEmpty()) {
        item(key = "sin_resultados") { MensajeSinResultados() }
        return
    }
    items(doctores, key = { it.idMedico }) { doctor ->
        TarjetaDoctor(
            doctor = doctor,
            solicitando = estado.idSolicitandoVinculacion == doctor.idMedico,
            deshabilitado = estado.idSolicitandoVinculacion != null,
            alContactar = { viewModel.solicitarVinculacion(doctor.idMedico) },
        )
    }
}

@Composable
private fun EstadoVacio() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column {
        Text(
            text = stringResource(Res.string.mimedico_estado_vacio_titulo),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(espaciado.compacto))
        Text(
            text = stringResource(Res.string.mimedico_estado_vacio_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

/** Barra de busqueda con lupa: filtra el directorio ya descargado, sin gastar red. */
@Composable
private fun BarraBusqueda(valor: String, alCambiar: (String) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_directorio_busqueda)
    val marcador = stringResource(Res.string.directorio_busqueda_placeholder)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .background(colores.fondoCampo, RoundedCornerShape(espaciado.medio))
            .padding(horizontal = espaciado.medio)
            .semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        IconoSalud(glifo = GlifoSalud.BUSCAR, lado = 20.dp, color = colores.textoSecundario)
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) {
                Text(
                    text = marcador,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colores.textoSecundario,
                )
            }
            BasicTextField(
                value = valor,
                onValueChange = alCambiar,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Chips de especialidad, con "Todas" siempre primero. */
@Composable
private fun FiltrosEspecialidad(
    seleccion: Especialidad?,
    alSeleccionar: (Especialidad?) -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val descripcionGrupo = stringResource(Res.string.a11y_directorio_filtro_especialidad)

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = descripcionGrupo },
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        item {
            ChipFiltro(
                etiqueta = stringResource(Res.string.directorio_filtro_todas),
                elegido = seleccion == null,
                alPulsar = { alSeleccionar(null) },
            )
        }
        items(Especialidad.entries) { especialidad ->
            ChipFiltro(
                etiqueta = stringResource(especialidad.recurso()),
                elegido = seleccion == especialidad,
                alPulsar = { alSeleccionar(especialidad) },
            )
        }
    }
}

@Composable
private fun ChipFiltro(etiqueta: String, elegido: Boolean, alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val forma = RoundedCornerShape(percent = 50)

    Text(
        text = etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = if (elegido) colores.sobreAcentoAccion else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .background(if (elegido) colores.acentoAccion else colores.fondoCampo, forma)
            .clickable(onClick = alPulsar)
            .semantics { selected = elegido }
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto + 2.dp),
    )
}

/** Tarjeta del doctor: nombre, especialidad, universidad, verificacion y accion. */
@Composable
private fun TarjetaDoctor(
    doctor: PerfilDoctorDirectorio,
    solicitando: Boolean,
    deshabilitado: Boolean,
    alContactar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val especialidadTexto = stringResource(doctor.especialidad.recurso())
    val descripcionTarjeta = if (doctor.cedulaVerificada) {
        stringResource(
            Res.string.a11y_directorio_doctor_verificado,
            doctor.nombreCompleto,
            especialidadTexto,
            doctor.disponibilidad,
        )
    } else {
        stringResource(
            Res.string.a11y_directorio_doctor_sin_verificar,
            doctor.nombreCompleto,
            especialidadTexto,
            doctor.disponibilidad,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcionTarjeta },
        color = colores.fondoTarjeta,
        shape = RoundedCornerShape(espaciado.medio),
    ) {
        Column(Modifier.padding(espaciado.amplio)) {
            Text(
                text = doctor.nombreCompleto,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = especialidadTexto,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
            if (doctor.universidad.isNotBlank()) {
                Text(
                    text = stringResource(
                        Res.string.directorio_universidad_egresado,
                        doctor.universidad,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoSecundario,
                )
            }

            Spacer(Modifier.height(espaciado.compacto))
            if (doctor.cedulaVerificada) {
                IconoSalud(
                    glifo = GlifoSalud.VERIFICADO,
                    lado = 16.dp,
                    color = colores.acentoAccion,
                )
                Spacer(Modifier.height(espaciado.minimo))
            }
            if (doctor.disponibilidad.isNotBlank()) {
                Text(
                    text = doctor.disponibilidad,
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.exito,
                )
            }

            Spacer(Modifier.height(espaciado.medio))
            BotonAccionPrincipal(
                etiqueta = stringResource(Res.string.directorio_accion_contactar),
                alPulsar = alContactar,
                descripcionAccesible = stringResource(
                    Res.string.a11y_directorio_accion_contactar,
                    doctor.nombreCompleto,
                ),
                habilitado = !deshabilitado,
                cargando = solicitando,
                etiquetaCargando = stringResource(Res.string.directorio_accion_solicitando),
            )
        }
    }
}

@Composable
private fun IndicadorCargando() {
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_mimedico_cargando)
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
            text = stringResource(Res.string.mimedico_estado_cargando),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** El backend respondio bien, pero no hay ni un doctor en el directorio. */
@Composable
private fun MensajeSinDoctores() {
    Text(
        text = stringResource(Res.string.directorio_sin_doctores),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalColoresSalud.current.textoSecundario,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Hay doctores, pero la busqueda por texto no dejo ninguno a la vista. */
@Composable
private fun MensajeSinResultados() {
    Text(
        text = stringResource(Res.string.directorio_sin_resultados),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalColoresSalud.current.textoSecundario,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MensajeErrorVinculacion() {
    Text(
        text = stringResource(Res.string.directorio_error_vinculacion),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun MensajeError(motivo: ErrorDirectorio) {
    Text(
        text = stringResource(motivo.recurso()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
