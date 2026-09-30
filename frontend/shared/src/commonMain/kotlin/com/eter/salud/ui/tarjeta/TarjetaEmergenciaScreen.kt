package com.eter.salud.ui.tarjeta

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.DatoDeTarjeta
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.presentation.tarjeta.TarjetaEmergenciaUiState
import com.eter.salud.presentation.tarjeta.TarjetaEmergenciaViewModel
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.TituloDeBloque
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_tarjeta_interruptor
import salud.shared.generated.resources.a11y_tarjeta_oculto
import salud.shared.generated.resources.a11y_tarjeta_visible
import salud.shared.generated.resources.tarjeta_aviso_vital
import salud.shared.generated.resources.tarjeta_bloque_compartes
import salud.shared.generated.resources.tarjeta_dato_alergias
import salud.shared.generated.resources.tarjeta_dato_condiciones
import salud.shared.generated.resources.tarjeta_dato_donacion
import salud.shared.generated.resources.tarjeta_dato_medicacion
import salud.shared.generated.resources.tarjeta_dato_tipo_sangre
import salud.shared.generated.resources.tarjeta_descripcion
import salud.shared.generated.resources.tarjeta_donador_no
import salud.shared.generated.resources.tarjeta_donador_si
import salud.shared.generated.resources.tarjeta_error_carga
import salud.shared.generated.resources.tarjeta_etiqueta_vital
import salud.shared.generated.resources.tarjeta_numero
import salud.shared.generated.resources.tarjeta_oculto
import salud.shared.generated.resources.tarjeta_siempre_visible
import salud.shared.generated.resources.tarjeta_sin_registrar
import salud.shared.generated.resources.tarjeta_sin_tarjeta
import salud.shared.generated.resources.tarjeta_titulo
import salud.shared.generated.resources.tarjeta_vista_paramedico

/**
 * "Mi tarjeta de emergencia": el paciente decide que ve quien la escanea.
 *
 * Dos mitades, a proposito: arriba lo que el paciente controla (cada dato con
 * su valor real, para que sepa QUE esta compartiendo, no solo una etiqueta), y
 * abajo el resultado tal como lo vera un paramedico. Ver la consecuencia de
 * cada interruptor en el acto es lo que evita ocultar algo sin entenderlo.
 */
@Composable
fun TarjetaEmergenciaScreen(
    viewModel: TarjetaEmergenciaViewModel,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

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
        verticalArrangement = Arrangement.spacedBy(espaciado.amplio),
    ) {
        item(key = "cabecera") {
            CabeceraGrande(
                titulo = stringResource(Res.string.tarjeta_titulo),
                apoyo = stringResource(Res.string.tarjeta_descripcion),
                accion = { BotonAtras(alPulsar = alVolver) },
            )
        }

        when {
            estado.cargando -> item(key = "cargando") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            estado.errorCarga -> item(key = "error") {
                BloqueDeError(
                    mensaje = stringResource(Res.string.tarjeta_error_carga),
                    alReintentar = viewModel::cargar,
                )
            }
            estado.sinTarjeta -> item(key = "sin_tarjeta") {
                TarjetaSalud {
                    Text(
                        text = stringResource(Res.string.tarjeta_sin_tarjeta),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                item(key = "controles") { Controles(estado, viewModel::cambiar) }
                if (estado.visibilidad.vitalesOcultos.isNotEmpty()) {
                    item(key = "aviso") { AvisoVital() }
                }
                item(key = "titulo_vista") { TituloDeBloque(stringResource(Res.string.tarjeta_vista_paramedico)) }
                item(key = "vista") { VistaDelParamedico(estado) }
            }
        }
    }
}

@Composable
private fun Controles(estado: TarjetaEmergenciaUiState, alCambiar: (DatoDeTarjeta, Boolean) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    TarjetaSalud {
        Text(
            text = stringResource(Res.string.tarjeta_bloque_compartes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.tarjeta_siempre_visible),
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoSecundario,
            modifier = Modifier.padding(top = espaciado.minimo, bottom = espaciado.compacto),
        )
        DatoDeTarjeta.entries.forEachIndexed { indice, dato ->
            if (indice > 0) HorizontalDivider(color = colores.textoSecundario.copy(alpha = 0.15f))
            FilaDeDato(
                dato = dato,
                valor = valorDe(dato, estado.perfil),
                visible = estado.visibilidad.muestra(dato),
                alCambiar = { alCambiar(dato, it) },
            )
        }
    }
}

/**
 * Un dato de la tarjeta con su interruptor. Toda la fila es el interruptor
 * (area de toque grande) y el lector de pantalla la anuncia como uno solo.
 */
@Composable
private fun FilaDeDato(dato: DatoDeTarjeta, valor: String, visible: Boolean, alCambiar: (Boolean) -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val nombre = stringResource(etiquetaDe(dato))
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
            Text(
                text = valor,
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoSecundario,
            )
            if (dato.esVital) {
                Text(
                    text = stringResource(Res.string.tarjeta_etiqueta_vital),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.acentoCritico,
                )
            }
        }
        // El Switch no recibe toques propios: los maneja la fila entera.
        Switch(checked = visible, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun AvisoVital() {
    val colores = LocalColoresSalud.current
    TarjetaSalud(color = colores.fondoAdvertencia) {
        Text(
            text = stringResource(Res.string.tarjeta_aviso_vital),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoAdvertencia,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** La tarjeta tal como aparece en el escaner, con lo oculto ya retirado. */
@Composable
private fun VistaDelParamedico(estado: TarjetaEmergenciaUiState) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val vista = estado.vistaDelParamedico
    TarjetaSalud {
        Text(
            text = estado.nombreCompleto,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = estado.fechaNacimiento,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        Text(
            text = stringResource(Res.string.tarjeta_numero, estado.idTarjeta),
            style = MaterialTheme.typography.labelSmall,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.compacto))
        DatoDeTarjeta.entries.forEach { dato ->
            val visible = estado.visibilidad.muestra(dato)
            Column(Modifier.fillMaxWidth().padding(vertical = espaciado.minimo)) {
                Text(
                    text = stringResource(etiquetaDe(dato)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoSecundario,
                )
                Text(
                    text = if (visible) valorDe(dato, vista) else stringResource(Res.string.tarjeta_oculto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (visible) MaterialTheme.colorScheme.onSurface else colores.textoSecundario,
                    fontWeight = if (visible) FontWeight.Normal else FontWeight.Light,
                )
            }
        }
    }
}

private fun etiquetaDe(dato: DatoDeTarjeta): StringResource = when (dato) {
    DatoDeTarjeta.ALERGIAS -> Res.string.tarjeta_dato_alergias
    DatoDeTarjeta.MEDICACION_RESCATE -> Res.string.tarjeta_dato_medicacion
    DatoDeTarjeta.CONDICIONES -> Res.string.tarjeta_dato_condiciones
    DatoDeTarjeta.TIPO_SANGRE -> Res.string.tarjeta_dato_tipo_sangre
    DatoDeTarjeta.DONACION_ORGANOS -> Res.string.tarjeta_dato_donacion
}

/** El valor real del dato, en una linea; "Sin registrar" si el expediente no lo tiene. */
@Composable
private fun valorDe(dato: DatoDeTarjeta, perfil: PerfilEmergenciaReducido): String {
    val sinRegistrar = stringResource(Res.string.tarjeta_sin_registrar)
    return when (dato) {
        DatoDeTarjeta.ALERGIAS -> perfil.alergias.joinToString(", ") { it.alergeno }.ifBlank { sinRegistrar }
        DatoDeTarjeta.MEDICACION_RESCATE -> perfil.medicacionRescate.joinToString(", ").ifBlank { sinRegistrar }
        DatoDeTarjeta.CONDICIONES -> perfil.condicionesCriticas.joinToString(", ").ifBlank { sinRegistrar }
        DatoDeTarjeta.TIPO_SANGRE -> perfil.tipoSangre?.ifBlank { null } ?: sinRegistrar
        DatoDeTarjeta.DONACION_ORGANOS -> when (perfil.donadorOrganos) {
            true -> stringResource(Res.string.tarjeta_donador_si)
            false -> stringResource(Res.string.tarjeta_donador_no)
            null -> sinRegistrar
        }
    }
}
