package com.eter.salud.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.eter.salud.presentation.onboarding.ErrorCampoOnboarding
import com.eter.salud.presentation.onboarding.OnboardingPacienteViewModel
import com.eter.salud.presentation.onboarding.OnboardingUiState
import com.eter.salud.presentation.onboarding.PasoOnboarding
import com.eter.salud.ui.componentes.CampoTextoSalud
import com.eter.salud.ui.componentes.CasillaDeclaracion
import com.eter.salud.ui.componentes.EncabezadoPregunta
import com.eter.salud.ui.componentes.ListaCapturada
import com.eter.salud.ui.componentes.SelectorFechaSalud
import com.eter.salud.ui.componentes.SelectorSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.PasoOnboardingEspaciado
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.*

/**
 * Contenido de cada pregunta de la Fase 1.
 *
 * Los campos de un formulario embebido (alergia, tratamiento, contacto) usan
 * estado local: son un buffer de escritura efimero. El dato deja de ser local
 * en cuanto se pulsa Agregar, momento en que el ViewModel lo valida y lo hace
 * parte del borrador, que es la unica fuente de verdad.
 */
@Composable
internal fun ContenidoPaso(
    estado: OnboardingUiState,
    viewModel: OnboardingPacienteViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PasoOnboardingEspaciado),
    ) {
        when (estado.paso) {
            PasoOnboarding.BIENVENIDA -> PasoBienvenida()
            PasoOnboarding.NOMBRE -> PasoNombre(estado, viewModel)
            PasoOnboarding.FECHA_NACIMIENTO -> PasoFechaNacimiento(estado, viewModel)
            PasoOnboarding.GENERO -> PasoGenero(estado, viewModel)
            PasoOnboarding.TELEFONO -> PasoTelefono(estado, viewModel)
            PasoOnboarding.TIPO_SANGRE -> PasoTipoSangre(estado, viewModel)
            PasoOnboarding.ALERGIAS -> PasoAlergias(estado, viewModel)
            PasoOnboarding.CONDICIONES_CRITICAS -> PasoCondicionesCriticas(estado, viewModel)
            PasoOnboarding.MEDICACION_RESCATE -> PasoMedicacionRescate(estado, viewModel)
            PasoOnboarding.TRATAMIENTOS_ACTIVOS -> PasoTratamientos(estado, viewModel)
            PasoOnboarding.CONTACTOS_EMERGENCIA -> PasoContactos(estado, viewModel)
            PasoOnboarding.RESUMEN -> PasoResumen(estado)
        }
    }
}

@Composable
private fun PasoBienvenida() {
    EncabezadoPregunta(
        titulo = stringResource(Res.string.onb_bienvenida_titulo),
        descripcion = stringResource(Res.string.onb_bienvenida_descripcion),
        numeroPregunta = null,
        totalPreguntas = PasoOnboarding.TOTAL_PREGUNTAS,
    )
}

// -------------------------------------------------------------- Pregunta 1

@Composable
private fun PasoNombre(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    Encabezado(estado, Res.string.onb_nombre_titulo, Res.string.onb_nombre_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.nombre,
        alCambiar = viewModel::actualizarNombre,
        etiqueta = stringResource(Res.string.onb_nombre_campo),
        marcador = stringResource(Res.string.onb_nombre_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_nombre_campo),
        error = errorTexto(estado.erroresPaso, ErrorCampoOnboarding.NOMBRE_VACIO),
    )
    CampoTextoSalud(
        valor = estado.borrador.apellidos,
        alCambiar = viewModel::actualizarApellidos,
        etiqueta = stringResource(Res.string.onb_apellidos_campo),
        marcador = stringResource(Res.string.onb_apellidos_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_apellidos_campo),
        error = errorTexto(estado.erroresPaso, ErrorCampoOnboarding.APELLIDOS_VACIO),
    )
}

// -------------------------------------------------------------- Pregunta 2

@Composable
private fun PasoFechaNacimiento(
    estado: OnboardingUiState,
    viewModel: OnboardingPacienteViewModel,
) {
    Encabezado(estado, Res.string.onb_nacimiento_titulo, Res.string.onb_nacimiento_descripcion)
    // Sin atajos rapidos: nadie nacio hoy ni la semana pasada. Solo los tres
    // campos cortos, que evitan recorrer decadas en un calendario nativo.
    SelectorFechaSalud(
        valor = estado.borrador.fechaNacimiento,
        alCambiar = viewModel::actualizarFechaNacimiento,
        fechaDeHoy = estado.fechaDeHoy,
        error = errorTexto(
            estado.erroresPaso,
            ErrorCampoOnboarding.NACIMIENTO_VACIO,
            ErrorCampoOnboarding.NACIMIENTO_FORMATO,
            ErrorCampoOnboarding.NACIMIENTO_FUTURA,
        ),
    )
}

// -------------------------------------------------------------- Pregunta 3

@Composable
private fun PasoGenero(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    Encabezado(estado, Res.string.onb_genero_titulo, Res.string.onb_genero_descripcion)
    SelectorSalud(
        etiqueta = stringResource(Res.string.onb_genero_campo),
        seleccion = estado.borrador.genero,
        opciones = stringArrayResource(Res.array.opciones_genero),
        alSeleccionar = viewModel::actualizarGenero,
        descripcionAccesible = stringResource(Res.string.a11y_onb_genero_campo),
        error = errorTexto(estado.erroresPaso, ErrorCampoOnboarding.GENERO_VACIO),
    )
}

// -------------------------------------------------------------- Pregunta 4

@Composable
private fun PasoTelefono(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    Encabezado(estado, Res.string.onb_telefono_titulo, Res.string.onb_telefono_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.telefono,
        alCambiar = viewModel::actualizarTelefono,
        etiqueta = stringResource(Res.string.onb_telefono_campo),
        marcador = stringResource(Res.string.onb_telefono_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_telefono_campo),
        tipoTeclado = KeyboardType.Phone,
        error = errorTexto(
            estado.erroresPaso,
            ErrorCampoOnboarding.TELEFONO_VACIO,
            ErrorCampoOnboarding.TELEFONO_FORMATO,
        ),
    )
}

// -------------------------------------------------------------- Pregunta 5

@Composable
private fun PasoTipoSangre(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    Encabezado(
        estado = estado,
        titulo = Res.string.onb_sangre_titulo,
        descripcion = Res.string.onb_sangre_descripcion,
        esDatoCritico = true,
    )
    SelectorSalud(
        etiqueta = stringResource(Res.string.onb_sangre_campo),
        seleccion = estado.borrador.tipoSangre,
        opciones = stringArrayResource(Res.array.opciones_tipo_sangre),
        alSeleccionar = viewModel::seleccionarTipoSangre,
        descripcionAccesible = stringResource(Res.string.a11y_onb_sangre_campo),
        error = errorTexto(estado.erroresPaso, ErrorCampoOnboarding.SANGRE_VACIO),
    )
    CasillaDeclaracion(
        marcada = estado.borrador.desconoceTipoSangre,
        alCambiar = viewModel::marcarDesconoceTipoSangre,
        etiqueta = stringResource(Res.string.onb_sangre_desconocido),
    )
}

// -------------------------------------------------------------- Pregunta 6

@Composable
private fun PasoAlergias(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    var alergeno by rememberSaveable { mutableStateOf("") }
    var severidad by rememberSaveable { mutableStateOf("") }
    var reaccion by rememberSaveable { mutableStateOf("") }

    Encabezado(
        estado = estado,
        titulo = Res.string.onb_alergias_titulo,
        descripcion = Res.string.onb_alergias_descripcion,
        esDatoCritico = true,
    )
    CampoTextoSalud(
        valor = alergeno,
        alCambiar = { alergeno = it },
        etiqueta = stringResource(Res.string.onb_alergias_alergeno_campo),
        marcador = stringResource(Res.string.onb_alergias_alergeno_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_alergias_alergeno),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.ALERGIA_ALERGENO_VACIO,
        ),
    )
    SelectorSalud(
        etiqueta = stringResource(Res.string.onb_alergias_severidad_campo),
        seleccion = severidad,
        opciones = stringArrayResource(Res.array.opciones_severidad_alergia),
        alSeleccionar = { severidad = it },
        descripcionAccesible = stringResource(Res.string.a11y_onb_alergias_severidad),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.ALERGIA_SEVERIDAD_VACIA,
        ),
    )
    CampoTextoSalud(
        valor = reaccion,
        alCambiar = { reaccion = it },
        etiqueta = stringResource(Res.string.onb_alergias_reaccion_campo),
        marcador = stringResource(Res.string.onb_alergias_reaccion_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_alergias_reaccion),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.ALERGIA_REACCION_VACIA,
        ),
    )
    BotonAgregar(stringResource(Res.string.onb_alergias_accion_agregar)) {
        val cantidadPrevia = estado.borrador.alergias.size
        viewModel.agregarAlergia(alergeno, severidad, reaccion)
        if (viewModel.estado.value.borrador.alergias.size > cantidadPrevia) {
            alergeno = ""
            severidad = ""
            reaccion = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.onb_alergias_lista_titulo),
        elementos = estado.borrador.alergias.map { it.alergeno },
        descripcionesAccesibles = estado.borrador.alergias.map {
            stringResource(
                Res.string.a11y_onb_alergias_item,
                it.alergeno,
                it.severidad,
                it.reaccion,
            )
        },
        alEliminar = viewModel::eliminarAlergia,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinAlergiasConocidas,
        alCambiar = viewModel::marcarSinAlergiasConocidas,
        etiqueta = stringResource(Res.string.onb_alergias_sin_alergias),
    )
    ErrorDePaso(errorTexto(estado.erroresPaso, ErrorCampoOnboarding.ALERGIAS_SIN_CONFIRMAR))
}

// -------------------------------------------------------------- Pregunta 7

@Composable
private fun PasoCondicionesCriticas(
    estado: OnboardingUiState,
    viewModel: OnboardingPacienteViewModel,
) {
    var condicion by rememberSaveable { mutableStateOf("") }

    Encabezado(
        estado = estado,
        titulo = Res.string.onb_condiciones_titulo,
        descripcion = Res.string.onb_condiciones_descripcion,
        esDatoCritico = true,
    )
    CampoTextoSalud(
        valor = condicion,
        alCambiar = { condicion = it },
        etiqueta = stringResource(Res.string.onb_condiciones_campo),
        marcador = stringResource(Res.string.onb_condiciones_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_condiciones_campo),
        error = errorTexto(estado.erroresFormulario, ErrorCampoOnboarding.CONDICION_VACIA),
    )
    BotonAgregar(stringResource(Res.string.onb_condiciones_accion_agregar)) {
        val cantidadPrevia = estado.borrador.condicionesCriticas.size
        viewModel.agregarCondicionCritica(condicion)
        if (viewModel.estado.value.borrador.condicionesCriticas.size > cantidadPrevia) {
            condicion = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.onb_condiciones_lista_titulo),
        elementos = estado.borrador.condicionesCriticas,
        descripcionesAccesibles = estado.borrador.condicionesCriticas.map {
            stringResource(Res.string.a11y_onb_condiciones_item, it)
        },
        alEliminar = viewModel::eliminarCondicionCritica,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinCondicionesCriticas,
        alCambiar = viewModel::marcarSinCondicionesCriticas,
        etiqueta = stringResource(Res.string.onb_condiciones_sin_condiciones),
    )
    ErrorDePaso(errorTexto(estado.erroresPaso, ErrorCampoOnboarding.CONDICIONES_SIN_CONFIRMAR))
}

// -------------------------------------------------------------- Pregunta 8

@Composable
private fun PasoMedicacionRescate(
    estado: OnboardingUiState,
    viewModel: OnboardingPacienteViewModel,
) {
    var medicamento by rememberSaveable { mutableStateOf("") }

    Encabezado(
        estado = estado,
        titulo = Res.string.onb_rescate_titulo,
        descripcion = Res.string.onb_rescate_descripcion,
        esDatoCritico = true,
    )
    CampoTextoSalud(
        valor = medicamento,
        alCambiar = { medicamento = it },
        etiqueta = stringResource(Res.string.onb_rescate_campo),
        marcador = stringResource(Res.string.onb_rescate_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_rescate_campo),
        error = errorTexto(estado.erroresFormulario, ErrorCampoOnboarding.RESCATE_VACIO),
    )
    BotonAgregar(stringResource(Res.string.onb_rescate_accion_agregar)) {
        val cantidadPrevia = estado.borrador.medicacionRescate.size
        viewModel.agregarMedicacionRescate(medicamento)
        if (viewModel.estado.value.borrador.medicacionRescate.size > cantidadPrevia) {
            medicamento = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.onb_rescate_lista_titulo),
        elementos = estado.borrador.medicacionRescate,
        descripcionesAccesibles = estado.borrador.medicacionRescate.map {
            stringResource(Res.string.a11y_onb_rescate_item, it)
        },
        alEliminar = viewModel::eliminarMedicacionRescate,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinMedicacionRescate,
        alCambiar = viewModel::marcarSinMedicacionRescate,
        etiqueta = stringResource(Res.string.onb_rescate_sin_medicacion),
    )
    ErrorDePaso(errorTexto(estado.erroresPaso, ErrorCampoOnboarding.RESCATE_SIN_CONFIRMAR))
}

// -------------------------------------------------------------- Pregunta 9

@Composable
private fun PasoTratamientos(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    var medicamento by rememberSaveable { mutableStateOf("") }
    var dosis by rememberSaveable { mutableStateOf("") }
    var frecuencia by rememberSaveable { mutableStateOf("") }
    var inventario by rememberSaveable { mutableStateOf("") }
    var via by rememberSaveable { mutableStateOf("") }

    Encabezado(estado, Res.string.onb_tratamientos_titulo, Res.string.onb_tratamientos_descripcion)
    CampoTextoSalud(
        valor = medicamento,
        alCambiar = { medicamento = it },
        etiqueta = stringResource(Res.string.onb_tratamientos_medicamento_campo),
        marcador = stringResource(Res.string.onb_tratamientos_medicamento_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_tratamientos_medicamento),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.TRATAMIENTO_MEDICAMENTO_VACIO,
        ),
    )
    CampoTextoSalud(
        valor = dosis,
        alCambiar = { dosis = it },
        etiqueta = stringResource(Res.string.onb_tratamientos_dosis_campo),
        marcador = stringResource(Res.string.onb_tratamientos_dosis_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_tratamientos_dosis),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.TRATAMIENTO_DOSIS_VACIA,
        ),
    )
    CampoTextoSalud(
        valor = frecuencia,
        alCambiar = { frecuencia = it },
        etiqueta = stringResource(Res.string.onb_tratamientos_frecuencia_campo),
        marcador = stringResource(Res.string.onb_tratamientos_frecuencia_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_tratamientos_frecuencia),
        tipoTeclado = KeyboardType.Number,
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_VACIA,
            ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_INVALIDA,
        ),
    )
    CampoTextoSalud(
        valor = inventario,
        alCambiar = { inventario = it },
        etiqueta = stringResource(Res.string.onb_tratamientos_inventario_campo),
        marcador = stringResource(Res.string.onb_tratamientos_inventario_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_tratamientos_inventario),
        tipoTeclado = KeyboardType.Number,
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.TRATAMIENTO_INVENTARIO_INVALIDO,
        ),
    )
    SelectorSalud(
        etiqueta = stringResource(Res.string.onb_tratamientos_via_campo),
        seleccion = via,
        opciones = stringArrayResource(Res.array.opciones_via_administracion),
        alSeleccionar = { via = it },
        descripcionAccesible = stringResource(Res.string.onb_tratamientos_via_campo),
    )
    BotonAgregar(stringResource(Res.string.onb_tratamientos_accion_agregar)) {
        val cantidadPrevia = estado.borrador.tratamientos.size
        viewModel.agregarTratamiento(medicamento, dosis, frecuencia, inventario, via)
        if (viewModel.estado.value.borrador.tratamientos.size > cantidadPrevia) {
            medicamento = ""
            dosis = ""
            frecuencia = ""
            inventario = ""
            via = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.onb_tratamientos_lista_titulo),
        elementos = estado.borrador.tratamientos.map { it.medicamento },
        descripcionesAccesibles = estado.borrador.tratamientos.map {
            stringResource(
                Res.string.a11y_onb_tratamientos_item,
                it.medicamento,
                it.dosis,
                it.frecuenciaHoras,
            )
        },
        alEliminar = viewModel::eliminarTratamiento,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinTratamientos,
        alCambiar = viewModel::marcarSinTratamientos,
        etiqueta = stringResource(Res.string.onb_tratamientos_sin_tratamientos),
    )
    ErrorDePaso(errorTexto(estado.erroresPaso, ErrorCampoOnboarding.TRATAMIENTOS_SIN_CONFIRMAR))
}

// ------------------------------------------------------------- Pregunta 10

@Composable
private fun PasoContactos(estado: OnboardingUiState, viewModel: OnboardingPacienteViewModel) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var relacion by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }

    Encabezado(
        estado = estado,
        titulo = Res.string.onb_contactos_titulo,
        descripcion = Res.string.onb_contactos_descripcion,
        esDatoCritico = true,
    )
    CampoTextoSalud(
        valor = nombre,
        alCambiar = { nombre = it },
        etiqueta = stringResource(Res.string.onb_contactos_nombre_campo),
        marcador = stringResource(Res.string.onb_contactos_nombre_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_contactos_nombre),
        error = errorTexto(estado.erroresFormulario, ErrorCampoOnboarding.CONTACTO_NOMBRE_VACIO),
    )
    SelectorSalud(
        etiqueta = stringResource(Res.string.onb_contactos_relacion_campo),
        seleccion = relacion,
        opciones = stringArrayResource(Res.array.opciones_relacion_contacto),
        alSeleccionar = { relacion = it },
        descripcionAccesible = stringResource(Res.string.a11y_onb_contactos_relacion),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.CONTACTO_RELACION_VACIA,
        ),
    )
    CampoTextoSalud(
        valor = telefono,
        alCambiar = { telefono = it },
        etiqueta = stringResource(Res.string.onb_contactos_telefono_campo),
        marcador = stringResource(Res.string.onb_contactos_telefono_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_onb_contactos_telefono),
        tipoTeclado = KeyboardType.Phone,
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoOnboarding.CONTACTO_TELEFONO_VACIO,
            ErrorCampoOnboarding.CONTACTO_TELEFONO_FORMATO,
        ),
    )
    BotonAgregar(stringResource(Res.string.onb_contactos_accion_agregar)) {
        val cantidadPrevia = estado.borrador.contactos.size
        viewModel.agregarContacto(
            nombre = nombre,
            relacion = relacion,
            telefono = telefono,
            prioridad = cantidadPrevia + 1,
        )
        if (viewModel.estado.value.borrador.contactos.size > cantidadPrevia) {
            nombre = ""
            relacion = ""
            telefono = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.onb_contactos_lista_titulo),
        elementos = estado.borrador.contactos.map { it.nombre },
        descripcionesAccesibles = estado.borrador.contactos.map {
            stringResource(
                Res.string.a11y_onb_contactos_item,
                it.nombre,
                it.relacion,
                it.telefono,
                it.prioridad.toString(),
            )
        },
        alEliminar = viewModel::eliminarContacto,
    )
    ErrorDePaso(errorTexto(estado.erroresPaso, ErrorCampoOnboarding.CONTACTOS_VACIO))
}

// ---------------------------------------------------------------- Resumen

@Composable
private fun PasoResumen(estado: OnboardingUiState) {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    val borrador = estado.borrador

    EncabezadoPregunta(
        titulo = stringResource(Res.string.onb_resumen_titulo),
        descripcion = stringResource(Res.string.onb_resumen_descripcion),
        numeroPregunta = null,
        totalPreguntas = estado.totalPreguntas,
    )
    SeccionResumen(
        titulo = stringResource(Res.string.onb_resumen_seccion_identidad),
        lineas = listOf(
            borrador.nombre + " " + borrador.apellidos,
            borrador.fechaNacimiento,
            borrador.genero,
            borrador.telefono,
        ),
    )
    SeccionResumen(
        titulo = stringResource(Res.string.onb_resumen_seccion_emergencia),
        lineas = buildList {
            add(borrador.tipoSangre)
            addAll(borrador.alergias.map { it.alergeno + " - " + it.severidad })
            addAll(borrador.condicionesCriticas)
            addAll(borrador.medicacionRescate)
        },
    )
    SeccionResumen(
        titulo = stringResource(Res.string.onb_resumen_seccion_tratamientos),
        lineas = borrador.tratamientos.map { it.medicamento + " " + it.dosis },
    )
    SeccionResumen(
        titulo = stringResource(Res.string.onb_resumen_seccion_contactos),
        lineas = borrador.contactos.map { it.nombre + " - " + it.relacion },
    )
    if (estado.camposCriticosOmitidos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
            Text(
                text = stringResource(Res.string.onb_alerta_criticos_titulo),
                style = MaterialTheme.typography.titleSmall,
                color = colores.acentoCritico,
            )
            estado.camposCriticosOmitidos.forEach { campo ->
                Text(
                    text = stringResource(campo.recurso()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
            }
        }
    }
}

@Composable
internal fun MensajeRegistroCompletado(alTerminarRegistro: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
        Text(
            text = stringResource(Res.string.onb_final_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = colores.exito,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.onb_final_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        TextButton(
            onClick = alTerminarRegistro,
            modifier = Modifier.heightIn(min = AreaTactilMinima),
        ) {
            Text(stringResource(Res.string.onb_final_accion_ir_perfil))
        }
    }
}

// -------------------------------------------------------------- Utilidades

@Composable
private fun Encabezado(
    estado: OnboardingUiState,
    titulo: StringResource,
    descripcion: StringResource,
    esDatoCritico: Boolean = false,
) {
    EncabezadoPregunta(
        titulo = stringResource(titulo),
        descripcion = stringResource(descripcion),
        numeroPregunta = estado.numeroPregunta,
        totalPreguntas = estado.totalPreguntas,
        esDatoCritico = esDatoCritico,
    )
}

@Composable
private fun SeccionResumen(titulo: String, lineas: List<String>) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(verticalArrangement = Arrangement.spacedBy(espaciado.minimo)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            color = colores.textoSecundario,
            modifier = Modifier.semantics { heading() },
        )
        lineas.filter { it.isNotBlank() }.forEach { linea ->
            Text(text = linea, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BotonAgregar(etiqueta: String, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima),
    ) {
        Text(etiqueta)
    }
}

@Composable
private fun ErrorDePaso(mensaje: String?) {
    if (mensaje == null) return
    Text(
        text = mensaje,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/** Primer error del conjunto que corresponde a este campo, ya traducido. */
@Composable
private fun errorTexto(
    errores: List<ErrorCampoOnboarding>,
    vararg claves: ErrorCampoOnboarding,
): String? {
    val error = errores.firstOrNull { it in claves } ?: return null
    return stringResource(error.recurso())
}
