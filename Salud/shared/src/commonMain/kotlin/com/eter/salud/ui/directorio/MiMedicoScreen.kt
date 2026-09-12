package com.eter.salud.ui.directorio

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.presentation.directorio.DescubrimientoMedicoUiState
import com.eter.salud.presentation.directorio.DescubrimientoMedicoViewModel
import com.eter.salud.presentation.directorio.DirectorioUiState
import com.eter.salud.presentation.directorio.ErrorDirectorio
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.bordeDeTarjeta
import com.eter.salud.ui.componentes.elevacionDeTarjeta
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_directorio_accion_contactar
import salud.shared.generated.resources.a11y_directorio_busqueda
import salud.shared.generated.resources.a11y_directorio_doctor_sin_verificar
import salud.shared.generated.resources.a11y_directorio_doctor_verificado
import salud.shared.generated.resources.a11y_directorio_filtro_especialidad
import salud.shared.generated.resources.a11y_mimedico_accion_iniciar_consulta
import salud.shared.generated.resources.a11y_mimedico_cargando
import salud.shared.generated.resources.a11y_mimedico_ficha
import salud.shared.generated.resources.directorio_accion_contactar
import salud.shared.generated.resources.directorio_accion_solicitando
import salud.shared.generated.resources.directorio_busqueda_placeholder
import salud.shared.generated.resources.directorio_error_vinculacion
import salud.shared.generated.resources.directorio_filtro_todas
import salud.shared.generated.resources.directorio_sin_doctores
import salud.shared.generated.resources.directorio_sin_resultados
import salud.shared.generated.resources.directorio_titulo
import salud.shared.generated.resources.directorio_universidad_egresado
import salud.shared.generated.resources.mimedico_accion_iniciar_consulta
import salud.shared.generated.resources.mimedico_estado_cargando
import salud.shared.generated.resources.mimedico_estado_vacio_descripcion
import salud.shared.generated.resources.mimedico_estado_vacio_titulo
import salud.shared.generated.resources.mimedico_ficha_cedula_pendiente
import salud.shared.generated.resources.mimedico_ficha_cedula_verificada
import salud.shared.generated.resources.mimedico_ficha_disponibilidad
import salud.shared.generated.resources.mimedico_ficha_titulo
import salud.shared.generated.resources.mimedico_ficha_universidad
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
    /**
     * Lanza la teleconsulta a pantalla completa.
     *
     * El chat NO se dibuja dentro de esta seccion: una consulta clinica necesita
     * el alto entero de la pantalla para un diagnostico largo, y con la barra de
     * secciones abajo el teclado colisiona con el campo de texto. Esta pantalla
     * es la ficha del medico; la conversacion es otro destino.
     */
    alVolver: () -> Unit = {},
    alIniciarConsulta: (MedicoVinculado) -> Unit = {},
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    // Sin barra superior ni flecha: esto es una PESTANA, no una pantalla
    // apilada, y una flecha de retroceso sobre una raiz de navegacion promete
    // una vuelta atras que no existe.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentPadding = PaddingValues(
            start = espaciado.amplio,
            end = espaciado.amplio,
            top = espaciado.medio,
            bottom = espaciado.respiro,
        ),
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        item(key = "cabecera_seccion") {
            // Ahora es una pantalla apilada (se llega desde el boton flotante de
            // la bandeja), asi que si lleva flecha de retroceso.
            CabeceraGrande(
                titulo = stringResource(Res.string.directorio_titulo),
                // Con una ficha abierta, la flecha vuelve a la lista: el
                // paciente puede estar comparando medicos.
                accion = {
                    BotonAtras(alPulsar = if (estado.medicoElegido != null) viewModel::cerrarFicha else alVolver)
                },
            )
        }
        run {
            val elegido = estado.medicoElegido
            if (elegido != null) {
                item(key = "ficha_medico") {
                    FichaDelMedico(
                        medico = elegido,
                        perfil = estado.perfilDelElegido,
                        alIniciarConsulta = { alIniciarConsulta(elegido) },
                    )
                }
                return@LazyColumn
            }

            // "Aun no tienes medico" solo es verdad sin ninguna vinculacion; con
            // una ya hecha, el directorio es para sumar un especialista.
            if (estado.medicoVinculado == null) {
                item(key = "cabecera") { EstadoVacio() }
            }

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
                    BloqueDeError(
                        mensaje = stringResource(directorio.motivo.recurso()),
                        alReintentar = viewModel::cargar,
                    )
                }

                is DirectorioUiState.ConDoctores -> listaDeDoctores(estado, viewModel)
            }
        }
    }
}

/**
 * Ficha clinica del medico vinculado.
 *
 * Es lo que el paciente ve al entrar en su seccion "Mi medico": quien le
 * atiende, con que credenciales y cuando esta disponible, mas la unica accion
 * que importa aqui. Las credenciales van ANTES que el boton a proposito -- se
 * decide consultar a alguien despues de ver quien es, no al reves.
 */
@Composable
private fun FichaDelMedico(
    medico: MedicoVinculado,
    perfil: PerfilDoctorDirectorio?,
    alIniciarConsulta: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val especialidad = stringResource(medico.especialidad.recurso())

    // Sin ficha descargada se dice lo que la vinculacion SI garantiza. Mostrar
    // "cedula verificada" sin haberla comprobado seria una credencial inventada.
    val estadoCedula = perfil?.let {
        stringResource(
            if (it.cedulaVerificada) Res.string.mimedico_ficha_cedula_verificada
            else Res.string.mimedico_ficha_cedula_pendiente,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(espaciado.medio)) {
        Text(
            text = stringResource(Res.string.mimedico_ficha_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        val descripcion = stringResource(
            Res.string.a11y_mimedico_ficha,
            medico.nombreCompleto,
            especialidad,
            estadoCedula.orEmpty(),
        )
        TarjetaSalud(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = descripcion
            },
        ) {
            Text(
                text = medico.nombreCompleto,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = especialidad,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )

            if (estadoCedula != null) {
                Spacer(Modifier.height(espaciado.compacto))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoSalud(
                        glifo = if (perfil.cedulaVerificada) GlifoSalud.VERIFICADO else GlifoSalud.HISTORIAL,
                        lado = LADO_GLIFO_CEDULA,
                        color = if (perfil.cedulaVerificada) colores.exito else colores.textoAdvertencia,
                    )
                    Spacer(Modifier.width(espaciado.compacto))
                    Text(
                        text = estadoCedula,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (perfil.cedulaVerificada) colores.exito else colores.textoAdvertencia,
                    )
                }
            }

            if (perfil != null && perfil.universidad.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.mimedico_ficha_universidad, perfil.universidad),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoSecundario,
                )
            }

            if (perfil != null && perfil.disponibilidad.isNotBlank()) {
                Spacer(Modifier.height(espaciado.compacto))
                HorizontalDivider(color = colores.separador)
                Spacer(Modifier.height(espaciado.compacto))
                Text(
                    text = stringResource(Res.string.mimedico_ficha_disponibilidad),
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoSecundario,
                )
                Text(
                    text = perfil.disponibilidad,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.mimedico_accion_iniciar_consulta),
            alPulsar = alIniciarConsulta,
            descripcionAccesible = stringResource(
                Res.string.a11y_mimedico_accion_iniciar_consulta,
                medico.nombreCompleto,
            ),
        )
    }
}

private val LADO_GLIFO_CEDULA = 18.dp

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
            .background(colores.fondoCampo, FormaSalud.media)
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
    val forma = FormaSalud.pastilla

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
        border = bordeDeTarjeta(),
        shadowElevation = elevacionDeTarjeta(),
        shape = FormaSalud.media,
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

