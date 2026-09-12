package com.eter.salud.ui.navegacion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eter.salud.presentation.navegacion.Destino
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MovimientoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_nav_seccion
import salud.shared.generated.resources.a11y_nav_seccion_activa
import salud.shared.generated.resources.a11y_nav_sin_leer

/**
 * Barra de secciones del portal.
 *
 * Es el cambio estructural del rediseno: antes cada pantalla se alcanzaba
 * pulsando una tarjeta del panel y se abandonaba con un boton de texto
 * "Atras", asi que moverse entre las tres zonas de la app costaba dos toques y
 * obligaba a pasar siempre por el centro. Con la barra, las tres estan a un
 * toque desde cualquier sitio y la app deja de sentirse como pantallas sueltas.
 *
 * Se dibuja a mano en vez de usar `NavigationBar` de Material por una razon
 * concreta: ese componente pinta una pildora de fondo tras el icono activo y
 * una elevacion tonal propias de Material You, y las dos rompen la estetica
 * plana del sistema justo en la pieza mas visible de la pantalla. Aqui el estado
 * activo se dice con color y peso, que es lo que hace el resto de la app.
 *
 * La barra respeta [WindowInsets.safeDrawing] por abajo, de modo que nunca queda
 * bajo la barra de gestos de Android ni bajo el Home Indicator de iOS.
 */
@Composable
fun BarraSecciones(
    secciones: List<Destino.Seccion>,
    activa: Destino.Seccion?,
    alElegir: (Destino.Seccion) -> Unit,
    modifier: Modifier = Modifier,
    /** Pendientes por seccion; una entrada ausente o en cero no pinta nada. */
    sinLeer: Map<Destino.Seccion, Int> = emptyMap(),
) {
    val colores = LocalColoresSalud.current
    // Sin filete ni sombra: la barra es superficie sobre papel, y el salto de
    // tono entre las dos (1.11:1 en claro, 1.13:1 en oscuro) ya dibuja el borde.
    // Un filete encima seria la unica linea de la pantalla.
    Surface(modifier = modifier.fillMaxWidth(), color = colores.fondoTarjeta) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secciones.forEachIndexed { indice, seccion ->
                    Pestana(
                        seccion = seccion,
                        activa = seccion == activa,
                        posicion = indice + 1,
                        total = secciones.size,
                        sinLeer = sinLeer[seccion] ?: 0,
                        alPulsar = { alElegir(seccion) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pestana(
    seccion: Destino.Seccion,
    activa: Boolean,
    posicion: Int,
    total: Int,
    sinLeer: Int,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val etiqueta = stringResource(seccion.etiqueta())

    val tinta by animateColorAsState(
        targetValue = if (activa) MaterialTheme.colorScheme.primary else colores.textoSecundario,
        animationSpec = tween(MovimientoSalud.MEDIO),
        label = "tintaPestana",
    )

    // La descripcion dice ademas la posicion: quien navega con lector de
    // pantalla no ve que hay tres pestanas ni en cual esta.
    val descripcionBase = stringResource(
        if (activa) Res.string.a11y_nav_seccion_activa else Res.string.a11y_nav_seccion,
        etiqueta,
        posicion,
        total,
    )
    // El pendiente se ANUNCIA, no solo se pinta: un circulo rojo de 16dp es
    // invisible para un lector de pantalla, y es justo el dato por el que el
    // paciente entraria a esa pestana.
    val descripcion = if (sinLeer > 0) {
        stringResource(Res.string.a11y_nav_sin_leer, descripcionBase, sinLeer)
    } else {
        descripcionBase
    }

    Column(
        modifier = modifier
            .heightIn(min = ALTO_PESTANA)
            .clickable(
                // Sin ondas: la retroalimentacion la da el color, y una onda
                // circular en una barra plana ensucia mas de lo que informa.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = alPulsar,
            )
            .superficiePulsable(remember { MutableInteractionSource() }, FormaSalud.media)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                selected = activa
            }
            .padding(vertical = espaciado.compacto),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            IconoSalud(glifo = seccion.glifo(), lado = LADO_ICONO, color = tinta)
            if (sinLeer > 0) {
                Surface(
                    // Desplazado fuera del icono para que no tape el glifo: el
                    // punto avisa, pero no debe impedir reconocer la pestana.
                    modifier = Modifier.offset(x = espaciado.compacto, y = -espaciado.minimo),
                    color = MaterialTheme.colorScheme.error,
                    shape = FormaSalud.pastilla,
                ) {
                    Text(
                        text = if (sinLeer > MAXIMO_EN_PUNTO) TOPE_PENDIENTES else sinLeer.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(
                            horizontal = espaciado.compacto - espaciado.minimo,
                            vertical = 1.dp,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(espaciado.minimo))
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = tinta,
        )
        Spacer(Modifier.height(espaciado.minimo))
        // Subrayado corto bajo la pestana activa. Es el unico indicador de
        // posicion ademas del color, y existe para que el estado no dependa solo
        // del tono: sobre una barra de tres iconos del mismo tamano, distinguir
        // azul de gris no basta para todo el mundo.
        Box(
            Modifier
                .width(if (activa) ANCHO_MARCA else 0.dp)
                .height(GROSOR_MARCA)
                .background(MaterialTheme.colorScheme.primary, FormaSalud.pastilla),
        )
    }
}

/** Por encima de nueve el numero exacto deja de importar y ensancharia la pildora. */
private const val MAXIMO_EN_PUNTO = 9
private const val TOPE_PENDIENTES = "9+"

private val ALTO_PESTANA = 60.dp
private val LADO_ICONO = 24.dp
private val ANCHO_MARCA = 20.dp
private val GROSOR_MARCA = 3.dp
