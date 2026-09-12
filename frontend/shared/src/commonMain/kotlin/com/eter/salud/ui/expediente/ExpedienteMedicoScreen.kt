package com.eter.salud.ui.expediente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.Cirugia
import com.eter.salud.domain.model.ContactoEmergencia
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.presentation.expediente.ExpedienteMedicoViewModel
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.TituloDeBloque
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.expediente_alergia_item
import salud.shared.generated.resources.expediente_campo_aseguradora
import salud.shared.generated.resources.expediente_campo_curp
import salud.shared.generated.resources.expediente_campo_donador_organos
import salud.shared.generated.resources.expediente_campo_fecha_nacimiento
import salud.shared.generated.resources.expediente_campo_genero
import salud.shared.generated.resources.expediente_campo_nss
import salud.shared.generated.resources.expediente_campo_telefono
import salud.shared.generated.resources.expediente_campo_tipo_sangre
import salud.shared.generated.resources.expediente_cirugia_item
import salud.shared.generated.resources.expediente_contacto_item
import salud.shared.generated.resources.expediente_estado_cargando
import salud.shared.generated.resources.expediente_estado_error
import salud.shared.generated.resources.expediente_seccion_contactos
import salud.shared.generated.resources.expediente_seccion_emergencia
import salud.shared.generated.resources.expediente_seccion_historial_clinico
import salud.shared.generated.resources.expediente_seccion_identidad
import salud.shared.generated.resources.expediente_seccion_tratamientos
import salud.shared.generated.resources.expediente_sin_alergias
import salud.shared.generated.resources.expediente_sin_antecedentes
import salud.shared.generated.resources.expediente_sin_cirugias
import salud.shared.generated.resources.expediente_sin_condiciones
import salud.shared.generated.resources.expediente_sin_contactos
import salud.shared.generated.resources.expediente_sin_medicacion_rescate
import salud.shared.generated.resources.expediente_sin_tratamientos
import salud.shared.generated.resources.expediente_subseccion_alergias
import salud.shared.generated.resources.expediente_subseccion_antecedentes
import salud.shared.generated.resources.expediente_subseccion_cirugias
import salud.shared.generated.resources.expediente_subseccion_condiciones
import salud.shared.generated.resources.expediente_subseccion_medicacion_rescate
import salud.shared.generated.resources.expediente_titulo
import salud.shared.generated.resources.expediente_tratamiento_dosis
import salud.shared.generated.resources.expediente_valor_no
import salud.shared.generated.resources.expediente_valor_si
import salud.shared.generated.resources.expediente_valor_sin_dato

/**
 * Expediente clinico de un paciente vinculado, en solo lectura.
 *
 * Es deliberadamente una pantalla de CONSULTA y no la edicion de
 * [com.eter.salud.ui.perfil.PerfilMedicoScreen]: un medico lee el expediente de
 * su paciente, no lo corrige desde aqui (la correccion sigue siendo del propio
 * paciente, en su Ajustes). Cada bloque que no tiene dato dice explicitamente
 * "sin X registrado" en vez de desaparecer: un bloque ausente se lee como "no
 * se pregunto", y uno que dice "sin alergias registradas" se lee como "se
 * registro que no tiene".
 */
@Composable
fun ExpedienteMedicoScreen(
    viewModel: ExpedienteMedicoViewModel,
    nombreReferencia: String,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val paciente = estado.paciente
    val nombreMostrado = paciente?.datosPersonales
        ?.let { "${it.nombre} ${it.apellidos}" }
        ?.trim()
        ?.ifBlank { null }
        ?: nombreReferencia.ifBlank { stringResource(Res.string.expediente_titulo) }

    Scaffold(modifier = modifier.fillMaxSize()) { relleno ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            contentPadding = PaddingValues(
                horizontal = espaciado.amplio,
                vertical = espaciado.medio,
            ),
            verticalArrangement = Arrangement.spacedBy(espaciado.medio),
        ) {
            item(key = "cabecera") {
                CabeceraGrande(
                    sobretitulo = stringResource(Res.string.expediente_titulo),
                    titulo = nombreMostrado,
                    accion = { BotonAtras(alPulsar = alVolver) },
                )
            }

            when {
                estado.errorCarga -> item(key = "error") {
                    BloqueDeError(
                        mensaje = stringResource(Res.string.expediente_estado_error),
                        alReintentar = viewModel::reintentar,
                    )
                }

                estado.cargando || paciente == null -> item(key = "cargando") {
                    Cargando()
                }

                else -> {
                    item(key = "identidad") { SeccionIdentidad(paciente) }
                    item(key = "emergencia") { SeccionEmergencia(paciente) }
                    item(key = "tratamientos") { SeccionTratamientos(paciente.tratamientosActivos) }
                    item(key = "historial") { SeccionHistorialClinico(paciente) }
                    item(key = "contactos") { SeccionContactos(paciente.contactosEmergencia) }
                }
            }
        }
    }
}

@Composable
private fun Cargando() {
    val colores = LocalColoresSalud.current
    val texto = stringResource(Res.string.expediente_estado_cargando)
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyLarge,
        color = colores.textoSecundario,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = texto
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun SeccionIdentidad(paciente: PacienteDto) {
    val sinDato = stringResource(Res.string.expediente_valor_sin_dato)
    Column(verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.compacto)) {
        TituloDeBloque(stringResource(Res.string.expediente_seccion_identidad))
        TarjetaSalud {
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_fecha_nacimiento),
                paciente.datosPersonales?.fechaNacimiento?.ifBlank { null } ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_genero),
                paciente.datosPersonales?.genero?.ifBlank { null } ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_telefono),
                paciente.datosPersonales?.telefono?.ifBlank { null } ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_curp),
                paciente.identificaciones?.curp ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_nss),
                paciente.identificaciones?.nss ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_aseguradora),
                paciente.identificaciones?.aseguradora ?: sinDato,
            )
        }
    }
}

@Composable
private fun SeccionEmergencia(paciente: PacienteDto) {
    val espaciado = LocalEspaciadoSalud.current
    val sinDato = stringResource(Res.string.expediente_valor_sin_dato)
    val perfil = paciente.perfilEmergenciaReducido

    Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
        TituloDeBloque(stringResource(Res.string.expediente_seccion_emergencia))
        TarjetaSalud {
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_tipo_sangre),
                perfil?.tipoSangre ?: sinDato,
            )
            FilaEtiquetaValor(
                stringResource(Res.string.expediente_campo_donador_organos),
                when (perfil?.donadorOrganos) {
                    true -> stringResource(Res.string.expediente_valor_si)
                    false -> stringResource(Res.string.expediente_valor_no)
                    null -> sinDato
                },
            )

            SubseccionLista(
                titulo = stringResource(Res.string.expediente_subseccion_alergias),
                elementos = perfil?.alergias.orEmpty(),
                textoVacio = stringResource(Res.string.expediente_sin_alergias),
            ) { alergia: Alergia ->
                stringResource(
                    Res.string.expediente_alergia_item,
                    alergia.alergeno,
                    alergia.severidad,
                    alergia.reaccion,
                )
            }

            SubseccionLista(
                titulo = stringResource(Res.string.expediente_subseccion_condiciones),
                elementos = perfil?.condicionesCriticas.orEmpty(),
                textoVacio = stringResource(Res.string.expediente_sin_condiciones),
            ) { condicion: String -> condicion }

            SubseccionLista(
                titulo = stringResource(Res.string.expediente_subseccion_medicacion_rescate),
                elementos = perfil?.medicacionRescate.orEmpty(),
                textoVacio = stringResource(Res.string.expediente_sin_medicacion_rescate),
            ) { medicamento: String -> medicamento }
        }
    }
}

@Composable
private fun SeccionTratamientos(tratamientos: List<TratamientoActivo>) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.compacto)) {
        TituloDeBloque(stringResource(Res.string.expediente_seccion_tratamientos))
        TarjetaSalud {
            if (tratamientos.isEmpty()) {
                TextoVacio(stringResource(Res.string.expediente_sin_tratamientos))
            } else {
                tratamientos.forEach { tratamiento ->
                    FilaEtiquetaValor(
                        tratamiento.medicamento,
                        stringResource(
                            Res.string.expediente_tratamiento_dosis,
                            tratamiento.dosis,
                            tratamiento.frecuenciaHoras,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionHistorialClinico(paciente: PacienteDto) {
    val historial = paciente.historialClinico
    Column(verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.compacto)) {
        TituloDeBloque(stringResource(Res.string.expediente_seccion_historial_clinico))
        TarjetaSalud {
            SubseccionLista(
                titulo = stringResource(Res.string.expediente_subseccion_cirugias),
                elementos = historial?.cirugias.orEmpty(),
                textoVacio = stringResource(Res.string.expediente_sin_cirugias),
            ) { cirugia: Cirugia ->
                stringResource(Res.string.expediente_cirugia_item, cirugia.procedimiento, cirugia.fecha)
            }

            SubseccionLista(
                titulo = stringResource(Res.string.expediente_subseccion_antecedentes),
                elementos = historial?.antecedentesHeredofamiliares.orEmpty(),
                textoVacio = stringResource(Res.string.expediente_sin_antecedentes),
            ) { antecedente: String -> antecedente }
        }
    }
}

@Composable
private fun SeccionContactos(contactos: List<ContactoEmergencia>) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalEspaciadoSalud.current.compacto)) {
        TituloDeBloque(stringResource(Res.string.expediente_seccion_contactos))
        TarjetaSalud {
            if (contactos.isEmpty()) {
                TextoVacio(stringResource(Res.string.expediente_sin_contactos))
            } else {
                contactos.sortedBy { it.prioridad }.forEach { contacto ->
                    Text(
                        text = stringResource(
                            Res.string.expediente_contacto_item,
                            contacto.nombre,
                            contacto.relacion,
                            contacto.telefono,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** Etiqueta secundaria y valor principal, la misma pareja que usa el detalle de una cita. */
@Composable
private fun FilaEtiquetaValor(etiqueta: String, valor: String) {
    val colores = LocalColoresSalud.current
    Column {
        Text(text = etiqueta, style = MaterialTheme.typography.labelMedium, color = colores.textoSecundario)
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TextoVacio(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalColoresSalud.current.textoSecundario,
    )
}

/**
 * Una lista con titulo dentro de una tarjeta, o el aviso de que esta vacia.
 *
 * Comparten esta forma las alergias, las condiciones criticas, la medicacion
 * de rescate, las cirugias y los antecedentes: todas son "un titulo y cero o
 * mas lineas de texto", y la unica diferencia entre ellas es como se arma cada
 * linea a partir de su elemento.
 */
@Composable
private fun <T> SubseccionLista(
    titulo: String,
    elementos: List<T>,
    textoVacio: String,
    texto: @Composable (T) -> String,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(verticalArrangement = Arrangement.spacedBy(espaciado.minimo)) {
        Text(text = titulo, style = MaterialTheme.typography.labelMedium, color = colores.textoSecundario)
        if (elementos.isEmpty()) {
            TextoVacio(textoVacio)
        } else {
            elementos.forEach { elemento ->
                Text(text = texto(elemento), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
