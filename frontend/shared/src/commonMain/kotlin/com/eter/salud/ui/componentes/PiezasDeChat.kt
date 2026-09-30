package com.eter.salud.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_chat_mas_opciones
import salud.shared.generated.resources.chat_fecha_ayer
import salud.shared.generated.resources.chat_fecha_hoy
import salud.shared.generated.resources.chat_vacio_titulo

/**
 * Nombre y subtitulo en la barra de un chat, con el avatar de iniciales.
 *
 * El nombre va SIEMPRE en una linea con puntos suspensivos. Antes el titulo
 * competia por el ancho con las acciones de la barra y, al perder, se partia
 * letra por letra: la barra crecia hasta media pantalla y la conversacion
 * parecia vacia o rota.
 */
@Composable
fun TituloDeChat(
    nombre: String,
    subtitulo: String,
    modifier: Modifier = Modifier,
    /** Un punto de color junto al avatar (el semaforo de riesgo del medico). */
    colorDeEstado: Color? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
    ) {
        Box {
            AvatarDeIniciales(nombre = nombre, lado = LADO_AVATAR)
            if (colorDeEstado != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(LADO_PUNTO_ESTADO)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp)
                        .background(colorDeEstado, CircleShape),
                )
            }
        }
        Column {
            Text(
                text = nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitulo.isNotBlank()) {
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Iniciales de la persona en un circulo del color de marca. Da identidad a la
 * conversacion sin pedir una foto que nadie ha subido.
 */
@Composable
fun AvatarDeIniciales(nombre: String, lado: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(lado)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = inicialesDe(nombre),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * "Dr. Carlos Silva Rodriguez" -> "CS". Se saltan los tratamientos para que
 * todos los medicos no queden como "DC".
 */
internal fun inicialesDe(nombre: String): String =
    nombre.split(' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.endsWith('.') }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { nombre.trim().take(1).uppercase() }

/** Una opcion del menu de "mas opciones" de la barra del chat. */
data class OpcionDeMenu(val etiqueta: String, val alElegir: () -> Unit)

/**
 * El boton de tres puntos. Guarda aqui lo que no cabe como icono en la barra
 * (traducir todo, por ejemplo): una barra con dos botones de texto le quitaba
 * al nombre todo el espacio.
 */
@Composable
fun MenuDeOpciones(opciones: List<OpcionDeMenu>) {
    if (opciones.isEmpty()) return
    var abierto by remember { mutableStateOf(false) }
    val descripcion = stringResource(Res.string.a11y_chat_mas_opciones)
    Box {
        IconButton(onClick = { abierto = true }, modifier = Modifier.semantics { contentDescription = descripcion }) {
            IconoSalud(GlifoSalud.MAS_OPCIONES, lado = 24.dp, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.etiqueta) },
                    onClick = {
                        abierto = false
                        opcion.alElegir()
                    },
                )
            }
        }
    }
}

/**
 * "Hoy", "Ayer" o la fecha, centrado entre los mensajes de dias distintos.
 * Sin esto, "Me siento mejor" de hace una semana y de hace una hora se leen
 * igual, y en un chat clinico cuando se dijo algo importa.
 */
@Composable
fun SeparadorDeFecha(fechaLocal: String, hoy: String, modifier: Modifier = Modifier) {
    if (fechaLocal.isBlank()) return
    val texto = when (fechaLocal) {
        hoy -> stringResource(Res.string.chat_fecha_hoy)
        CalendarioSalud.restarDias(hoy, 1) -> stringResource(Res.string.chat_fecha_ayer)
        else -> fechaLegible(fechaLocal)
    }
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = colores.fondoTarjeta,
            shape = CircleShape,
            tonalElevation = 1.dp,
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = colores.textoSecundario,
                modifier = Modifier.padding(horizontal = espaciado.medio, vertical = espaciado.minimo),
            )
        }
    }
}

/** `2026-09-12` -> `12/09/2026`: sin nombres de mes, que dependerian del idioma. */
private fun fechaLegible(fecha: String): String {
    val partes = CalendarioSalud.descomponer(fecha) ?: return fecha
    return "${partes.dia}/${partes.mes}/${partes.anio}"
}

/**
 * Lo que se ve cuando la conversacion aun no tiene mensajes. Antes era una
 * pantalla negra vacia, que se confundia con "la app no conecta".
 */
@Composable
fun EstadoVacioDeChat(descripcion: String, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = espaciado.amplio),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(GlifoSalud.CONVERSACIONES, lado = 34.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            text = stringResource(Res.string.chat_vacio_titulo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = espaciado.medio),
        )
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = espaciado.compacto)
                .widthIn(max = 320.dp),
        )
    }
}

private val LADO_AVATAR = 40.dp
private val LADO_PUNTO_ESTADO = 14.dp
