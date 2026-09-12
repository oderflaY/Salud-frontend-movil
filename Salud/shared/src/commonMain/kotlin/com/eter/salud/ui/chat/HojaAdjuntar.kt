package com.eter.salud.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.adjuntos.SelectorDeAdjuntos
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.margenInferiorSeguro
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_chat_adjuntar_archivo
import salud.shared.generated.resources.a11y_chat_adjuntar_escanear
import salud.shared.generated.resources.a11y_chat_adjuntar_foto
import salud.shared.generated.resources.chat_adjuntar_archivo
import salud.shared.generated.resources.chat_adjuntar_escanear
import salud.shared.generated.resources.chat_adjuntar_foto
import salud.shared.generated.resources.chat_adjuntar_titulo

/**
 * Hoja de las tres formas de adjuntar: foto de galeria, archivo del sistema, o
 * escanear un documento con la camara.
 *
 * Cada fila cierra la hoja ANTES de abrir el selector de sistema, no despues:
 * el selector (la galeria, el explorador de archivos, la camara del escaner)
 * ya es su propia pantalla modal, y dejar la hoja de opciones detras se veria
 * como dos capas de modal apiladas sin motivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaAdjuntar(
    selector: SelectorDeAdjuntos,
    alAdjuntar: (Adjunto) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = estadoHoja,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = espaciado.amplio)
                .margenInferiorSeguro(),
            verticalArrangement = Arrangement.spacedBy(espaciado.minimo),
        ) {
            Text(
                text = stringResource(Res.string.chat_adjuntar_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = espaciado.compacto)
                    .semantics { heading() },
            )
            FilaOpcionAdjuntar(
                glifo = GlifoSalud.FOTO,
                etiqueta = stringResource(Res.string.chat_adjuntar_foto),
                descripcionAccesible = stringResource(Res.string.a11y_chat_adjuntar_foto),
                alPulsar = {
                    onDismissRequest()
                    selector.elegirFoto(alAdjuntar)
                },
            )
            FilaOpcionAdjuntar(
                glifo = GlifoSalud.ARCHIVO,
                etiqueta = stringResource(Res.string.chat_adjuntar_archivo),
                descripcionAccesible = stringResource(Res.string.a11y_chat_adjuntar_archivo),
                alPulsar = {
                    onDismissRequest()
                    selector.elegirArchivo(alAdjuntar)
                },
            )
            FilaOpcionAdjuntar(
                glifo = GlifoSalud.ESCANER,
                etiqueta = stringResource(Res.string.chat_adjuntar_escanear),
                descripcionAccesible = stringResource(Res.string.a11y_chat_adjuntar_escanear),
                alPulsar = {
                    onDismissRequest()
                    selector.escanearDocumento(alAdjuntar)
                },
            )
        }
    }
}

@Composable
private fun FilaOpcionAdjuntar(
    glifo: GlifoSalud,
    etiqueta: String,
    descripcionAccesible: String,
    alPulsar: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(onClick = alPulsar)
            .semantics(mergeDescendants = true) { contentDescription = descripcionAccesible },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MedidaSalud.disco)
                .background(colores.acentoSuave, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(glifo = glifo, lado = LADO_ICONO_OPCION, color = colores.acentoAccion)
        }
        Spacer(Modifier.width(espaciado.medio))
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val LADO_ICONO_OPCION = 22.dp
