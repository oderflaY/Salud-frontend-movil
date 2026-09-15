package com.eter.salud.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.adjuntos.AbridorDeAdjuntos
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_chat_accion_adjuntar
import salud.shared.generated.resources.a11y_chat_adjunto_abrir
import salud.shared.generated.resources.a11y_chat_adjunto_quitar
import salud.shared.generated.resources.chat_adjunto_accion_quitar
import salud.shared.generated.resources.chat_adjunto_no_se_pudo_abrir
import salud.shared.generated.resources.chat_adjunto_tipo_archivo
import salud.shared.generated.resources.chat_adjunto_tipo_escaneo
import salud.shared.generated.resources.chat_adjunto_tipo_foto

// Piezas de adjuntos que comparten el chat del paciente (ChatScreen) y el del
// medico (ChatMedicoScreen): los dos lados mandan y reciben lo mismo.

/** Nombre del tipo de adjunto, tal como se muestra en la burbuja. */
@Composable
internal fun tituloDe(tipo: TipoAdjunto): String = when (tipo) {
    TipoAdjunto.FOTO -> stringResource(Res.string.chat_adjunto_tipo_foto)
    TipoAdjunto.ARCHIVO -> stringResource(Res.string.chat_adjunto_tipo_archivo)
    TipoAdjunto.ESCANEO -> stringResource(Res.string.chat_adjunto_tipo_escaneo)
}

internal fun glifoDe(tipo: TipoAdjunto): GlifoSalud = when (tipo) {
    TipoAdjunto.FOTO -> GlifoSalud.FOTO
    TipoAdjunto.ARCHIVO -> GlifoSalud.ARCHIVO
    TipoAdjunto.ESCANEO -> GlifoSalud.ESCANER
}

/**
 * El adjunto dentro de la burbuja: icono por tipo, nombre del archivo y su
 * categoria. Es deliberadamente un rotulo y no una miniatura: leer una foto de
 * un archivo local en las tres plataformas exige decodificarla a mano (no hay
 * libreria de imagenes en el proyecto).
 *
 * Con [abridor], tocarlo abre el archivo en el visor del sistema. Si no se
 * puede -- aun bajando, o sin app para ese tipo -- el rotulo lo dice en vez de
 * ignorar el toque.
 */
@Composable
internal fun ChipDeAdjunto(adjunto: Adjunto, colorTexto: Color, abridor: AbridorDeAdjuntos? = null) {
    val espaciado = LocalEspaciadoSalud.current
    var falloAlAbrir by remember(adjunto.idAdjunto) { mutableStateOf(false) }
    val etiquetaAbrir = stringResource(Res.string.a11y_chat_adjunto_abrir, adjunto.nombre)
    val pulsable = if (abridor != null) {
        Modifier
            .heightIn(min = AreaTactilMinima)
            .clickable(onClickLabel = etiquetaAbrir) { falloAlAbrir = !abridor.abrir(adjunto) }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .background(colorTexto.copy(alpha = ALFA_FONDO_CHIP), FormaSalud.sutil)
            .then(pulsable)
            .padding(horizontal = espaciado.compacto, vertical = espaciado.minimo),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        IconoSalud(glifo = glifoDe(adjunto.tipo), lado = LADO_ICONO_ADJUNTO, color = colorTexto)
        Column {
            Text(text = adjunto.nombre, style = MaterialTheme.typography.labelLarge, color = colorTexto, maxLines = 1)
            Text(
                text = if (falloAlAbrir) stringResource(Res.string.chat_adjunto_no_se_pudo_abrir) else tituloDe(adjunto.tipo),
                style = MaterialTheme.typography.labelSmall,
                color = colorTexto.copy(alpha = ALFA_SUBTITULO_CHIP),
            )
        }
    }
}

@Composable
internal fun BotonAdjuntar(alPulsar: () -> Unit) {
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_chat_accion_adjuntar)
    Surface(
        modifier = Modifier
            .size(AreaTactilMinima)
            .clickable(onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        color = colores.fondoCampo,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            IconoSalud(glifo = GlifoSalud.ADJUNTAR, lado = 20.dp, color = colores.textoSecundario)
        }
    }
}

/**
 * El adjunto ya elegido, a la espera de que se pulse enviar. Vive pegado al
 * campo de texto y no dentro de una burbuja: todavia no es un mensaje, es lo
 * que el mensaje va a llevar.
 */
@Composable
internal fun VistaPreviaAdjunto(adjunto: Adjunto, alQuitar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcionQuitar = stringResource(Res.string.a11y_chat_adjunto_quitar, adjunto.nombre)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colores.fondoCampo, FormaSalud.sutil)
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        IconoSalud(glifo = glifoDe(adjunto.tipo), lado = LADO_ICONO_ADJUNTO, color = colores.acentoAccion)
        Text(
            text = adjunto.nombre,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.chat_adjunto_accion_quitar),
            style = MaterialTheme.typography.labelLarge,
            color = colores.acentoAccion,
            modifier = Modifier
                .heightIn(min = AreaTactilMinima)
                .clickable(onClick = alQuitar)
                .semantics { contentDescription = descripcionQuitar }
                .padding(horizontal = espaciado.compacto),
        )
    }
}

private const val ALFA_FONDO_CHIP = 0.12f
private const val ALFA_SUBTITULO_CHIP = 0.7f
private val LADO_ICONO_ADJUNTO = 22.dp
