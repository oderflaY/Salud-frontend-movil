package com.eter.salud.ui.navegacion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.eter.salud.presentation.navegacion.Destino
import com.eter.salud.presentation.navegacion.PilaNavegacion
import com.eter.salud.ui.theme.MovimientoSalud

/**
 * Dibuja la pantalla que corona la pila y anima el paso entre pantallas.
 *
 * La animacion no es adorno: es lo que da sentido espacial a la navegacion.
 * Avanzar entra por la derecha y volver entra por la izquierda, de modo que el
 * usuario percibe que las pantallas estan colocadas una al lado de otra y sabe,
 * sin leer nada, si se esta adentrando o saliendo. Antes toda transicion era un
 * corte seco y las dos direcciones se veian iguales.
 *
 * El desplazamiento es PARCIAL (un tercio del ancho, no el ancho entero)
 * acompanado de un fundido, y usa la curva de aceleracion suave: arranca
 * decidido y frena al llegar, como se mueven las cosas fisicas. Un movimiento
 * lineal, o mas largo, produce sensacion de mareo cuando la pantalla se lee en
 * movimiento -- el caso de un paramedico junto a una camilla.
 */
@Composable
fun AnfitrionNavegacion(
    pila: PilaNavegacion,
    modifier: Modifier = Modifier,
    contenido: @Composable (Destino) -> Unit,
) {
    AnimatedContent(
        targetState = pila.actual,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { transicion(pila.avanzando) },
        label = "navegacion",
    )
    { destino ->
        contenido(destino)
    }
}

private fun transicion(avanzando: Boolean): ContentTransform {
    // CALMADO y no MEDIO, y con curva de entrada rapida / salida lenta: una
    // transicion lineal o brusca marea cuando la pantalla se mira en
    // movimiento, que es exactamente el caso de un paramedico caminando junto a
    // una camilla. La curva arranca decidida y frena al llegar, que es como se
    // mueven las cosas fisicas.
    val duracion = MovimientoSalud.CALMADO
    val curva = FastOutSlowInEasing
    val desplazamiento: (Int) -> Int =
        if (avanzando) { ancho -> ancho / FRACCION_DESPLAZAMIENTO }
        else { ancho -> -ancho / FRACCION_DESPLAZAMIENTO }

    val especificacion = tween<IntOffset>(duracion, easing = curva)
    val fundido = tween<Float>(duracion, easing = curva)

    val entra = slideInHorizontally(especificacion, desplazamiento) + fadeIn(fundido)
    val sale = slideOutHorizontally(especificacion) { ancho ->
        if (avanzando) -ancho / FRACCION_DESPLAZAMIENTO else ancho / FRACCION_DESPLAZAMIENTO
    } + fadeOut(fundido)

    return entra togetherWith sale
}

private const val FRACCION_DESPLAZAMIENTO = 3
