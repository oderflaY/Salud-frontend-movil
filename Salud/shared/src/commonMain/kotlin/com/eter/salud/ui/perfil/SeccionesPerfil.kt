package com.eter.salud.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.eter.salud.presentation.perfil.ErrorCampoPerfil
import com.eter.salud.presentation.perfil.PerfilMedicoViewModel
import com.eter.salud.presentation.perfil.PerfilUiState
import com.eter.salud.presentation.perfil.SeccionPerfil
import com.eter.salud.ui.componentes.CampoTextoSalud
import com.eter.salud.ui.componentes.CasillaDeclaracion
import com.eter.salud.ui.componentes.EncabezadoPregunta
import com.eter.salud.ui.componentes.ListaCapturada
import com.eter.salud.ui.componentes.OpcionFechaRapida
import com.eter.salud.ui.componentes.SelectorFechaSalud
import com.eter.salud.ui.componentes.SelectorSalud
import com.eter.salud.ui.componentes.descripcionEliminar
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.PasoOnboardingEspaciado
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.*

/**
 * Contenido editable de cada seccion de la Fase 2.
 *
 * Los formularios de lista (cirugia, antecedente) usan estado local: es un
 * buffer de escritura efimero. El dato deja de ser local en cuanto se pulsa
 * Agregar, momento en que el ViewModel lo valida y lo incorpora al borrador,
 * unica fuente de verdad.
 */
@Composable
internal fun ContenidoSeccion(
    seccion: SeccionPerfil,
    estado: PerfilUiState,
    viewModel: PerfilMedicoViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PasoOnboardingEspaciado),
    ) {
        when (seccion) {
            SeccionPerfil.IDENTIFICACION_CURP -> SeccionCurp(estado, viewModel)
            SeccionPerfil.SEGURIDAD_SOCIAL -> SeccionSeguridadSocial(estado, viewModel)
            SeccionPerfil.DONACION_ORGANOS -> SeccionDonacionOrganos(estado, viewModel)
            SeccionPerfil.METRICAS_CORPORALES -> SeccionMetricas(estado, viewModel)
            SeccionPerfil.PRESION_ARTERIAL -> SeccionPresion(estado, viewModel)
            SeccionPerfil.CIRUGIAS -> SeccionCirugias(estado, viewModel)
            SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES -> SeccionAntecedentes(estado, viewModel)
        }
    }
}

// --------------------------------------------------------------- Seccion 1

@Composable
private fun SeccionCurp(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    Encabezado(Res.string.f2_curp_titulo, Res.string.f2_curp_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.curp,
        alCambiar = viewModel::actualizarCurp,
        etiqueta = stringResource(Res.string.f2_curp_campo),
        marcador = stringResource(Res.string.f2_curp_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_curp_campo),
        error = errorTexto(
            estado.erroresSeccion,
            ErrorCampoPerfil.CURP_VACIA,
            ErrorCampoPerfil.CURP_FORMATO,
        ),
    )
}

// --------------------------------------------------------------- Seccion 2

@Composable
private fun SeccionSeguridadSocial(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    Encabezado(Res.string.f2_nss_titulo, Res.string.f2_nss_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.nss,
        alCambiar = viewModel::actualizarNss,
        etiqueta = stringResource(Res.string.f2_nss_campo),
        marcador = stringResource(Res.string.f2_nss_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_nss_campo),
        tipoTeclado = KeyboardType.Number,
        error = errorTexto(
            estado.erroresSeccion,
            ErrorCampoPerfil.NSS_VACIO,
            ErrorCampoPerfil.NSS_FORMATO,
        ),
    )
    CampoTextoSalud(
        valor = estado.borrador.aseguradora,
        alCambiar = viewModel::actualizarAseguradora,
        etiqueta = stringResource(Res.string.f2_aseguradora_campo),
        marcador = stringResource(Res.string.f2_aseguradora_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_aseguradora_campo),
    )
}

// --------------------------------------------------------------- Seccion 3

@Composable
private fun SeccionDonacionOrganos(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    val opciones = stringArrayResource(Res.array.opciones_donador)
    // `firstOrNull` y no `first`: si alguien vaciara el array en una traduccion,
    // `first()` lanzaria y cerraria la app en mitad del cuestionario.
    val si = opciones.firstOrNull().orEmpty()
    val no = opciones.lastOrNull().orEmpty()
    val seleccion = when (estado.borrador.donadorOrganos) {
        true -> si
        false -> no
        null -> ""
    }
    Encabezado(
        titulo = Res.string.f2_donador_titulo,
        descripcion = Res.string.f2_donador_descripcion,
        esDatoCritico = true,
    )
    SelectorSalud(
        etiqueta = stringResource(Res.string.f2_donador_campo),
        seleccion = seleccion,
        opciones = opciones,
        alSeleccionar = { viewModel.marcarDonadorOrganos(it == si) },
        descripcionAccesible = stringResource(Res.string.a11y_f2_donador_campo),
        error = errorTexto(estado.erroresSeccion, ErrorCampoPerfil.DONADOR_SIN_RESPUESTA),
    )
}

// --------------------------------------------------------------- Seccion 4

@Composable
private fun SeccionMetricas(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    Encabezado(Res.string.f2_metricas_titulo, Res.string.f2_metricas_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.pesoKg,
        alCambiar = viewModel::actualizarPeso,
        etiqueta = stringResource(Res.string.f2_peso_campo),
        marcador = stringResource(Res.string.f2_peso_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_peso_campo),
        tipoTeclado = KeyboardType.Decimal,
        error = errorTexto(estado.erroresSeccion, ErrorCampoPerfil.PESO_INVALIDO),
    )
    CampoTextoSalud(
        valor = estado.borrador.alturaCm,
        alCambiar = viewModel::actualizarAltura,
        etiqueta = stringResource(Res.string.f2_altura_campo),
        marcador = stringResource(Res.string.f2_altura_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_altura_campo),
        tipoTeclado = KeyboardType.Number,
        error = errorTexto(estado.erroresSeccion, ErrorCampoPerfil.ALTURA_INVALIDA),
    )
    // El IMC no se captura: se deriva del peso y la altura y se muestra en vivo.
    estado.imcCalculado?.let { imc ->
        val texto = imc.toString()
        val anuncio = stringResource(Res.string.a11y_f2_imc_calculado, texto)
        Text(
            text = stringResource(Res.string.f2_imc_calculado, texto),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresSalud.current.textoSecundario,
            modifier = Modifier.semantics { contentDescription = anuncio },
        )
    }
}

// --------------------------------------------------------------- Seccion 5

@Composable
private fun SeccionPresion(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    Encabezado(Res.string.f2_presion_titulo, Res.string.f2_presion_descripcion)
    CampoTextoSalud(
        valor = estado.borrador.presionSistolica,
        alCambiar = viewModel::actualizarPresionSistolica,
        etiqueta = stringResource(Res.string.f2_presion_sistolica_campo),
        marcador = stringResource(Res.string.f2_presion_placeholder_sistolica),
        descripcionAccesible = stringResource(Res.string.a11y_f2_presion_sistolica),
        tipoTeclado = KeyboardType.Number,
        error = errorTexto(estado.erroresSeccion, ErrorCampoPerfil.PRESION_INVALIDA),
    )
    CampoTextoSalud(
        valor = estado.borrador.presionDiastolica,
        alCambiar = viewModel::actualizarPresionDiastolica,
        etiqueta = stringResource(Res.string.f2_presion_diastolica_campo),
        marcador = stringResource(Res.string.f2_presion_placeholder_diastolica),
        descripcionAccesible = stringResource(Res.string.a11y_f2_presion_diastolica),
        tipoTeclado = KeyboardType.Number,
    )
}

// --------------------------------------------------------------- Seccion 6

@Composable
private fun SeccionCirugias(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    var procedimiento by rememberSaveable { mutableStateOf("") }
    var fecha by rememberSaveable { mutableStateOf("") }
    var notas by rememberSaveable { mutableStateOf("") }

    Encabezado(Res.string.f2_cirugias_titulo, Res.string.f2_cirugias_descripcion)
    CampoTextoSalud(
        valor = procedimiento,
        alCambiar = { procedimiento = it },
        etiqueta = stringResource(Res.string.f2_cirugias_procedimiento_campo),
        marcador = stringResource(Res.string.f2_cirugias_procedimiento_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_cirugias_procedimiento),
        error = errorTexto(
            estado.erroresFormulario,
            ErrorCampoPerfil.CIRUGIA_PROCEDIMIENTO_VACIO,
        ),
    )
    Text(
        text = stringResource(Res.string.f2_cirugias_fecha_campo),
        style = MaterialTheme.typography.labelLarge,
        color = LocalColoresSalud.current.textoSecundario,
    )
    SelectorFechaSalud(
        valor = fecha,
        alCambiar = { fecha = it },
        fechaDeHoy = estado.fechaDeHoy,
        opcionesRapidas = listOf(
            OpcionFechaRapida.HACE_UNA_SEMANA,
            OpcionFechaRapida.HACE_UN_MES,
            OpcionFechaRapida.PERSONALIZADA,
        ),
        error = errorTexto(estado.erroresFormulario, ErrorCampoPerfil.CIRUGIA_FECHA_INVALIDA),
    )
    CampoTextoSalud(
        valor = notas,
        alCambiar = { notas = it },
        etiqueta = stringResource(Res.string.f2_cirugias_notas_campo),
        marcador = stringResource(Res.string.f2_cirugias_notas_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_cirugias_notas),
    )
    BotonAgregar(Res.string.f2_cirugias_accion_agregar) {
        val cirugiasPrevias = estado.borrador.cirugias.size
        viewModel.agregarCirugia(procedimiento, fecha, notas)
        if (viewModel.estado.value.borrador.cirugias.size > cirugiasPrevias) {
            procedimiento = ""
            fecha = ""
            notas = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.f2_cirugias_lista_titulo),
        elementos = estado.borrador.cirugias.map { "${it.procedimiento} - ${it.fecha}" },
        descripcionesAccesibles = estado.borrador.cirugias.map {
            stringResource(Res.string.a11y_f2_cirugias_item, it.procedimiento, it.fecha)
        },
        alEliminar = viewModel::eliminarCirugia,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinCirugias,
        alCambiar = viewModel::marcarSinCirugias,
        etiqueta = stringResource(Res.string.f2_cirugias_sin_cirugias),
    )
    MensajeDeError(errorTexto(estado.erroresSeccion, ErrorCampoPerfil.CIRUGIAS_SIN_CONFIRMAR))
}

// --------------------------------------------------------------- Seccion 7

@Composable
private fun SeccionAntecedentes(estado: PerfilUiState, viewModel: PerfilMedicoViewModel) {
    var antecedente by rememberSaveable { mutableStateOf("") }

    Encabezado(Res.string.f2_antecedentes_titulo, Res.string.f2_antecedentes_descripcion)
    CampoTextoSalud(
        valor = antecedente,
        alCambiar = { antecedente = it },
        etiqueta = stringResource(Res.string.f2_antecedentes_campo),
        marcador = stringResource(Res.string.f2_antecedentes_placeholder),
        descripcionAccesible = stringResource(Res.string.a11y_f2_antecedentes_campo),
        error = errorTexto(estado.erroresFormulario, ErrorCampoPerfil.ANTECEDENTE_VACIO),
    )
    BotonAgregar(Res.string.f2_antecedentes_accion_agregar) {
        val previos = estado.borrador.antecedentesHeredofamiliares.size
        viewModel.agregarAntecedente(antecedente)
        if (viewModel.estado.value.borrador.antecedentesHeredofamiliares.size > previos) {
            antecedente = ""
        }
    }
    ListaCapturada(
        titulo = stringResource(Res.string.f2_antecedentes_lista_titulo),
        elementos = estado.borrador.antecedentesHeredofamiliares,
        descripcionesAccesibles = estado.borrador.antecedentesHeredofamiliares.map {
            stringResource(Res.string.a11y_f2_antecedentes_item, it)
        },
        alEliminar = viewModel::eliminarAntecedente,
    )
    CasillaDeclaracion(
        marcada = estado.borrador.sinAntecedentes,
        alCambiar = viewModel::marcarSinAntecedentes,
        etiqueta = stringResource(Res.string.f2_antecedentes_sin_antecedentes),
    )
    MensajeDeError(
        errorTexto(estado.erroresSeccion, ErrorCampoPerfil.ANTECEDENTES_SIN_CONFIRMAR),
    )
}

// -------------------------------------------------------------- Utilidades

/**
 * Encabezado de seccion. No lleva numero de paso: la Fase 2 no es un flujo
 * lineal y numerarla haria creer al paciente que debe completarla de corrido.
 */
@Composable
private fun Encabezado(
    titulo: StringResource,
    descripcion: StringResource,
    esDatoCritico: Boolean = false,
) {
    EncabezadoPregunta(
        titulo = stringResource(titulo),
        descripcion = stringResource(descripcion),
        numeroPregunta = null,
        totalPreguntas = SeccionPerfil.TOTAL_SECCIONES,
        esDatoCritico = esDatoCritico,
    )
}

@Composable
private fun BotonAgregar(etiqueta: StringResource, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima),
    ) {
        Text(stringResource(etiqueta))
    }
}

/** Error de seccion que no cuelga de un campo concreto. */
@Composable
private fun MensajeDeError(texto: String?) {
    if (texto == null) return
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/** Primer error presente de los que afectan a un campo, ya traducido. */
@Composable
private fun errorTexto(
    errores: List<ErrorCampoPerfil>,
    vararg propios: ErrorCampoPerfil,
): String? = errores.firstOrNull { it in propios }?.let { stringResource(it.recurso()) }
