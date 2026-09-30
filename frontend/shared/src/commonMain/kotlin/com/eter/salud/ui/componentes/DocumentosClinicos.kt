package com.eter.salud.ui.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eter.salud.data.expediente.decodificarMiniatura
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.CategoriaDocumento
import com.eter.salud.domain.model.DocumentoClinico
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_documento
import salud.shared.generated.resources.documentos_categoria_foto
import salud.shared.generated.resources.documentos_categoria_laboratorio
import salud.shared.generated.resources.documentos_categoria_otro
import salud.shared.generated.resources.documentos_categoria_radiografia
import salud.shared.generated.resources.documentos_categoria_receta

/** Nombre legible de la categoria, igual para el paciente y para el medico. */
fun CategoriaDocumento.recurso(): StringResource = when (this) {
    CategoriaDocumento.RADIOGRAFIA -> Res.string.documentos_categoria_radiografia
    CategoriaDocumento.LABORATORIO -> Res.string.documentos_categoria_laboratorio
    CategoriaDocumento.RECETA -> Res.string.documentos_categoria_receta
    CategoriaDocumento.FOTO -> Res.string.documentos_categoria_foto
    CategoriaDocumento.OTRO -> Res.string.documentos_categoria_otro
}

/** El documento como [Adjunto], para abrirlo con el visor del sistema. */
fun DocumentoClinico.comoAdjunto(): Adjunto = Adjunto(
    idAdjunto = idDocumento,
    tipo = if (categoria == CategoriaDocumento.FOTO) TipoAdjunto.FOTO else TipoAdjunto.ARCHIVO,
    nombre = nombreArchivo,
    rutaLocal = rutaLocal,
    tipoMime = tipoMime,
)

/**
 * Miniatura del estudio: la imagen misma si es una foto o una radiografia
 * escaneada; un icono de archivo si es un PDF.
 *
 * Se decodifica reducida y fuera del hilo principal (ver
 * [decodificarMiniatura]): una radiografia a tamano completo no cabe en
 * memoria varias veces seguidas.
 */
@Composable
fun MiniaturaDeDocumento(documento: DocumentoClinico, modifier: Modifier = Modifier) {
    val colores = LocalColoresSalud.current
    val imagen by produceState<ImageBitmap?>(null, documento.rutaLocal) {
        value = if (documento.esImagen) decodificarMiniatura(documento.rutaLocal, LADO_MINIATURA) else null
    }
    Box(
        modifier = modifier
            .clip(FormaSalud.media)
            .background(colores.fondoCampo),
        contentAlignment = Alignment.Center,
    ) {
        val cargada = imagen
        if (cargada != null) {
            Image(
                bitmap = cargada,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IconoSalud(GlifoSalud.ARCHIVO, lado = 36.dp, color = colores.textoSecundario)
        }
    }
}

/**
 * Un estudio en la galeria: miniatura, nombre, categoria y fecha. Tocarlo lo
 * abre en el visor del sistema. [pie] recibe los controles de quien lo muestra
 * (el paciente pone ahi a quien lo comparte; el medico, nada).
 */
@Composable
fun TarjetaDeDocumento(
    documento: DocumentoClinico,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    pie: @Composable () -> Unit = {},
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val categoria = stringResource(documento.categoria.recurso())
    val descripcion = stringResource(Res.string.a11y_documento, documento.titulo, categoria, documento.fecha)

    Surface(
        onClick = alAbrir,
        shape = FormaSalud.media,
        color = colores.fondoTarjeta,
        tonalElevation = 1.dp,
        modifier = modifier.semantics { contentDescription = descripcion },
    ) {
        Column {
            MiniaturaDeDocumento(
                documento = documento,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(Modifier.padding(espaciado.compacto)) {
                Text(
                    text = documento.titulo,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$categoria · ${documento.fecha}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoSecundario,
                    maxLines = 1,
                )
                pie()
            }
        }
    }
}

/**
 * Los documentos en dos columnas. No es una cuadricula perezosa porque vive
 * dentro de pantallas que ya se desplazan: una lista perezosa anidada no sabe
 * cuanto medir.
 */
@Composable
fun GaleriaDeDocumentos(
    documentos: List<DocumentoClinico>,
    alAbrir: (DocumentoClinico) -> Unit,
    pieDe: @Composable (DocumentoClinico) -> Unit = {},
) {
    val espaciado = LocalEspaciadoSalud.current
    Column(verticalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
        documentos.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(espaciado.compacto)) {
                fila.forEach { documento ->
                    TarjetaDeDocumento(
                        documento = documento,
                        alAbrir = { alAbrir(documento) },
                        modifier = Modifier.weight(1f),
                        pie = { pieDe(documento) },
                    )
                }
                // Una fila con un solo documento no debe estirarlo al doble.
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Pixeles por lado de la miniatura: nitida en pantallas densas, ligera en memoria. */
private const val LADO_MINIATURA = 480
