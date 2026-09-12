package com.eter.salud.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Geometria del sistema: los unicos radios que la app tiene permitido usar.
 *
 * Antes cada pantalla calculaba su esquina a mano a partir del espaciado
 * (`espaciado.compacto + espaciado.minimo`), y el resultado eran seis radios
 * distintos repartidos por el front sin criterio. Con cinco tokens la geometria
 * se vuelve legible: el radio deja de ser un numero y pasa a decir QUE es la
 * pieza.
 *
 * La escala crece con el tamano del elemento, y eso es lo que hace que un boton
 * y una tarjeta parezcan del mismo sistema en vez de dos decisiones sueltas: una
 * esquina de 10dp en una tarjeta grande se ve casi recta, y una de 16dp en un
 * campo de texto se ve hinchada.
 *
 * Es un `object` y no un token de composicion, a diferencia de [ColoresSalud] y
 * [EspaciadoSalud]. No es comodidad: aquellos VARIAN (el color con el Modo
 * Oscuro, el espaciado si algun dia se escala), y por eso viajan por el arbol.
 * La forma de un boton es la misma en Modo Claro, en Modo Oscuro, en Android y
 * en iOS; envolverla en un `CompositionLocal` solo anadiria plomeria para
 * propagar una constante.
 */
object FormaSalud {

    /** Campos de captura, fichas e insignias dentro de otra pieza. */
    val sutil: CornerBasedShape = RoundedCornerShape(10.dp)

    /** Botones de accion. */
    val media: CornerBasedShape = RoundedCornerShape(12.dp)

    /** Tarjetas y bloques agrupados. */
    val grande: CornerBasedShape = RoundedCornerShape(16.dp)

    /** Superficies heroe: la accion de emergencia, los paneles destacados. */
    val destacada: CornerBasedShape = RoundedCornerShape(20.dp)

    /**
     * Tarjeta de toma del carril del paciente.
     *
     * Mas redonda que [grande] porque es la superficie mas ancha de la app sin
     * filete ni sombra: sin borde que la dibuje, la esquina es lo unico que
     * define su silueta, y a 16dp sobre 300dp de ancho se leia casi recta.
     */
    val carril: CornerBasedShape = RoundedCornerShape(28.dp)

    /** Pildoras: filtros, etiquetas de estado, avatares. */
    val pastilla: CornerBasedShape = RoundedCornerShape(percent = 50)
}

/**
 * Duraciones de las transiciones, en milisegundos.
 *
 * Tres y no mas. El movimiento en una app clinica confirma que el sistema
 * recibio el toque; no entretiene. Cualquier cosa por encima de [CALMADO] se
 * percibe como lentitud, y por debajo de [INMEDIATO] no se percibe.
 */
object MovimientoSalud {
    /** Respuesta al toque: hundir un boton, encender un filtro. */
    const val INMEDIATO = 120

    /** Cambio de contenido dentro de una pieza que no se mueve. */
    const val MEDIO = 220

    /** Cambio de significado: el semaforo de una cita pasando a confirmada. */
    const val CALMADO = 320

    /**
     * El UNICO revelado orquestado de una pantalla: el anillo de adherencia
     * llenandose al abrir el panel.
     *
     * Es la excepcion a [CALMADO] y por eso tiene nombre propio: una cifra que
     * se construye ante los ojos del paciente comunica "esto se acaba de medir",
     * y en 320ms ese recorrido no se alcanza a leer. Todo lo demas de la
     * pantalla simplemente esta ahi.
     */
    const val REVELADO = 900

    /**
     * Ciclo del latido del punto de triage critico.
     *
     * Lento a proposito (menos de 1Hz, muy por debajo del limite de destellos
     * de WCAG): tiene que atraer la mirada desde el otro extremo de la lista,
     * no alarmar al que ya la esta mirando.
     */
    const val LATIDO = 1600

    /**
     * Salida exponencial: lo que entra frena al llegar a su sitio.
     *
     * El `FastOutSlowIn` de Material frena tarde y se lee mecanico en un
     * recorrido largo como el del anillo; esta curva llega casi entera en el
     * primer tercio y se asienta despacio, que es como se mueve un instrumento
     * de medicion.
     */
    val SALIDA: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Transicion simetrica para estados que van y vuelven (el latido). */
    val VAIVEN: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}
