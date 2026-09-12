package com.eter.salud.ui.diario

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.diario.DiarioViewModel
import com.eter.salud.ui.agenda.fechaLarga
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.BotonAtras
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.CampoTextoRellenoSalud
import com.eter.salud.ui.componentes.TarjetaSalud
import com.eter.salud.ui.componentes.TituloDeBloque
import com.eter.salud.ui.componentes.bordeDeTarjeta
import com.eter.salud.ui.componentes.elevacionDeTarjeta
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_diario_accion_eliminar
import salud.shared.generated.resources.a11y_diario_accion_guardar
import salud.shared.generated.resources.a11y_diario_campo
import salud.shared.generated.resources.a11y_diario_entrada
import salud.shared.generated.resources.diario_accion_eliminar
import salud.shared.generated.resources.diario_accion_guardar
import salud.shared.generated.resources.diario_aviso_no_es_diagnostico
import salud.shared.generated.resources.diario_campo
import salud.shared.generated.resources.diario_descripcion
import salud.shared.generated.resources.diario_error_guardado
import salud.shared.generated.resources.diario_historial_titulo
import salud.shared.generated.resources.diario_marcador
import salud.shared.generated.resources.diario_titulo
import salud.shared.generated.resources.diario_vacio

/**
 * Diario de sintomas del paciente.
 *
 * ## El semaforo se ve MIENTRAS se escribe
 *
 * La senal de severidad no espera a guardar: cambia bajo el dedo segun lo que el
 * paciente teclea. Es deliberado. Ver que "opresion en el pecho" va a llegarle a
 * su medica marcado como urgente le permite matizarlo, corregirlo o darse cuenta
 * de que efectivamente deberia llamar. Calcularlo solo al guardar convertiria el
 * semaforo en una sorpresa que el paciente nunca ve.
 *
 * ## Y siempre dice que NO es un diagnostico
 *
 * El aviso acompana al semaforo, no esta escondido en una ayuda. Un color rojo
 * junto a un texto clinico se lee como un dictamen si nadie dice lo contrario, y
 * esto solo ordena la bandeja del medico.
 */
@Composable
fun DiarioScreen(
    viewModel: DiarioViewModel,
    modifier: Modifier = Modifier,
    alVolver: () -> Unit = {},
    reloj: RelojSalud = relojDelSistema(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

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
                titulo = stringResource(Res.string.diario_titulo),
                apoyo = stringResource(Res.string.diario_descripcion),
                accion = { BotonAtras(alPulsar = alVolver) },
            )
        }

        item(key = "captura") {
            TarjetaSalud {
                CampoTextoRellenoSalud(
                    valor = estado.borrador,
                    alCambiar = viewModel::actualizarBorrador,
                    etiqueta = stringResource(Res.string.diario_campo),
                    marcador = stringResource(Res.string.diario_marcador),
                    descripcionAccesible = stringResource(Res.string.a11y_diario_campo),
                    tipoTeclado = KeyboardType.Text,
                )
                if (estado.borrador.isNotBlank()) {
                    Spacer(Modifier.height(espaciado.compacto))
                    SenalDeSeveridad(estado.severidadDelBorrador)
                    Text(
                        text = stringResource(Res.string.diario_aviso_no_es_diagnostico),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoSecundario,
                    )
                }
                Spacer(Modifier.height(espaciado.compacto))
                BotonAccionPrincipal(
                    etiqueta = stringResource(Res.string.diario_accion_guardar),
                    alPulsar = viewModel::guardar,
                    descripcionAccesible = stringResource(Res.string.a11y_diario_accion_guardar),
                    habilitado = estado.puedeGuardar,
                )
                if (estado.errorGuardado) {
                    Text(
                        text = stringResource(Res.string.diario_error_guardado),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        }

        item(key = "titulo_historial") {
            TituloDeBloque(stringResource(Res.string.diario_historial_titulo))
        }

        if (estado.entradas.isEmpty() && !estado.cargando) {
            item(key = "vacio") {
                Text(
                    text = stringResource(Res.string.diario_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoSecundario,
                )
            }
        }

        items(estado.entradas, key = { it.idEntrada }) { entrada ->
            FilaDeEntrada(
                entrada = entrada,
                fechaLegible = fechaLarga(entrada.fecha),
                horaLocal = reloj.horaLocal(entrada.instante),
                alEliminar = { viewModel.eliminar(entrada.idEntrada) },
            )
        }
    }
}

/**
 * Senal de severidad: punto de color y la palabra que lo explica.
 *
 * Nunca solo color. En una app clinica el semaforo tiene que leerse igual sin
 * distinguir el rojo del verde, y aqui ademas se juega si el paciente entiende
 * que lo que acaba de escribir llegara marcado como urgente.
 */
@Composable
private fun SenalDeSeveridad(severidad: SeveridadDiario) {
    val espaciado = LocalEspaciadoSalud.current
    val etiqueta = stringResource(severidad.recurso())
    val tinta by animateColorAsState(
        targetValue = severidad.tinta(),
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "tintaSeveridad",
    )
    val fondo by animateColorAsState(
        targetValue = severidad.fondo(),
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "fondoSeveridad",
    )

    Surface(
        color = fondo,
        shape = FormaSalud.pastilla,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = etiqueta },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = espaciado.medio,
                vertical = espaciado.compacto,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(MedidaSalud.punto).background(tinta, CircleShape))
            Spacer(Modifier.width(espaciado.compacto))
            Text(text = etiqueta, style = MaterialTheme.typography.labelMedium, color = tinta)
        }
    }
}

@Composable
private fun FilaDeEntrada(
    entrada: EntradaDiario,
    fechaLegible: String,
    horaLocal: String,
    alEliminar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val etiquetaSeveridad = stringResource(entrada.severidad.recurso())
    val descripcion = stringResource(
        Res.string.a11y_diario_entrada,
        fechaLegible,
        etiquetaSeveridad,
        entrada.texto,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        color = colores.fondoTarjeta,
        shape = FormaSalud.grande,
        border = bordeDeTarjeta(),
        shadowElevation = elevacionDeTarjeta(),
    ) {
        Column(Modifier.padding(espaciado.amplio)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(MedidaSalud.punto)
                        .background(entrada.severidad.tinta(), CircleShape),
                )
                Spacer(Modifier.width(espaciado.compacto))
                Text(
                    text = fechaLegible,
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoSecundario,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = horaLocal,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoSecundario,
                )
            }
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = entrada.texto,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(espaciado.compacto))
            val descripcionEliminar = stringResource(
                Res.string.a11y_diario_accion_eliminar,
                fechaLegible,
            )
            TextButton(
                onClick = alEliminar,
                modifier = Modifier
                    .heightIn(min = AreaTactilMinima)
                    .semantics { contentDescription = descripcionEliminar },
            ) {
                Text(
                    text = stringResource(Res.string.diario_accion_eliminar),
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoSecundario,
                )
            }
        }
    }
}

