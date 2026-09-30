package com.eter.salud.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.adjuntos.rememberAbridorDeAdjuntos
import com.eter.salud.domain.adjuntos.rememberSelectorDeAdjuntos
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.BloqueDeExpediente
import com.eter.salud.domain.model.CategoriaDocumento
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.presentation.perfil.ExpedienteCompartidoViewModel
import com.eter.salud.ui.componentes.GaleriaDeDocumentos
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.TituloDeBloque
import com.eter.salud.ui.componentes.comoAdjunto
import com.eter.salud.ui.componentes.recurso
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_documentos_nombre
import salud.shared.generated.resources.a11y_tarjeta_interruptor
import salud.shared.generated.resources.a11y_tarjeta_oculto
import salud.shared.generated.resources.a11y_tarjeta_visible
import salud.shared.generated.resources.compartido_apoyo
import salud.shared.generated.resources.compartido_aviso_vital
import salud.shared.generated.resources.compartido_bloque_contactos
import salud.shared.generated.resources.compartido_bloque_emergencia
import salud.shared.generated.resources.compartido_bloque_historial
import salud.shared.generated.resources.compartido_bloque_identificaciones
import salud.shared.generated.resources.compartido_bloque_metricas
import salud.shared.generated.resources.compartido_bloque_tratamientos
import salud.shared.generated.resources.compartido_sin_datos
import salud.shared.generated.resources.compartido_titulo
import salud.shared.generated.resources.documentos_apoyo
import salud.shared.generated.resources.documentos_archivo
import salud.shared.generated.resources.documentos_cancelar
import salud.shared.generated.resources.documentos_clasificar_marcador
import salud.shared.generated.resources.documentos_clasificar_nombre
import salud.shared.generated.resources.documentos_clasificar_titulo
import salud.shared.generated.resources.documentos_eliminar
import salud.shared.generated.resources.documentos_escanear
import salud.shared.generated.resources.documentos_foto
import salud.shared.generated.resources.documentos_guardar
import salud.shared.generated.resources.documentos_oculto
import salud.shared.generated.resources.documentos_titulo
import salud.shared.generated.resources.documentos_vacio
import salud.shared.generated.resources.documentos_visible
import salud.shared.generated.resources.tarjeta_etiqueta_vital

/**
 * Lo que el paciente comparte con su medico, dentro de su historial: que
 * bloques del expediente ve, con el dato real de cada uno, y los estudios que
 * adjunto (radiografias escaneadas, laboratorios, fotos).
 */
@Composable
fun SeccionExpedienteCompartido(viewModel: ExpedienteCompartidoViewModel, idPaciente: String) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    // Carpeta propia del expediente: los estudios no se mezclan con los adjuntos del chat.
    val selector = rememberSelectorDeAdjuntos("expediente_$idPaciente")
    val abridor = rememberAbridorDeAdjuntos()

    Column(verticalArrangement = Arrangement.spacedBy(espaciado.amplio)) {
        // ------------------------------------------------ Bloques del expediente
        TituloDeBloque(stringResource(Res.string.compartido_titulo))
        TarjetaSalud {
            Text(
                text = stringResource(Res.string.compartido_apoyo),
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
                modifier = Modifier.padding(bottom = espaciado.compacto),
            )
            BloqueDeExpediente.entries.forEachIndexed { indice, bloque ->
                if (indice > 0) HorizontalDivider(color = colores.textoSecundario.copy(alpha = 0.15f))
                FilaDeBloque(
                    bloque = bloque,
                    resumen = resumenDe(bloque, estado.paciente),
                    visible = estado.compartido.muestra(bloque),
                    alCambiar = { viewModel.cambiarBloque(bloque, it) },
                )
            }
        }
        if (estado.compartido.vitalesOcultos.isNotEmpty()) {
            TarjetaSalud(color = colores.fondoAdvertencia) {
                Text(
                    text = stringResource(Res.string.compartido_aviso_vital),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoAdvertencia,
                )
            }
        }

        // ------------------------------------------------------- Documentos
        TituloDeBloque(stringResource(Res.string.documentos_titulo))
        TarjetaSalud {
            Text(
                text = stringResource(Res.string.documentos_apoyo),
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
                modifier = Modifier.padding(vertical = espaciado.compacto),
            ) {
                // El escaner primero: es el que endereza y recorta una radiografia en papel.
                BotonAdjuntar(Res.string.documentos_escanear, GlifoSalud.ESCANER, Modifier.weight(1f)) {
                    selector.escanearDocumento(viewModel::prepararDocumento)
                }
                BotonAdjuntar(Res.string.documentos_foto, GlifoSalud.FOTO, Modifier.weight(1f)) {
                    selector.elegirFoto(viewModel::prepararDocumento)
                }
                BotonAdjuntar(Res.string.documentos_archivo, GlifoSalud.ARCHIVO, Modifier.weight(1f)) {
                    selector.elegirArchivo(viewModel::prepararDocumento)
                }
            }
            if (estado.documentos.isEmpty()) {
                Text(
                    text = stringResource(Res.string.documentos_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
            } else {
                GaleriaDeDocumentos(
                    documentos = estado.documentos,
                    alAbrir = { abridor.abrir(it.comoAdjunto()) },
                    pieDe = { documento ->
                        PieDeDocumentoDelPaciente(
                            visible = documento.visibleParaMedico,
                            alCambiar = { viewModel.cambiarVisibilidad(documento.idDocumento, it) },
                            alEliminar = { viewModel.eliminarDocumento(documento.idDocumento) },
                        )
                    },
                )
            }
        }
    }

    estado.documentoPorClasificar?.let { adjunto ->
        DialogoClasificar(
            adjunto = adjunto,
            alGuardar = viewModel::guardarDocumento,
            alCancelar = viewModel::cancelarDocumento,
        )
    }
}

@Composable
private fun FilaDeBloque(bloque: BloqueDeExpediente, resumen: String, visible: Boolean, alCambiar: (Boolean) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val nombre = stringResource(bloque.recurso())
    val estadoLeido = stringResource(if (visible) Res.string.a11y_tarjeta_visible else Res.string.a11y_tarjeta_oculto)
    val descripcion = stringResource(Res.string.a11y_tarjeta_interruptor, nombre, estadoLeido)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .toggleable(value = visible, role = Role.Switch, onValueChange = alCambiar)
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .padding(vertical = espaciado.compacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = espaciado.medio)) {
            Text(text = nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = resumen, style = MaterialTheme.typography.bodySmall, color = colores.textoSecundario)
            if (bloque.esVital) {
                Text(
                    text = stringResource(Res.string.tarjeta_etiqueta_vital),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.acentoCritico,
                )
            }
        }
        Switch(checked = visible, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun BotonAdjuntar(etiqueta: StringResource, glifo: GlifoSalud, modifier: Modifier, alPulsar: () -> Unit) {
    OutlinedButton(onClick = alPulsar, modifier = modifier.heightIn(min = AreaTactilMinima)) {
        IconoSalud(glifo, lado = 18.dp)
        Text(
            text = stringResource(etiqueta),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,
        )
    }
}

/** A quien se comparte el documento, y quitarlo. */
@Composable
private fun PieDeDocumentoDelPaciente(visible: Boolean, alCambiar: (Boolean) -> Unit, alEliminar: () -> Unit) {
    val colores = LocalColoresSalud.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = visible, role = Role.Switch, onValueChange = alCambiar),
    ) {
        Text(
            text = stringResource(if (visible) Res.string.documentos_visible else Res.string.documentos_oculto),
            style = MaterialTheme.typography.labelSmall,
            color = if (visible) MaterialTheme.colorScheme.primary else colores.textoSecundario,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = visible, onCheckedChange = null)
    }
    TextButton(onClick = alEliminar, modifier = Modifier.heightIn(min = AreaTactilMinima)) {
        Text(
            text = stringResource(Res.string.documentos_eliminar),
            style = MaterialTheme.typography.labelMedium,
            color = colores.acentoCritico,
        )
    }
}

/**
 * Al adjuntar, se pregunta que es: "IMG_20260918.jpg" no le dice nada al
 * medico; "Radiografia de torax" si. La categoria llega sugerida segun como se
 * consiguio (el escaner sugiere radiografia).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DialogoClasificar(
    adjunto: Adjunto,
    alGuardar: (CategoriaDocumento, String) -> Unit,
    alCancelar: () -> Unit,
) {
    var categoria by remember(adjunto) { mutableStateOf(ExpedienteCompartidoViewModel.categoriaSugerida(adjunto)) }
    var titulo by remember(adjunto) { mutableStateOf("") }
    val espaciado = LocalEspaciadoSalud.current

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(Res.string.documentos_clasificar_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
                    CategoriaDocumento.entries.forEach { opcion ->
                        FilterChip(
                            selected = opcion == categoria,
                            onClick = { categoria = opcion },
                            label = { Text(stringResource(opcion.recurso())) },
                        )
                    }
                }
                CampoTextoRellenoSalud(
                    valor = titulo,
                    alCambiar = { titulo = it },
                    etiqueta = stringResource(Res.string.documentos_clasificar_nombre),
                    marcador = stringResource(Res.string.documentos_clasificar_marcador),
                    descripcionAccesible = stringResource(Res.string.a11y_documentos_nombre),
                )
            }
        },
        confirmButton = {
            val nombreCategoria = stringResource(categoria.recurso())
            TextButton(onClick = { alGuardar(categoria, titulo.ifBlank { nombreCategoria }) }) {
                Text(stringResource(Res.string.documentos_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text(stringResource(Res.string.documentos_cancelar)) }
        },
    )
}

private fun BloqueDeExpediente.recurso(): StringResource = when (this) {
    BloqueDeExpediente.EMERGENCIA -> Res.string.compartido_bloque_emergencia
    BloqueDeExpediente.TRATAMIENTOS -> Res.string.compartido_bloque_tratamientos
    BloqueDeExpediente.HISTORIAL_CLINICO -> Res.string.compartido_bloque_historial
    BloqueDeExpediente.METRICAS -> Res.string.compartido_bloque_metricas
    BloqueDeExpediente.IDENTIFICACIONES -> Res.string.compartido_bloque_identificaciones
    BloqueDeExpediente.CONTACTOS -> Res.string.compartido_bloque_contactos
}

/**
 * Lo que de verdad contiene cada bloque, en una linea: el paciente tiene que
 * ver QUE esta compartiendo, no solo el nombre de la seccion.
 */
@Composable
private fun resumenDe(bloque: BloqueDeExpediente, paciente: PacienteDto?): String {
    val sinDatos = stringResource(Res.string.compartido_sin_datos)
    if (paciente == null) return sinDatos
    val partes: List<String> = when (bloque) {
        BloqueDeExpediente.EMERGENCIA -> paciente.perfilEmergenciaReducido?.let { p ->
            p.alergias.map { it.alergeno } + p.condicionesCriticas + listOfNotNull(p.tipoSangre)
        }.orEmpty()
        BloqueDeExpediente.TRATAMIENTOS -> paciente.tratamientosActivos.map { it.medicamento }
        BloqueDeExpediente.HISTORIAL_CLINICO -> paciente.historialClinico?.let { h ->
            h.cirugias.map { it.procedimiento } + h.antecedentesHeredofamiliares
        }.orEmpty()
        BloqueDeExpediente.METRICAS -> paciente.metricasVitalesActuales?.let { m ->
            listOfNotNull(
                m.pesoKg?.let { "$it kg" },
                m.alturaCm?.let { "$it cm" },
                m.ultimaPresionArterial,
            )
        }.orEmpty()
        BloqueDeExpediente.IDENTIFICACIONES -> paciente.identificaciones?.let { i ->
            listOfNotNull(i.curp?.let(::enmascarar), i.nss?.let(::enmascarar), i.aseguradora)
        }.orEmpty()
        BloqueDeExpediente.CONTACTOS -> paciente.contactosEmergencia.map { "${it.nombre} (${it.relacion})" }
    }
    return partes.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { sinDatos }
}

/** "GOLJ800101HDFRRN09" -> "GOLJ••••••••••••09": se reconoce sin exponerlo en pantalla. */
private fun enmascarar(valor: String): String =
    if (valor.length <= 6) valor else valor.take(4) + "•".repeat(valor.length - 6) + valor.takeLast(2)
